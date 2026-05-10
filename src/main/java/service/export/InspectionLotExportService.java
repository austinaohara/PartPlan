package service.export;

import model.InspectionLot;
import model.InspectionType;
import model.PartBubbleDefinition;
import model.PartRecord;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class InspectionLotExportService {
    private static final float PDF_MARGIN = 36.0f;
    private static final float PDF_ROW_HEIGHT = 18.0f;
    private static final float PDF_FONT_SIZE = 9.0f;
    private static final float PDF_TITLE_GAP = 14.0f;
    private static final PDType1Font PDF_REGULAR_FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font PDF_BOLD_FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final float[] PDF_COLUMN_WIDTHS = {36.0f, 36.0f, 110.0f, 50.0f, 80.0f, 140.0f, 70.0f, 50.0f, 120.0f};
    private static final String[] PDF_HEADERS = {"Part", "Seq", "Bubble", "Type", "Spec", "Note", "Value", "Result", "Comment"};

    public void export(InspectionLot inspectionLot, ExportFormat format, Path output) throws IOException {
        if (inspectionLot == null) {
            throw new IllegalArgumentException("inspectionLot must not be null");
        }
        if (format == null) {
            throw new IllegalArgumentException("format must not be null");
        }
        if (output == null) {
            throw new IllegalArgumentException("output must not be null");
        }

        List<ExportRow> rows = buildRows(inspectionLot);
        switch (format) {
            case CSV -> exportCsv(inspectionLot, rows, output);
            case PDF -> exportPdf(inspectionLot, rows, output);
            default -> throw new IllegalArgumentException(format + " is an unsupported export format.");
        }
    }

    private void exportCsv(InspectionLot inspectionLot, List<ExportRow> rows, Path output) throws IOException {
        StringBuilder csv = new StringBuilder();
        csv.append("Lot,Plan,Version,Part,Sequence,Bubble,Type,Spec,Note,Measurement,Result,Comment\n");
        for (ExportRow row : rows) {
            csv.append(escape(inspectionLot.getName())).append(",");
            csv.append(escape(inspectionLot.getPlanName())).append(",");
            csv.append(inspectionLot.getPlanVersion()).append(",");
            csv.append(row.partNumber()).append(",");
            csv.append(row.sequenceNumber()).append(",");
            csv.append(escape(row.bubbleName())).append(",");
            csv.append(escape(row.inspectionType())).append(",");
            csv.append(escape(row.specification())).append(",");
            csv.append(escape(row.note())).append(",");
            csv.append(escape(row.measurement())).append(",");
            csv.append(escape(row.result())).append(",");
            csv.append(escape(row.comment())).append("\n");
        }
        Files.writeString(output, csv.toString());
    }

    private void exportPdf(InspectionLot inspectionLot, List<ExportRow> rows, Path output) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDRectangle landscapeLetter = new PDRectangle(PDRectangle.LETTER.getHeight(), PDRectangle.LETTER.getWidth());
            PDPage page = new PDPage(landscapeLetter);
            document.addPage(page);
            PDPageContentStream contentStream = new PDPageContentStream(document, page);

            float yPosition = landscapeLetter.getHeight() - PDF_MARGIN;
            yPosition = drawPdfTitle(contentStream, inspectionLot, yPosition);
            yPosition = drawPdfRow(contentStream, PDF_HEADERS, PDF_COLUMN_WIDTHS, yPosition, true);

            for (ExportRow row : rows) {
                if (yPosition < PDF_MARGIN + PDF_ROW_HEIGHT) {
                    contentStream.close();
                    page = new PDPage(landscapeLetter);
                    document.addPage(page);
                    contentStream = new PDPageContentStream(document, page);
                    yPosition = landscapeLetter.getHeight() - PDF_MARGIN;
                    yPosition = drawPdfTitle(contentStream, inspectionLot, yPosition);
                    yPosition = drawPdfRow(contentStream, PDF_HEADERS, PDF_COLUMN_WIDTHS, yPosition, true);
                }

                String[] values = {
                        String.valueOf(row.partNumber()),
                        String.valueOf(row.sequenceNumber()),
                        row.bubbleName(),
                        row.inspectionType(),
                        row.specification(),
                        row.note(),
                        row.measurement(),
                        row.result(),
                        row.comment()
                };
                yPosition = drawPdfRow(contentStream, values, PDF_COLUMN_WIDTHS, yPosition, false);
            }

            contentStream.close();
            document.save(output.toFile());
        }
    }

    private float drawPdfTitle(PDPageContentStream contentStream, InspectionLot inspectionLot, float yPosition) throws IOException {
        contentStream.beginText();
        contentStream.setFont(PDF_BOLD_FONT, 12.0f);
        contentStream.newLineAtOffset(PDF_MARGIN, yPosition);
        contentStream.showText("Inspection Lot Report: " + safe(inspectionLot.getName()));
        contentStream.endText();

        yPosition -= PDF_TITLE_GAP;

        contentStream.beginText();
        contentStream.setFont(PDF_REGULAR_FONT, PDF_FONT_SIZE);
        contentStream.newLineAtOffset(PDF_MARGIN, yPosition);
        contentStream.showText("Plan: " + safe(inspectionLot.getPlanName()) + "  Version: " + inspectionLot.getPlanVersion());
        contentStream.endText();

        return yPosition - (PDF_ROW_HEIGHT + 4.0f);
    }

    private float drawPdfRow(
            PDPageContentStream contentStream,
            String[] values,
            float[] columnWidths,
            float yPosition,
            boolean header
    ) throws IOException {
        float xPosition = PDF_MARGIN;
        for (int index = 0; index < values.length; index++) {
            float columnWidth = columnWidths[index];
            contentStream.addRect(xPosition, yPosition, columnWidth, PDF_ROW_HEIGHT);
            contentStream.stroke();

            contentStream.beginText();
            contentStream.setFont(header ? PDF_BOLD_FONT : PDF_REGULAR_FONT, PDF_FONT_SIZE);
            contentStream.newLineAtOffset(xPosition + 2.0f, yPosition + 5.0f);
            contentStream.showText(truncate(values[index], columnWidth));
            contentStream.endText();
            xPosition += columnWidth;
        }

        return yPosition - PDF_ROW_HEIGHT;
    }

    private List<ExportRow> buildRows(InspectionLot inspectionLot) {
        List<ExportRow> rows = new ArrayList<>();
        for (PartRecord part : inspectionLot.getParts()) {
            for (PartBubbleDefinition bubble : inspectionLot.getBubbles()) {
                String measurement = safe(part.getMeasurement(bubble.getId()));
                String comment = safe(part.getComment(bubble.getId()));
                rows.add(new ExportRow(
                        part.getPartNumber(),
                        bubble.getSequenceNumber(),
                        safe(bubble.getName()),
                        bubble.getInspectionType().name(),
                        buildSpecification(bubble),
                        safe(bubble.getNote()),
                        measurement,
                        evaluateResult(bubble, measurement),
                        comment
                ));
            }
        }
        return rows;
    }

    private String buildSpecification(PartBubbleDefinition bubble) {
        if (bubble.getInspectionType() == InspectionType.PASS_FAIL) {
            boolean expectedPass = bubble.getExpectedPassFail() == null || bubble.getExpectedPassFail();
            return expectedPass ? "Expected PASS" : "Expected FAIL";
        }

        List<String> segments = new ArrayList<>();
        if (!bubble.getNominalValue().isBlank()) {
            segments.add("Nom " + bubble.getNominalValue().trim());
        }
        if (!bubble.getUpperTolerance().isBlank()) {
            segments.add("+" + bubble.getUpperTolerance().trim());
        }
        if (!bubble.getLowerTolerance().isBlank()) {
            segments.add("-" + bubble.getLowerTolerance().trim());
        }
        return String.join(" ", segments);
    }

    private String evaluateResult(PartBubbleDefinition bubble, String measurement) {
        if (measurement == null || measurement.isBlank()) {
            return "";
        }

        if (bubble.getInspectionType() == InspectionType.PASS_FAIL) {
            Boolean actual = parsePassFailValue(measurement);
            if (actual == null) {
                return "INVALID";
            }
            boolean expected = bubble.getExpectedPassFail() == null || bubble.getExpectedPassFail();
            return actual == expected ? "PASS" : "FAIL";
        }

        if (bubble.getNominalValue().isBlank()) {
            return "";
        }

        try {
            double measured = Double.parseDouble(measurement.trim());
            double nominal = Double.parseDouble(bubble.getNominalValue().trim());
            double lowerTolerance = parseNumericOrZero(bubble.getLowerTolerance());
            double upperTolerance = parseNumericOrZero(bubble.getUpperTolerance());
            return measured >= (nominal - lowerTolerance) && measured <= (nominal + upperTolerance)
                    ? "PASS"
                    : "FAIL";
        } catch (NumberFormatException exception) {
            return "INVALID";
        }
    }

    private double parseNumericOrZero(String text) {
        if (text == null || text.isBlank()) {
            return 0.0;
        }
        return Double.parseDouble(text.trim());
    }

    private Boolean parsePassFailValue(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "pass", "p", "true", "yes", "y", "ok", "accept", "accepted", "good", "1" -> true;
            case "fail", "f", "false", "no", "n", "ng", "reject", "rejected", "bad", "0" -> false;
            default -> null;
        };
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private String truncate(String value, float columnWidth) {
        if (value == null || value.isBlank()) {
            return "";
        }
        int maxCharacters = Math.max(1, (int) (columnWidth / 5.0f));
        if (value.length() <= maxCharacters) {
            return value;
        }
        return value.substring(0, Math.max(0, maxCharacters - 3)) + "...";
    }

    private record ExportRow(
            int partNumber,
            int sequenceNumber,
            String bubbleName,
            String inspectionType,
            String specification,
            String note,
            String measurement,
            String result,
            String comment
    ) {
    }
}
