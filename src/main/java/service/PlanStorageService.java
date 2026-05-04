package service;

import model.Bubble;
import model.BubbleStatus;
import model.InspectionPlan;
import model.InspectionType;
import model.PlanDrawing;
import model.PlanPage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class PlanStorageService {
    private static final String APP_DIRECTORY_NAME = ".partplan";
    private static final String PLANS_DIRECTORY_NAME = "plans";
    private static final String PLAN_FILE_NAME = "plan.json";
    private static final String DRAWING_DIRECTORY_NAME = "drawing";
    private static final String PAGES_DIRECTORY_NAME = "pages";

    public void savePlan(InspectionPlan plan) {
        if (plan == null) {
            throw new IllegalArgumentException("plan must not be null");
        }

        try {
            Path planDirectory = getPlanDirectory(plan.getId());
            Files.createDirectories(planDirectory);

            List<PlanPage> savedPages = copyPagesIfNeeded(plan, planDirectory);
            if (!savedPages.isEmpty()) {
                plan.setPages(savedPages);
            } else {
                PlanDrawing savedDrawing = copyDrawingIfNeeded(plan, planDirectory);
                if (savedDrawing != null) {
                    plan.setDrawing(savedDrawing);
                }
            }

            plan.setUpdatedAt(LocalDateTime.now());
            String json = buildJson(plan);
            Files.writeString(planDirectory.resolve(PLAN_FILE_NAME), json, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to save inspection plan.", exception);
        }
    }

    public List<InspectionPlan> loadPlans() {
        List<InspectionPlan> plans = new ArrayList<>();
        Path plansDirectory = getPlansDirectory();

        if (!Files.isDirectory(plansDirectory)) {
            return plans;
        }

        try {
            List<Path> planFiles = Files.list(plansDirectory)
                    .filter(Files::isDirectory)
                    .map(path -> path.resolve(PLAN_FILE_NAME))
                    .filter(Files::isRegularFile)
                    .toList();

            for (Path planFile : planFiles) {
                plans.add(readPlan(planFile));
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load saved plans.", exception);
        }

        plans.sort(Comparator.comparing(InspectionPlan::getUpdatedAt).reversed());
        return plans;
    }

    public InspectionPlan loadPlan(String planId) {
        try {
            Path planFile = getPlanDirectory(planId).resolve(PLAN_FILE_NAME);
            if (!Files.isRegularFile(planFile)) {
                throw new IllegalStateException("Saved plan file was not found.");
            }
            return readPlan(planFile);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to open inspection plan.", exception);
        }
    }

    public void deletePlan(String planId) {
        try {
            Path planDirectory = getPlanDirectory(planId);
            if (!Files.exists(planDirectory)) {
                return;
            }

            List<Path> paths = Files.walk(planDirectory)
                    .sorted(Comparator.reverseOrder())
                    .toList();

            for (Path path : paths) {
                Files.deleteIfExists(path);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to delete inspection plan.", exception);
        }
    }

    private InspectionPlan readPlan(Path planFile) throws IOException {
        String json = Files.readString(planFile, StandardCharsets.UTF_8);
        String id = readStringValue(json, "id");
        String name = readStringValue(json, "name");
        String partNumber = readStringValue(json, "partNumber");
        String revision = readStringValue(json, "revision");
        String description = readStringValue(json, "description");
        String createdAtText = readStringValue(json, "createdAt");
        String updatedAtText = readStringValue(json, "updatedAt");
        List<PlanPage> pages = readPages(json);
        List<Bubble> bubbles = readBubbles(json);

        PlanDrawing drawing = null;
        if (json.contains("\"drawing\"")) {
            String drawingFileName = readStringValue(json, "fileName");
            String drawingPath = readStringValue(json, "storedPath");
            String drawingType = readStringValue(json, "fileType");
            if (!drawingFileName.isBlank() || !drawingPath.isBlank()) {
                drawing = new PlanDrawing(drawingFileName, drawingPath, drawingType);
            }
        }

        return new InspectionPlan(
                id,
                name,
                partNumber,
                revision,
                description,
                drawing,
                pages,
                bubbles,
                LocalDateTime.parse(createdAtText),
                LocalDateTime.parse(updatedAtText)
        );
    }

    private PlanDrawing copyDrawingIfNeeded(InspectionPlan plan, Path planDirectory) throws IOException {
        PlanDrawing drawing = plan.getDrawing();
        if (drawing == null || drawing.getStoredPath() == null || drawing.getStoredPath().isBlank()) {
            return null;
        }

        Path sourcePath = Path.of(drawing.getStoredPath());
        if (!Files.isRegularFile(sourcePath)) {
            return drawing;
        }

        Path drawingDirectory = planDirectory.resolve(DRAWING_DIRECTORY_NAME);
        Files.createDirectories(drawingDirectory);

        Path targetPath = drawingDirectory.resolve(drawing.getFileName());
        if (!sourcePath.toAbsolutePath().normalize().equals(targetPath.toAbsolutePath().normalize())) {
            Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }

        return new PlanDrawing(drawing.getFileName(), targetPath.toString(), drawing.getFileType());
    }

    private List<PlanPage> copyPagesIfNeeded(InspectionPlan plan, Path planDirectory) throws IOException {
        List<PlanPage> savedPages = new ArrayList<>();
        if (plan.getPages().isEmpty()) {
            return savedPages;
        }

        Path pagesDirectory = planDirectory.resolve(PAGES_DIRECTORY_NAME);
        Files.createDirectories(pagesDirectory);

        for (PlanPage page : plan.getPages()) {
            PlanDrawing savedDrawing = copyPageDrawingIfNeeded(page, pagesDirectory);
            savedPages.add(new PlanPage(page.getId(), page.getName(), page.getPageNumber(), savedDrawing));
        }

        return savedPages;
    }

    private PlanDrawing copyPageDrawingIfNeeded(PlanPage page, Path pagesDirectory) throws IOException {
        PlanDrawing drawing = page.getDrawing();
        if (drawing == null || drawing.getStoredPath() == null || drawing.getStoredPath().isBlank()) {
            return drawing;
        }

        Path sourcePath = Path.of(drawing.getStoredPath());
        if (!Files.isRegularFile(sourcePath)) {
            return drawing;
        }

        Path pageDirectory = pagesDirectory.resolve("page-" + page.getPageNumber());
        Files.createDirectories(pageDirectory);

        Path targetPath = pageDirectory.resolve(drawing.getFileName());
        if (!sourcePath.toAbsolutePath().normalize().equals(targetPath.toAbsolutePath().normalize())) {
            Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }

        return new PlanDrawing(drawing.getFileName(), targetPath.toString(), drawing.getFileType());
    }

    private Path getPlansDirectory() {
        return Path.of(System.getProperty("user.home"), APP_DIRECTORY_NAME, PLANS_DIRECTORY_NAME);
    }

    private Path getPlanDirectory(String planId) {
        return getPlansDirectory().resolve(planId);
    }

    private String buildJson(InspectionPlan plan) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\n");
        builder.append("  \"id\": \"").append(escape(plan.getId())).append("\",\n");
        builder.append("  \"name\": \"").append(escape(plan.getName())).append("\",\n");
        builder.append("  \"partNumber\": \"").append(escape(plan.getPartNumber())).append("\",\n");
        builder.append("  \"revision\": \"").append(escape(plan.getRevision())).append("\",\n");
        builder.append("  \"description\": \"").append(escape(plan.getDescription())).append("\",\n");
        builder.append("  \"createdAt\": \"").append(plan.getCreatedAt()).append("\",\n");
        builder.append("  \"updatedAt\": \"").append(plan.getUpdatedAt()).append("\"");

        PlanDrawing drawing = plan.getDrawing();
        if (drawing != null) {
            builder.append(",\n");
            builder.append("  \"drawing\": {\n");
            builder.append("    \"fileName\": \"").append(escape(drawing.getFileName())).append("\",\n");
            builder.append("    \"storedPath\": \"").append(escape(drawing.getStoredPath())).append("\",\n");
            builder.append("    \"fileType\": \"").append(escape(drawing.getFileType())).append("\"\n");
            builder.append("  }\n");
        }

        if (!plan.getPages().isEmpty()) {
            builder.append(",\n");
            builder.append("  \"pages\": [\n");
            for (int index = 0; index < plan.getPages().size(); index++) {
                appendPageJson(builder, plan.getPages().get(index));
                if (index < plan.getPages().size() - 1) {
                    builder.append(",");
                }
                builder.append("\n");
            }
            builder.append("  ]\n");
        }

        if (!plan.getBubbles().isEmpty()) {
            builder.append(",\n");
            builder.append("  \"bubbles\": [\n");
            for (int index = 0; index < plan.getBubbles().size(); index++) {
                appendBubbleJson(builder, plan.getBubbles().get(index));
                if (index < plan.getBubbles().size() - 1) {
                    builder.append(",");
                }
                builder.append("\n");
            }
            builder.append("  ]\n");
        } else {
            builder.append("\n");
        }

        builder.append("}\n");
        return builder.toString();
    }

    private void appendPageJson(StringBuilder builder, PlanPage page) {
        PlanDrawing drawing = page.getDrawing();
        builder.append("    {\n");
        builder.append("      \"id\": \"").append(escape(page.getId())).append("\",\n");
        builder.append("      \"name\": \"").append(escape(page.getName())).append("\",\n");
        builder.append("      \"pageNumber\": \"").append(page.getPageNumber()).append("\"");
        if (drawing != null) {
            builder.append(",\n");
            builder.append("      \"drawing\": {\n");
            builder.append("        \"fileName\": \"").append(escape(drawing.getFileName())).append("\",\n");
            builder.append("        \"storedPath\": \"").append(escape(drawing.getStoredPath())).append("\",\n");
            builder.append("        \"fileType\": \"").append(escape(drawing.getFileType())).append("\"\n");
            builder.append("      }\n");
        } else {
            builder.append("\n");
        }
        builder.append("    }");
    }

    private List<PlanPage> readPages(String json) {
        List<PlanPage> pages = new ArrayList<>();
        String arrayText = readArrayText(json, "pages");
        if (arrayText.isBlank()) {
            return pages;
        }

        for (String pageJson : readObjectTexts(arrayText)) {
            pages.add(readPage(pageJson));
        }

        return pages;
    }

    private PlanPage readPage(String json) {
        PlanDrawing drawing = null;
        if (json.contains("\"drawing\"")) {
            String drawingFileName = readStringValue(json, "fileName");
            String drawingPath = readStringValue(json, "storedPath");
            String drawingType = readStringValue(json, "fileType");
            if (!drawingFileName.isBlank() || !drawingPath.isBlank()) {
                drawing = new PlanDrawing(drawingFileName, drawingPath, drawingType);
            }
        }

        return new PlanPage(
                readStringValue(json, "id"),
                readStringValue(json, "name"),
                parseInteger(readStringValue(json, "pageNumber"), 0),
                drawing
        );
    }

    private void appendBubbleJson(StringBuilder builder, Bubble bubble) {
        builder.append("    {\n");
        builder.append("      \"id\": \"").append(escape(bubble.getId())).append("\",\n");
        builder.append("      \"pageId\": \"").append(escape(bubble.getPageId())).append("\",\n");
        builder.append("      \"x\": \"").append(bubble.getX()).append("\",\n");
        builder.append("      \"y\": \"").append(bubble.getY()).append("\",\n");
        builder.append("      \"radius\": \"").append(bubble.getRadius()).append("\",\n");
        builder.append("      \"useDefaultDiameter\": \"").append(bubble.isUseDefaultDiameter()).append("\",\n");
        builder.append("      \"color\": \"").append(escape(bubble.getColor())).append("\",\n");
        builder.append("      \"useDefaultColor\": \"").append(bubble.isUseDefaultColor()).append("\",\n");
        builder.append("      \"label\": \"").append(escape(bubble.getLabel())).append("\",\n");
        builder.append("      \"characteristic\": \"").append(escape(bubble.getCharacteristic())).append("\",\n");
        builder.append("      \"inspectionType\": \"").append(bubble.getInspectionType()).append("\",\n");
        builder.append("      \"nominalValue\": \"").append(nullableDouble(bubble.getNominalValue())).append("\",\n");
        builder.append("      \"lowerTolerance\": \"").append(nullableDouble(bubble.getLowerTolerance())).append("\",\n");
        builder.append("      \"upperTolerance\": \"").append(nullableDouble(bubble.getUpperTolerance())).append("\",\n");
        builder.append("      \"expectedPassFail\": \"").append(nullableBoolean(bubble.getExpectedPassFail())).append("\",\n");
        builder.append("      \"measuredValue\": \"").append(nullableDouble(bubble.getMeasuredValue())).append("\",\n");
        builder.append("      \"actualPassFail\": \"").append(nullableBoolean(bubble.getActualPassFail())).append("\",\n");
        builder.append("      \"status\": \"").append(bubble.getStatus()).append("\",\n");
        builder.append("      \"note\": \"").append(escape(bubble.getNote())).append("\",\n");
        builder.append("      \"sequenceNumber\": \"").append(bubble.getSequenceNumber()).append("\",\n");
        builder.append("      \"createdAt\": \"").append(bubble.getCreatedAt()).append("\",\n");
        builder.append("      \"updatedAt\": \"").append(bubble.getUpdatedAt()).append("\"\n");
        builder.append("    }");
    }

    private List<Bubble> readBubbles(String json) {
        List<Bubble> bubbles = new ArrayList<>();
        String arrayText = readArrayText(json, "bubbles");
        if (arrayText.isBlank()) {
            return bubbles;
        }

        for (String bubbleJson : readObjectTexts(arrayText)) {
            bubbles.add(readBubble(bubbleJson));
        }

        return bubbles;
    }

    private Bubble readBubble(String json) {
        return new Bubble(
                readStringValue(json, "id"),
                readStringValue(json, "pageId"),
                parseDouble(readStringValue(json, "x"), 0.0),
                parseDouble(readStringValue(json, "y"), 0.0),
                parseDouble(readStringValue(json, "radius"), 18.0),
                parseBoolean(readStringValue(json, "useDefaultDiameter"), true),
                readStringOrDefault(json, "color", "#E53935"),
                parseBoolean(readStringValue(json, "useDefaultColor"), true),
                readStringValue(json, "label"),
                readStringValue(json, "characteristic"),
                parseInspectionType(readStringValue(json, "inspectionType")),
                parseNullableDouble(readStringValue(json, "nominalValue")),
                parseNullableDouble(readStringValue(json, "lowerTolerance")),
                parseNullableDouble(readStringValue(json, "upperTolerance")),
                parseNullableBoolean(readStringValue(json, "expectedPassFail")),
                parseNullableDouble(readStringValue(json, "measuredValue")),
                parseNullableBoolean(readStringValue(json, "actualPassFail")),
                parseBubbleStatus(readStringValue(json, "status")),
                readStringValue(json, "note"),
                parseInteger(readStringValue(json, "sequenceNumber"), 0),
                parseLocalDateTime(readStringValue(json, "createdAt")),
                parseLocalDateTime(readStringValue(json, "updatedAt"))
        );
    }

    private String readArrayText(String json, String key) {
        String marker = "\"" + key + "\"";
        int keyIndex = json.indexOf(marker);
        if (keyIndex < 0) {
            return "";
        }

        int openingBracketIndex = json.indexOf('[', keyIndex);
        if (openingBracketIndex < 0) {
            return "";
        }

        int closingBracketIndex = findMatchingBracket(json, openingBracketIndex, '[', ']');
        if (closingBracketIndex < 0) {
            return "";
        }

        return json.substring(openingBracketIndex + 1, closingBracketIndex);
    }

    private List<String> readObjectTexts(String arrayText) {
        List<String> objects = new ArrayList<>();
        int index = 0;
        while (index < arrayText.length()) {
            int openingBraceIndex = arrayText.indexOf('{', index);
            if (openingBraceIndex < 0) {
                break;
            }

            int closingBraceIndex = findMatchingBracket(arrayText, openingBraceIndex, '{', '}');
            if (closingBraceIndex < 0) {
                break;
            }

            objects.add(arrayText.substring(openingBraceIndex, closingBraceIndex + 1));
            index = closingBraceIndex + 1;
        }

        return objects;
    }

    private int findMatchingBracket(String text, int openingIndex, char openingCharacter, char closingCharacter) {
        int depth = 0;
        boolean inString = false;

        for (int index = openingIndex; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '"' && (index == 0 || text.charAt(index - 1) != '\\')) {
                inString = !inString;
            }

            if (inString) {
                continue;
            }

            if (character == openingCharacter) {
                depth++;
            } else if (character == closingCharacter) {
                depth--;
            }

            if (depth == 0) {
                return index;
            }
        }

        return -1;
    }

    private String readStringValue(String json, String key) {
        String marker = "\"" + key + "\"";
        int keyIndex = json.indexOf(marker);
        if (keyIndex < 0) {
            return "";
        }

        int colonIndex = json.indexOf(':', keyIndex);
        int openingQuoteIndex = json.indexOf('"', colonIndex + 1);
        int closingQuoteIndex = openingQuoteIndex + 1;

        while (closingQuoteIndex < json.length()) {
            closingQuoteIndex = json.indexOf('"', closingQuoteIndex);
            if (closingQuoteIndex < 0) {
                return "";
            }
            if (json.charAt(closingQuoteIndex - 1) != '\\') {
                break;
            }
            closingQuoteIndex++;
        }

        String value = json.substring(openingQuoteIndex + 1, closingQuoteIndex);
        return unescape(value);
    }

    private String readStringOrDefault(String json, String key, String defaultValue) {
        String value = readStringValue(json, key);
        return value.isBlank() ? defaultValue : value;
    }

    private InspectionType parseInspectionType(String value) {
        if (value == null || value.isBlank()) {
            return InspectionType.NUMERIC;
        }

        try {
            return InspectionType.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return InspectionType.NUMERIC;
        }
    }

    private BubbleStatus parseBubbleStatus(String value) {
        if (value == null || value.isBlank()) {
            return BubbleStatus.OPEN;
        }

        try {
            return BubbleStatus.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return BubbleStatus.OPEN;
        }
    }

    private LocalDateTime parseLocalDateTime(String value) {
        if (value == null || value.isBlank()) {
            return LocalDateTime.now();
        }

        return LocalDateTime.parse(value);
    }

    private Double parseNullableDouble(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return Double.parseDouble(value);
    }

    private double parseDouble(String value, double defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        return Double.parseDouble(value);
    }

    private Boolean parseNullableBoolean(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return Boolean.parseBoolean(value);
    }

    private boolean parseBoolean(String value, boolean defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }

    private int parseInteger(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        return Integer.parseInt(value);
    }

    private String nullableDouble(Double value) {
        return value == null ? "" : value.toString();
    }

    private String nullableBoolean(Boolean value) {
        return value == null ? "" : value.toString();
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
    }

    private String unescape(String value) {
        return value
                .replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }
}
