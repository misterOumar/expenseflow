package dev.misteroumar.expenseflow.receipt;

import dev.misteroumar.expenseflow.receipt.dto.ReceiptResponse;
import dev.misteroumar.expenseflow.security.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.nio.charset.StandardCharsets;

@Tag(name = "Justificatifs", description = "Dépôt et téléchargement des pièces justificatives")
@RestController
@RequestMapping("/api/receipts")
@RequiredArgsConstructor
public class ReceiptController {

    private final ReceiptService receiptService;

    @Operation(summary = "Déposer un justificatif",
            description = "PDF, JPEG ou PNG, 5 Mo maximum. Le format est vérifié sur le contenu du fichier. "
                    + "L'id retourné se renseigne dans le champ receiptId d'une ligne.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ReceiptResponse> upload(@CurrentUserId Long userId,
                                                  @RequestPart("file") MultipartFile file) {
        ReceiptResponse created = receiptService.upload(userId, file);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest()
                        .path("/{id}").buildAndExpand(created.id()).toUri())
                .body(created);
    }

    @Operation(summary = "Télécharger un justificatif",
            description = "Réservé à l'auteur du dépôt et aux personnes qui peuvent consulter la note associée.")
    @GetMapping("/{id}")
    public ResponseEntity<Resource> download(@CurrentUserId Long userId, @PathVariable Long id) {
        ReceiptService.ReceiptFile file = receiptService.download(userId, id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .contentLength(file.size())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.filename(), StandardCharsets.UTF_8).build().toString())
                .body(file.content());
    }
}
