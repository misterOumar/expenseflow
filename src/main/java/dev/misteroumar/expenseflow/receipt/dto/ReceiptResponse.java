package dev.misteroumar.expenseflow.receipt.dto;

import java.time.Instant;

/**
 * @param contentType type détecté à partir du contenu, et non celui déclaré par le client
 */
public record ReceiptResponse(
        Long id,
        String originalFilename,
        String contentType,
        long sizeBytes,
        Instant uploadedAt
) {
}
