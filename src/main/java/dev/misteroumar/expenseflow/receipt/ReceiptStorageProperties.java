package dev.misteroumar.expenseflow.receipt;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;

/**
 * Configuration du stockage des justificatifs ({@code expenseflow.receipts.*}).
 *
 * @param directory répertoire de stockage, hors de tout répertoire servi statiquement
 * @param maxSize   taille maximale d'un fichier
 */
@Validated
@ConfigurationProperties(prefix = "expenseflow.receipts")
public record ReceiptStorageProperties(@NotNull Path directory, @NotNull DataSize maxSize) {
}
