package dev.misteroumar.expenseflow.report;

import dev.misteroumar.expenseflow.IntegrationTest;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReportPdfIntegrationTest extends IntegrationTest {

    @Test
    @DisplayName("le PDF d'une note approuvée contient l'en-tête, les lignes et le total")
    void exportsApprovedReport() throws Exception {
        long id = approvedReport(ALICE, 3);

        byte[] pdf = mvc.perform(get("/api/reports/{id}/export.pdf", id).with(as(ACCOUNTANT)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        containsString("note-de-frais-2026-03-" + id + ".pdf")))
                .andReturn().getResponse().getContentAsByteArray();

        try (PDDocument document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text)
                    .contains("Alice Durand", "03/2026", "Approuvée", "Claire Martin")
                    .contains("15/01/2026", "Repas", "Repas client")
                    .contains("Total TTC : 22,00");
        }
    }

    @Test
    @DisplayName("409 en ProblemDetail pour une note soumise")
    void rejectsSubmittedReport() throws Exception {
        long id = submittedReport(ALICE, 3);

        mvc.perform(get("/api/reports/{id}/export.pdf", id).with(as(MANAGER)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("404 pour un employé qui n'a pas accès à la note")
    void hidesOthersReport() throws Exception {
        long id = approvedReport(ALICE, 3);

        mvc.perform(get("/api/reports/{id}/export.pdf", id).with(as(BOB)))
                .andExpect(status().isNotFound());
    }
}
