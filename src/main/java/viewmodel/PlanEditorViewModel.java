package viewmodel;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import model.InspectionPlan;
import model.PlanDrawing;
import service.PlanStorageService;

import java.io.File;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class PlanEditorViewModel {
    private static final String DEFAULT_PLAN_NAME = "New Inspection Plan";

    private final PlanStorageService storageService = new PlanStorageService();
    private final ObjectProperty<InspectionPlan> currentPlan = new SimpleObjectProperty<>();
    private final ObservableList<InspectionPlan> savedPlans = FXCollections.observableArrayList();
    private final StringProperty planName = new SimpleStringProperty();
    private final StringProperty drawingFileName = new SimpleStringProperty("No drawing selected");
    private final StringProperty drawingPath = new SimpleStringProperty("");
    private final BooleanProperty drawingLoaded = new SimpleBooleanProperty(false);

    public PlanEditorViewModel() {
        createNewPlan();
        refreshSavedPlans();
    }

    public void createNewPlan() {
        InspectionPlan plan = new InspectionPlan(DEFAULT_PLAN_NAME);
        loadPlan(plan);
    }

    public void renamePlan(String newName) {
        InspectionPlan plan = requireCurrentPlan();
        String sanitizedName = sanitizePlanName(newName);
        plan.rename(sanitizedName);
        planName.set(plan.getName());
    }

    public void importDrawing(File imageFile) {
        Objects.requireNonNull(imageFile, "imageFile must not be null");

        InspectionPlan plan = requireCurrentPlan();
        try {
            Path planDirectory = Path.of("app-data", "plans", plan.getId());
            Files.createDirectories(planDirectory);
            Path target = planDirectory.resolve(imageFile.getName());

            Files.copy(imageFile.toPath(), target, StandardCopyOption.REPLACE_EXISTING);

            PlanDrawing drawing = new PlanDrawing(
                    imageFile.getName(),
                    target.toString(),
                    determineFileType(imageFile.getName())
            );

            plan.setDrawing(drawing);
            drawingFileName.set(drawing.getFileName());
            drawingPath.set(drawing.getStoredPath());
            drawingLoaded.set(true);
        } catch (IOException e) {
            throw new RuntimeException("Failed to restore drawing", e);
        }

    }

    public void setCurrentPlan(InspectionPlan plan) {
        currentPlan.set(plan);
        planName.set(plan.getName());
        PlanDrawing drawing = plan.getDrawing();

        if (drawing != null && drawing.getStoredPath() != null) {
            File file = new File(drawing.getStoredPath());
            if (file.exists()) {
                drawingFileName.set(drawing.getFileName());
                drawingPath.set(drawing.getStoredPath());
                drawingLoaded.set(true);
            } else {
                clearDrawingState();
            }
        } else {
            clearDrawingState();
        }
    }

    public void saveCurrentPlan() {
        InspectionPlan plan = requireCurrentPlan();
        storageService.savePlan(plan);
        if (plan.getDrawing() != null) {
            updateDrawingState(plan.getDrawing());
        }
        refreshSavedPlans();
    }

    public void openPlan(InspectionPlan selectedPlan) {
        if (selectedPlan == null) {
            return;
        }

        InspectionPlan loadedPlan = storageService.loadPlan(selectedPlan.getId());
        loadPlan(loadedPlan);
        refreshSavedPlans();
    }

    public void deletePlan(InspectionPlan selectedPlan) {
        if (selectedPlan == null) {
            return;
        }
        storageService.deletePlan(selectedPlan.getId());
        refreshSavedPlans();

        InspectionPlan plan = currentPlan.get();
        if (plan != null && plan.getId().equals(selectedPlan.getId())) {
            createNewPlan();
        }
    }

    public void refreshSavedPlans() {
        List<InspectionPlan> plans = storageService.loadPlans();
        savedPlans.setAll(plans);
    }

    public boolean hasDrawing() {
        return drawingLoaded.get();
    }

    public InspectionPlan getCurrentPlan() {
        return currentPlan.get();
    }

    public ObservableList<InspectionPlan> getSavedPlans() {
        return savedPlans;
    }

    public ObjectProperty<InspectionPlan> currentPlanProperty() {
        return currentPlan;
    }

    public String getPlanName() {
        return planName.get();
    }

    public StringProperty planNameProperty() {
        return planName;
    }

    public String getDrawingFileName() {
        return drawingFileName.get();
    }

    public StringProperty drawingFileNameProperty() {
        return drawingFileName;
    }

    public String getDrawingPath() {
        return drawingPath.get();
    }

    public StringProperty drawingPathProperty() {
        return drawingPath;
    }

    public boolean isDrawingLoaded() {
        return drawingLoaded.get();
    }

    public BooleanProperty drawingLoadedProperty() {
        return drawingLoaded;
    }

    private void loadPlan(InspectionPlan plan) {
        currentPlan.set(plan);
        planName.set(plan.getName());

        if (plan.getDrawing() == null) {
            clearDrawingState();
            return;
        }

        updateDrawingState(plan.getDrawing());
    }

    private void updateDrawingState(PlanDrawing drawing) {
        drawingFileName.set(drawing.getFileName());
        drawingPath.set(drawing.getStoredPath());
        drawingLoaded.set(true);
    }

    private InspectionPlan requireCurrentPlan() {
        InspectionPlan plan = currentPlan.get();
        if (plan == null) {
            throw new IllegalStateException("No inspection plan is currently loaded.");
        }
        return plan;
    }

    private String sanitizePlanName(String proposedName) {
        if (proposedName == null || proposedName.isBlank()) {
            return DEFAULT_PLAN_NAME;
        }
        return proposedName.trim();
    }

    private String determineFileType(String fileName) {
        int extensionIndex = fileName.lastIndexOf('.');
        if (extensionIndex < 0 || extensionIndex == fileName.length() - 1) {
            return "unknown";
        }
        return fileName.substring(extensionIndex + 1).toLowerCase(Locale.ROOT);
    }

    private void clearDrawingState() {
        drawingFileName.set("No drawing selected");
        drawingPath.set("");
        drawingLoaded.set(false);
    }
}
