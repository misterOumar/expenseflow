package dev.misteroumar.expenseflow.receipt;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Stockage des fichiers sur le disque local, sous un nom généré (UUID) sans lien avec le nom
 * fourni par le client : aucune collision, aucun chemin relatif ({@code ../}) ni nom devinable.
 */
@Component
@EnableConfigurationProperties(ReceiptStorageProperties.class)
public class ReceiptStorage {

    private static final Pattern KEY_FORMAT = Pattern.compile("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}");

    private final Path root;

    public ReceiptStorage(ReceiptStorageProperties properties) throws IOException {
        this.root = Files.createDirectories(properties.directory().toAbsolutePath().normalize());
    }

    /**
     * Écrit le contenu et retourne la clé de stockage générée.
     */
    public String store(byte[] content) {
        String key = UUID.randomUUID().toString();
        try {
            Files.write(resolve(key), content);
        } catch (IOException ex) {
            throw new UncheckedIOException("Écriture du justificatif impossible", ex);
        }
        return key;
    }

    public Resource load(String key) {
        return new PathResource(resolve(key));
    }

    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException ex) {
            throw new UncheckedIOException("Suppression du justificatif impossible", ex);
        }
    }

    /**
     * Défense en profondeur : seule une clé au format UUID est résolue, et le chemin obtenu
     * doit rester dans le répertoire racine.
     */
    private Path resolve(String key) {
        if (!KEY_FORMAT.matcher(key).matches()) {
            throw new IllegalArgumentException("Clé de stockage invalide : " + key);
        }
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Chemin hors du répertoire de stockage : " + key);
        }
        return path;
    }
}
