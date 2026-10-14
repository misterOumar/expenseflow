package dev.misteroumar.expenseflow.receipt;

import dev.misteroumar.expenseflow.common.error.ResourceNotFoundException;
import dev.misteroumar.expenseflow.receipt.dto.ReceiptResponse;
import dev.misteroumar.expenseflow.report.ExpenseReportRepository;
import dev.misteroumar.expenseflow.user.User;
import dev.misteroumar.expenseflow.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Upload et téléchargement des justificatifs (§4.4, §7.2).
 * <p>
 * Rien de ce que fournit le client n'est cru sur parole : le format est déterminé par les magic bytes,
 * l'extension doit correspondre à ce format, et le nom d'origine n'est conservé que pour l'affichage.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ReceiptService {

    private static final int MAX_FILENAME_LENGTH = 255;

    private final ReceiptRepository receiptRepository;
    private final ExpenseReportRepository reportRepository;
    private final UserRepository userRepository;
    private final ReceiptStorage storage;
    private final ReceiptStorageProperties properties;
    private final ReceiptMapper mapper;

    /**
     * Valide puis enregistre un justificatif.
     *
     * @throws InvalidReceiptException 400 si le fichier est vide, 413 s'il dépasse la taille maximale,
     *                                 415 si son contenu n'est ni PDF, ni JPEG, ni PNG, ou si l'extension
     *                                 ne correspond pas au contenu
     */
    public ReceiptResponse upload(Long userId, MultipartFile file) {
        User user = loadUser(userId);
        byte[] content = readValidated(file);
        String filename = sanitizeFilename(file.getOriginalFilename());
        ReceiptFileType type = ReceiptFileType.detect(content)
                .orElseThrow(() -> new InvalidReceiptException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                        "Seuls les fichiers PDF, JPEG et PNG sont acceptés."));
        if (!type.acceptsExtension(extensionOf(filename))) {
            throw new InvalidReceiptException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "L'extension du fichier « " + filename + " » ne correspond pas à son contenu (" + type.mediaType() + ").");
        }

        String key = storage.store(content);
        deleteFileIfRolledBack(key);
        Receipt receipt = new Receipt(filename, key, type.mediaType(), content.length, sha256(content), user);
        return mapper.toResponse(receiptRepository.save(receipt));
    }

    /**
     * Retourne un justificatif si l'utilisateur l'a déposé, ou s'il peut consulter la note qui l'utilise.
     * Sinon le justificatif est traité comme inexistant (404).
     */
    @Transactional(readOnly = true)
    public ReceiptFile download(Long userId, Long receiptId) {
        User user = loadUser(userId);
        Receipt receipt = receiptRepository.findById(receiptId)
                .filter(candidate -> canRead(user, candidate))
                .orElseThrow(() -> new ResourceNotFoundException("Justificatif " + receiptId + " introuvable."));
        return new ReceiptFile(storage.load(receipt.getStoredKey()), receipt.getContentType(),
                receipt.getOriginalFilename(), receipt.getSizeBytes());
    }

    public record ReceiptFile(Resource content, String contentType, String filename, long size) {
    }

    private boolean canRead(User user, Receipt receipt) {
        return receipt.getUploadedBy().getId().equals(user.getId())
                || reportRepository.findByReceiptId(receipt.getId())
                        .map(report -> report.isVisibleTo(user))
                        .orElse(false);
    }

    private byte[] readValidated(MultipartFile file) {
        if (file.isEmpty()) {
            throw new InvalidReceiptException(HttpStatus.BAD_REQUEST, "Le fichier est vide.");
        }
        if (file.getSize() > properties.maxSize().toBytes()) {
            throw new InvalidReceiptException(HttpStatus.CONTENT_TOO_LARGE,
                    "Le fichier dépasse la taille maximale de " + properties.maxSize().toMegabytes() + " Mo.");
        }
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new UncheckedIOException("Lecture du fichier reçu impossible", ex);
        }
    }

    /**
     * Le fichier est écrit avant l'insertion en base : si la transaction échoue ensuite,
     * il est supprimé pour ne pas laisser de fichier orphelin sur le disque.
     */
    private void deleteFileIfRolledBack(String key) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    storage.delete(key);
                }
            }
        });
    }

    /**
     * Ne garde que le dernier segment du nom (aucun répertoire), sans caractères de contrôle.
     */
    static String sanitizeFilename(String original) {
        String name = original == null ? "" : original;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        name = name.replaceAll("\\p{Cntrl}", "").strip();
        if (name.isEmpty()) {
            return "justificatif";
        }
        return name.length() > MAX_FILENAME_LENGTH ? name.substring(name.length() - MAX_FILENAME_LENGTH) : name;
    }

    private static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private User loadUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur " + userId + " introuvable."));
    }
}
