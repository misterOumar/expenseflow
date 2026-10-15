package dev.misteroumar.expenseflow.receipt;

import dev.misteroumar.expenseflow.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReceiptIntegrationTest extends IntegrationTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 0};
    private static final byte[] HTML = "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8);

    @Value("${expenseflow.receipts.directory}")
    private Path storageDirectory;

    @Test
    @DisplayName("un PDF déposé est restitué à l'identique à son auteur")
    void uploadThenDownload() throws Exception {
        long id = uploadReceipt(ALICE);

        mvc.perform(get("/api/receipts/{id}", id).with(as(ALICE)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(content().bytes(PDF))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")));
    }

    @Test
    @DisplayName("le type est déduit du contenu, pas du Content-Type déclaré")
    void contentTypeComesFromMagicBytes() throws Exception {
        upload(ALICE, new MockMultipartFile("file", "photo.png", "application/octet-stream", PNG))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentType").value("image/png"));
    }

    @Test
    @DisplayName("415 pour un fichier HTML déguisé en PDF")
    void rejectsDisguisedFile() throws Exception {
        upload(ALICE, new MockMultipartFile("file", "facture.pdf", "application/pdf", HTML))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.title").value("Justificatif refusé"));
    }

    @Test
    @DisplayName("415 si l'extension ne correspond pas au contenu")
    void rejectsExtensionMismatch() throws Exception {
        upload(ALICE, new MockMultipartFile("file", "ticket.exe", "application/pdf", PDF))
                .andExpect(status().isUnsupportedMediaType());
        upload(ALICE, new MockMultipartFile("file", "ticket.png", "image/png", PDF))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    @DisplayName("400 pour un fichier vide")
    void rejectsEmptyFile() throws Exception {
        upload(ALICE, new MockMultipartFile("file", "vide.pdf", "application/pdf", new byte[0]))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("413 au-delà de 5 Mo")
    void rejectsOversizedFile() throws Exception {
        byte[] large = new byte[5 * 1024 * 1024 + 1];
        System.arraycopy(PDF, 0, large, 0, PDF.length);

        upload(ALICE, new MockMultipartFile("file", "gros.pdf", "application/pdf", large))
                .andExpect(status().isContentTooLarge());
    }

    @Test
    @DisplayName("le nom d'origine est réduit à son dernier segment")
    void stripsPathFromFilename() throws Exception {
        upload(ALICE, new MockMultipartFile("file", "../../etc/ticket.pdf", "application/pdf", PDF))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.originalFilename").value("ticket.pdf"));
    }

    @Test
    @DisplayName("404 quand un autre employé télécharge le justificatif")
    void otherEmployeeCannotDownload() throws Exception {
        long id = uploadReceipt(ALICE);

        mvc.perform(get("/api/receipts/{id}", id).with(as(BOB)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("le manager télécharge le justificatif d'une note de son équipe")
    void managerDownloadsTeamReceipt() throws Exception {
        long reportId = submittedReport(ALICE, 1);
        long receiptId = receiptOfFirstLine(reportId);

        mvc.perform(get("/api/receipts/{id}", receiptId).with(as(MANAGER)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("le comptable ne télécharge le justificatif qu'une fois la note approuvée")
    void accountantDownloadsOnceApproved() throws Exception {
        long reportId = submittedReport(ALICE, 1);
        long receiptId = receiptOfFirstLine(reportId);

        mvc.perform(get("/api/receipts/{id}", receiptId).with(as(ACCOUNTANT)))
                .andExpect(status().isNotFound());

        transition(MANAGER, reportId, "approve");

        mvc.perform(get("/api/receipts/{id}", receiptId).with(as(ACCOUNTANT)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("404 si le fichier a disparu du stockage")
    void missingFileGivesNotFound() throws Exception {
        long id = uploadReceipt(ALICE);
        String key = jdbc.queryForObject("SELECT stored_key FROM receipts WHERE id = ?", String.class, id);
        Files.delete(storageDirectory.resolve(key));

        mvc.perform(get("/api/receipts/{id}", id).with(as(ALICE)))
                .andExpect(status().isNotFound());
    }

    private ResultActions upload(String email, MockMultipartFile file)
            throws Exception {
        return mvc.perform(multipart("/api/receipts").file(file).with(as(email)));
    }

    private long receiptOfFirstLine(long reportId) {
        return jdbc.queryForObject("SELECT receipt_id FROM expense_lines WHERE report_id = ? LIMIT 1",
                Long.class, reportId);
    }
}
