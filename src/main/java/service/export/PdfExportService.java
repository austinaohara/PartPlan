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

    private static final float MARGIN = 50;
    private static final float ROW_HEIGHT = 20;
    private static final float FONT_SIZE = 12;
    private static final PDType1Font FONT_REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font FONT_BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);


    @Override
    public void export(InspectionPlan inspectionPlan, Path output) {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            PDPageContentStream contentStream = new PDPageContentStream(document, page);

            contentStream.setFont(FONT_REGULAR, FONT_SIZE);
            float yStart = 700;

            yStart = drawTitle(contentStream, inspectionPlan.getName(), yStart);
            String[] headers = {"Seq", "Label", "Characteristic", "Nom", "- Tol", "+ Tol", "Measured", "Status"};
            float[] columnWidths = {40, 60, 140, 50, 50, 50, 70, 60};

            List<Bubble> bubbles = inspectionPlan.getBubblesInSequenceOrder();
            float yPosition = yStart - 20;

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
                        bubble.getStatus().toString()

                };

                if (yPosition < MARGIN) {
                    contentStream.close();

                    page = new PDPage(PDRectangle.LETTER);
                    document.addPage(page);

                    contentStream = new PDPageContentStream(document, page);
                    contentStream.setFont(FONT_REGULAR, FONT_SIZE);
                    yPosition = 700;
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
        contentStream.newLineAtOffset(MARGIN, y);
        contentStream.setFont(FONT_BOLD, FONT_SIZE);
        contentStream.showText("Inspection Report: " + title);
        contentStream.endText();
        return y - 25;
    }

    private float drawRow(PDPageContentStream contentStream, String[] row, float[] columnWidths, float y, boolean isHeader) throws IOException {
        float x = MARGIN;
        for (int i = 0; i < row.length; i++) {
            contentStream.addRect(x, y, columnWidths[i], ROW_HEIGHT);
            contentStream.stroke();

            contentStream.beginText();
            contentStream.setFont(isHeader ? FONT_BOLD : FONT_REGULAR, FONT_SIZE);
            contentStream.newLineAtOffset(x + 2, y + 5);
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
        return string == null ? "" : string;
    }

    private String truncate(String text, float maxWidth) {
        if (text == null) return "";
        int maxChars = (int) (maxWidth / 5);
        if (text.length() <= maxChars) return text;
        return text.substring(0, Math.max(0, maxChars - 3)) + "...";
    }
}
