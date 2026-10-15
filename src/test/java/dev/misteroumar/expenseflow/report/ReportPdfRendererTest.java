package dev.misteroumar.expenseflow.report;

import dev.misteroumar.expenseflow.report.dto.LineResponse;
import dev.misteroumar.expenseflow.report.dto.ReportDetailResponse;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class ReportPdfRendererTest {

    private final ReportPdfRenderer renderer =
            new ReportPdfRenderer(Clock.fixed(Instant.parse("2026-10-15T08:30:00Z"), ZoneOffset.UTC));

    @Test
    @DisplayName("les lignes en trop passent sur une nouvelle page, numérotée")
    void paginatesLongReports() throws Exception {
        List<LineResponse> lines = IntStream.rangeClosed(1, 80)
                .mapToObj(i -> line("Ligne " + i, "10.00"))
                .toList();

        try (PDDocument document = Loader.loadPDF(renderer.render(report(lines, "800.00")))) {
            assertThat(document.getNumberOfPages()).isGreaterThan(1);
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("Ligne 1", "Ligne 80", "Page 1/" + document.getNumberOfPages(),
                    "Généré le 15/10/2026 à 10:30", "Total TTC : 800,00");
        }
    }

    @Test
    @DisplayName("les caractères hors WinAnsi et les retours à la ligne ne font pas échouer la génération")
    void toleratesUnsupportedCharacters() throws Exception {
        List<LineResponse> lines = List.of(line("Taxi 🚕 Kraków\navec retour", "1234.50"));

        try (PDDocument document = Loader.loadPDF(renderer.render(report(lines, "1234.50")))) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("Taxi ? Kraków avec retour", "1\u00A0234,50");
        }
    }

    private static ReportDetailResponse report(List<LineResponse> lines, String total) {
        return new ReportDetailResponse(1L, "Déplacements « clients »", 9, 2026, ReportStatus.APPROVED,
                new BigDecimal(total), 3L, "Chloé Moreau", Instant.parse("2026-10-01T10:00:00Z"),
                Instant.parse("2026-10-02T10:00:00Z"), "Claire Martin", null, lines);
    }

    private static LineResponse line(String description, String ttc) {
        BigDecimal amount = new BigDecimal(ttc);
        return new LineResponse(1L, LocalDate.of(2026, 9, 3), ExpenseCategory.TRANSPORT, description,
                amount, BigDecimal.ZERO, amount, 1L);
    }
}
