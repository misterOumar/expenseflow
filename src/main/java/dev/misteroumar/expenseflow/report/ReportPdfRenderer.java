package dev.misteroumar.expenseflow.report;

import dev.misteroumar.expenseflow.report.dto.LineResponse;
import dev.misteroumar.expenseflow.report.dto.ReportDetailResponse;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

/**
 * Récapitulatif PDF d'une note (§4.5) : en-tête, tableau des lignes, total. Mise en page fixe,
 * sans logo ni template. Les lignes passent sur une nouvelle page quand la page est pleine.
 */
@Component
public class ReportPdfRenderer {

    private static final ZoneId ZONE = ZoneId.of("Europe/Paris");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm");

    private static final float MARGIN = 50;
    private static final float ROW_HEIGHT = 18;
    private static final float FONT_SIZE = 10;

    /** Colonnes : libellé, largeur, alignement à droite. */
    private static final Column[] COLUMNS = {
            new Column("Date", 65, false),
            new Column("Catégorie", 80, false),
            new Column("Description", 170, false),
            new Column("HT", 60, true),
            new Column("TVA", 55, true),
            new Column("TTC", 65, true),
    };

    private static final Map<ExpenseCategory, String> CATEGORY_LABELS = Map.of(
            ExpenseCategory.MEAL, "Repas",
            ExpenseCategory.TRANSPORT, "Transport",
            ExpenseCategory.ACCOMMODATION, "Hébergement",
            ExpenseCategory.SUPPLIES, "Fournitures",
            ExpenseCategory.OTHER, "Autre");

    private static final Map<ReportStatus, String> STATUS_LABELS = Map.of(
            ReportStatus.DRAFT, "Brouillon",
            ReportStatus.SUBMITTED, "Soumise",
            ReportStatus.APPROVED, "Approuvée",
            ReportStatus.REJECTED, "Rejetée",
            ReportStatus.PAID, "Payée");

    private final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private final Clock clock;

    public ReportPdfRenderer() {
        this(Clock.system(ZONE));
    }

    ReportPdfRenderer(Clock clock) {
        this.clock = clock;
    }

    public byte[] render(ReportDetailResponse report) {
        try (PDDocument document = new PDDocument()) {
            new Writer(document).write(report);
            addFooters(document);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException("Génération du PDF impossible", ex);
        }
    }

    /**
     * Écrit le contenu page par page, en suivant la position verticale courante.
     */
    private final class Writer {

        private final PDDocument document;
        private PDPageContentStream content;
        private float y;

        private Writer(PDDocument document) {
            this.document = document;
        }

        private void write(ReportDetailResponse report) throws IOException {
            newPage();
            text(bold, 18, MARGIN, y, "Note de frais");
            y -= 26;
            text(bold, 13, MARGIN, y, report.title());
            y -= 28;

            field("Employé", report.ownerName());
            field("Période", "%02d/%d".formatted(report.periodMonth(), report.periodYear()));
            field("Statut", STATUS_LABELS.get(report.status()));
            if (report.decidedByName() != null) {
                field("Décision", report.decidedByName() + ", le " + format(report.decidedAt()));
            }
            y -= 16;

            tableHeader();
            for (LineResponse line : report.lines()) {
                if (y < MARGIN + 2 * ROW_HEIGHT) {
                    newPage();
                    tableHeader();
                }
                row(regular, DATE.format(line.spentOn()), CATEGORY_LABELS.get(line.category()), line.description(),
                        money(line.amountHt()), money(line.vatAmount()), money(line.amountTtc()));
            }

            y -= 6;
            float totalRight = MARGIN + tableWidth();
            String total = "Total TTC : " + money(report.totalAmount());
            text(bold, 12, totalRight - width(bold, 12, total), y, total);
            content.close();
        }

        private void newPage() throws IOException {
            if (content != null) {
                content.close();
            }
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            content = new PDPageContentStream(document, page);
            y = page.getMediaBox().getHeight() - MARGIN;
        }

        private void field(String label, String value) throws IOException {
            text(bold, FONT_SIZE, MARGIN, y, label + " :");
            text(regular, FONT_SIZE, MARGIN + 70, y, value);
            y -= 15;
        }

        private void tableHeader() throws IOException {
            row(bold, Arrays.stream(COLUMNS).map(Column::label).toArray(String[]::new));
            content.moveTo(MARGIN, y + ROW_HEIGHT - 4);
            content.lineTo(MARGIN + tableWidth(), y + ROW_HEIGHT - 4);
            content.stroke();
        }

        private void row(PDType1Font font, String... cells) throws IOException {
            float x = MARGIN;
            for (int i = 0; i < COLUMNS.length; i++) {
                Column column = COLUMNS[i];
                String cell = fit(font, cells[i], column.width() - 6);
                float cellX = column.alignRight() ? x + column.width() - width(font, FONT_SIZE, cell) : x;
                text(font, FONT_SIZE, cellX, y, cell);
                x += column.width();
            }
            y -= ROW_HEIGHT;
        }

        private void text(PDType1Font font, float size, float x, float baseline, String value) throws IOException {
            content.beginText();
            content.setFont(font, size);
            content.newLineAtOffset(x, baseline);
            content.showText(printable(font, value));
            content.endText();
        }
    }

    private void addFooters(PDDocument document) throws IOException {
        String generatedAt = "Généré le " + DATE_TIME.format(ZonedDateTime.now(clock).withZoneSameInstant(ZONE));
        int pageCount = document.getNumberOfPages();
        for (int i = 0; i < pageCount; i++) {
            PDPage page = document.getPage(i);
            try (PDPageContentStream footer = new PDPageContentStream(
                    document, page, PDPageContentStream.AppendMode.APPEND, true)) {
                String pageLabel = "Page " + (i + 1) + "/" + pageCount;
                footer.beginText();
                footer.setFont(regular, 8);
                footer.newLineAtOffset(MARGIN, MARGIN / 2);
                footer.showText(printable(regular, generatedAt));
                footer.newLineAtOffset(tableWidth() - width(regular, 8, pageLabel), 0);
                footer.showText(pageLabel);
                footer.endText();
            }
        }
    }

    /**
     * Les polices standard PDF ne couvrent que l'encodage WinAnsi : un caractère hors de cet
     * encodage (emoji, alphabet non latin…) ferait échouer la génération. Il est remplacé par « ? ».
     */
    private static String printable(PDType1Font font, String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        value.codePoints().forEach(codePoint -> {
            String character = Character.isISOControl(codePoint) ? " " : Character.toString(codePoint);
            try {
                font.encode(character);
                out.append(character);
            } catch (IOException | IllegalArgumentException ex) {
                out.append('?');
            }
        });
        return out.toString();
    }

    /**
     * Tronque le texte avec « … » pour qu'il tienne dans la largeur de la colonne.
     */
    private static String fit(PDType1Font font, String value, float maxWidth) throws IOException {
        String text = printable(font, value);
        if (width(font, FONT_SIZE, text) <= maxWidth) {
            return text;
        }
        while (!text.isEmpty() && width(font, FONT_SIZE, text + "…") > maxWidth) {
            text = text.substring(0, text.length() - 1);
        }
        return text + "…";
    }

    private static float width(PDType1Font font, float size, String text) throws IOException {
        return font.getStringWidth(printable(font, text)) / 1000 * size;
    }

    private static float tableWidth() {
        float width = 0;
        for (Column column : COLUMNS) {
            width += column.width();
        }
        return width;
    }

    /**
     * Format monétaire français. L'espace fine insécable (U+202F) utilisée par Java comme séparateur
     * de milliers n'existe pas en WinAnsi : elle est remplacée par une espace insécable classique.
     */
    private static String money(BigDecimal amount) {
        return NumberFormat.getCurrencyInstance(Locale.FRANCE).format(amount).replace(' ', ' ');
    }

    private static String format(Instant instant) {
        return instant == null ? "" : DATE.format(instant.atZone(ZONE));
    }

    private record Column(String label, float width, boolean alignRight) {
    }
}
