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
import model.PlanPage;
import service.PdfPageRenderingService;
import service.PlanStorageService;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class PlanEditorViewModel {
    private static final String DEFAULT_PLAN_NAME = "New Inspection Plan";

    private final PlanStorageService storageService = new PlanStorageService();
    private final PdfPageRenderingService pdfPageRenderingService = new PdfPageRenderingService();
    private final ObjectProperty<InspectionPlan> currentPlan = new SimpleObjectProperty<>();
    private final ObjectProperty<PlanPage> selectedPage = new SimpleObjectProperty<>();
    private final ObservableList<InspectionPlan> savedPlans = FXCollections.observableArrayList();
    private final ObservableList<PlanPage> planPages = FXCollections.observableArrayList();
    private final StringProperty planName = new SimpleStringProperty();
    private final StringProperty drawingFileName = new SimpleStringProperty("No drawing selected");
    private final StringProperty drawingPath = new SimpleStringProperty("");
    private final StringProperty pageName = new SimpleStringProperty("");
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

    public void importDrawing(File drawingFile) {
        Objects.requireNonNull(drawingFile, "drawingFile must not be null");

        InspectionPlan plan = requireCurrentPlan();
        if (isPdf(drawingFile)) {
            importPdfPages(plan, drawingFile);
            return;
        }

        addPageFromFile(plan, drawingFile);
    }

    private void importPdfPages(InspectionPlan plan, File pdfFile) {
        Path outputDirectory = Path.of(
                System.getProperty("user.home"),
                ".partplan",
                "imports",
                plan.getId(),
                stripExtension(pdfFile.getName()) + "-" + System.nanoTime()
        );
        List<File> renderedPages = pdfPageRenderingService.renderPdfPages(pdfFile, outputDirectory);
        PlanPage firstImportedPage = null;
        for (File renderedPage : renderedPages) {
            PlanPage page = addPageFromFile(plan, renderedPage);
            if (firstImportedPage == null) {
                firstImportedPage = page;
            }
        }

        if (firstImportedPage != null) {
            selectPage(firstImportedPage);
        }
    }

    private PlanPage addPageFromFile(InspectionPlan plan, File drawingFile) {
        PlanDrawing drawing = new PlanDrawing(
                drawingFile.getName(),
                drawingFile.getAbsolutePath(),
                determineFileType(drawingFile.getName())
        );
        int pageNumber = plan.nextPageNumber();
        PlanPage page = new PlanPage("Page " + pageNumber, pageNumber, drawing);
        plan.addPage(page);
        planPages.setAll(plan.getPages());
        selectPage(page);
        return page;
    }

    public void selectPage(PlanPage page) {
        selectedPage.set(page);
        if (page == null || page.getDrawing() == null) {
            clearDrawingState();
            return;
        }

        pageName.set(page.getName());
        updateDrawingState(page.getDrawing());
    }

    public void saveCurrentPlan() {
        InspectionPlan plan = requireCurrentPlan();
        storageService.savePlan(plan);
        planPages.setAll(plan.getPages());
        if (!planPages.isEmpty()) {
            selectPage(planPages.getFirst());
        } else if (plan.getDrawing() != null) {
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

    public ObservableList<InspectionPlan> getSavedPlans(){return savedPlans;}
    public ObservableList<PlanPage> getPlanPages() {
        return planPages;
    }

    public PlanPage getSelectedPage() {
        return selectedPage.get();
    }

    public ObjectProperty<PlanPage> selectedPageProperty() {
        return selectedPage;
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

    public String getPageName() {
        return pageName.get();
    }

    public StringProperty pageNameProperty() {
        return pageName;
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
        planPages.setAll(plan.getPages());

        if (planPages.isEmpty()) {
            clearDrawingState();
            return;
        }

        selectPage(planPages.getFirst());
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

    private boolean isPdf(File file) {
        return determineFileType(file.getName()).equals("pdf");
    }

    private String stripExtension(String fileName) {
        int extensionIndex = fileName.lastIndexOf('.');
        String baseName = extensionIndex < 0 ? fileName : fileName.substring(0, extensionIndex);
        return baseName.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private void clearDrawingState() {
        selectedPage.set(null);
        pageName.set("");
        drawingFileName.set("No drawing selected");
        drawingPath.set("");
        drawingLoaded.set(false);
    }
}
