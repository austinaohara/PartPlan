package service.export;

import model.Bubble;
import model.InspectionPlan;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class PdfExportService implements Exporter {

    private static final float MARGIN = 36;
    private static final float ROW_HEIGHT = 20;
    private static final float FONT_SIZE = 9;
    private static final float TITLE_FONT_SIZE = 14;
    private static final float SUMMARY_SPACING = 14;
    private static final float PAGE_TOP_Y = 740;
    private static final PDType1Font FONT_REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font FONT_BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);


    @Override
    public void export(InspectionPlan inspectionPlan, Path output) {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            PDPageContentStream contentStream = new PDPageContentStream(document, page);

            List<Bubble> bubbles = inspectionPlan.getBubblesInSequenceOrder();
            ExportSummary summary = ExportSummary.fromBubbles(bubbles);

            float yPosition = PAGE_TOP_Y;
            yPosition = drawTitle(contentStream, inspectionPlan.getName(), yPosition);
            yPosition = drawSummary(contentStream, summary, yPosition);


            String[] headers = {"Seq", "Label", "Characteristic", "Nom", "- Tol", "+ Tol", "Measured", "Status", "Comment"};
            float[] columnWidths = {28, 42, 116, 44, 42, 42, 52, 42, 110};

            yPosition -= 8;
            yPosition = drawRow(contentStream, headers, columnWidths, yPosition, true);

            for (Bubble bubble : bubbles) {
                String[] row = {
                        String.valueOf(bubble.getSequenceNumber()),
                        safe(bubble.getLabel()),
                        safe(bubble.getCharacteristic()),
                        value(bubble.getNominalValue()),
                        value(bubble.getLowerTolerance()),
                        value(bubble.getUpperTolerance()),
                        value(bubble.getMeasuredValue()),
                        bubble.getStatus().toString(),
                        safe(bubble.getNote())

                };

                if (yPosition < MARGIN) {
                    contentStream.close();

                    page = new PDPage(PDRectangle.LETTER);
                    document.addPage(page);

                    contentStream = new PDPageContentStream(document, page);
                    yPosition = PAGE_TOP_Y;
                    drawTitle(contentStream, inspectionPlan.getName() + " (continued)", yPosition);                    yPosition = 8;
                    yPosition = drawRow(contentStream, headers, columnWidths, yPosition, true);
                }
                yPosition = drawRow(contentStream, row, columnWidths, yPosition, false);
            }

            contentStream.close();
            document.save(output.toFile());

        } catch (IOException e) {
            throw new RuntimeException("Failed to export to PDF", e);
        }
    }

    private float drawTitle(PDPageContentStream contentStream, String title, float y) throws IOException {
        contentStream.beginText();
        contentStream.setFont(FONT_BOLD, TITLE_FONT_SIZE);
        contentStream.newLineAtOffset(MARGIN, y);
        contentStream.showText("Inspection Report: " + safe(title));
        contentStream.endText();
        return y - 24;
    }

    private float drawSummary(PDPageContentStream contentStream, ExportSummary summary, float y) throws IOException {
        String[] lines = {
                "Summary",
                "Total bubbles: " + summary.total(),
                "Pass: " + summary.pass() + "   Fail: " + summary.fail() + "   Review: " + summary.review() + "   Open: " + summary.open(),
                "With comments: " + summary.withComments() + "   Without comments: " + summary.withoutComments()
        };

        for (int i = 0; i < lines.length; i++) {
            contentStream.beginText();
            contentStream.setFont(i == 0 ? FONT_BOLD : FONT_REGULAR, FONT_SIZE);
            contentStream.newLineAtOffset(MARGIN, y);
            contentStream.showText(lines[i]);
            contentStream.endText();
            y -= SUMMARY_SPACING;
        }

        return y;
    }
    private float drawRow(PDPageContentStream contentStream, String[] row, float[] columnWidths, float y, boolean isHeader) throws IOException {
        float x = MARGIN;
        for (int i = 0; i < row.length; i++) {
            contentStream.addRect(x, y, columnWidths[i], ROW_HEIGHT);
            contentStream.stroke();

            contentStream.beginText();
            contentStream.setFont(isHeader ? FONT_BOLD : FONT_REGULAR, FONT_SIZE);
            contentStream.newLineAtOffset(x + 2, y + 6);
            contentStream.showText(truncate(row[i], columnWidths[i]));
            contentStream.endText();
            x += columnWidths[i];
        }

        return y - ROW_HEIGHT;
    }

    private String value(Double doubleValue) {
        return doubleValue == null ? "" : String.format("%.3f", doubleValue);
    }

    private String safe(String string) {
        return string == null ? "" : string.replace("\n", " ").replace("\r", " ");
    }

    private String truncate(String text, float maxWidth) {
        if (text == null) return "";
        int maxChars = (int) (maxWidth / 4.7f);
        if (text.length() <= maxChars) return text;
        return text.substring(0, Math.max(0, maxChars - 3)) + "...";
    }
}
