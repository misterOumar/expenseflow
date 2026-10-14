package dev.misteroumar.expenseflow.receipt;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * Formats de justificatif acceptés (§4.4), identifiés par leur signature binaire (magic bytes)
 * et non par le {@code Content-Type} ou l'extension déclarés par le client.
 */
public enum ReceiptFileType {
    PDF("application/pdf", Set.of("pdf"), new byte[]{'%', 'P', 'D', 'F', '-'}),
    JPEG("image/jpeg", Set.of("jpg", "jpeg"), new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),
    PNG("image/png", Set.of("png"), new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});

    private final String mediaType;
    private final Set<String> extensions;
    private final byte[] signature;

    ReceiptFileType(String mediaType, Set<String> extensions, byte[] signature) {
        this.mediaType = mediaType;
        this.extensions = extensions;
        this.signature = signature;
    }

    public String mediaType() {
        return mediaType;
    }

    public boolean acceptsExtension(String extension) {
        return extensions.contains(extension);
    }

    /**
     * Identifie le format d'après les premiers octets du contenu.
     */
    public static Optional<ReceiptFileType> detect(byte[] content) {
        return Arrays.stream(values())
                .filter(type -> content.length >= type.signature.length
                        && Arrays.equals(content, 0, type.signature.length, type.signature, 0, type.signature.length))
                .findFirst();
    }
}
