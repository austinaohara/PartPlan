package service;

import model.InspectionPlan;
import model.PlanDrawing;

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

    public void savePlan(InspectionPlan plan) {
        if (plan == null) {
            throw new IllegalArgumentException("plan must not be null");
        }

        try {
            Path planDirectory = getPlanDirectory(plan.getId());
            Files.createDirectories(planDirectory);

            PlanDrawing savedDrawing = copyDrawingIfNeeded(plan, planDirectory);
            if (savedDrawing != null) {
                plan.setDrawing(savedDrawing);
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
        } else {
            builder.append("\n");
        }

        builder.append("}\n");
        return builder.toString();
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