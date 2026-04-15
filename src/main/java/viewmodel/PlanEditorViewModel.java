package viewmodel;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import model.Bubble;
import model.InspectionPlan;
import model.InspectionType;
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
    private final ObjectProperty<Bubble> selectedBubble = new SimpleObjectProperty<>();
    private final ObservableList<InspectionPlan> savedPlans = FXCollections.observableArrayList();
    private final ObservableList<PlanPage> planPages = FXCollections.observableArrayList();
    private final ObservableList<Bubble> pageBubbles = FXCollections.observableArrayList();
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
        selectedBubble.set(null);
        if (page == null || page.getDrawing() == null) {
            clearDrawingState();
            return;
        }

        pageName.set(page.getName());
        updateDrawingState(page.getDrawing());
        refreshPageBubbles();
    }

    public void saveCurrentPlan() {
        persistCurrentPlanState();
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

    public ObservableList<Bubble> getPageBubbles() {
        return pageBubbles;
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

    public Bubble getSelectedBubble() {
        return selectedBubble.get();
    }

    public ObjectProperty<Bubble> selectedBubbleProperty() {
        return selectedBubble;
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

    public Bubble placeBubble(double x, double y) {
        InspectionPlan plan = requireCurrentPlan();
        PlanPage page = selectedPage.get();
        if (page == null) {
            throw new IllegalStateException("No page is currently selected.");
        }

        int sequenceNumber = nextBubbleSequenceNumberForPage(page.getId());
        Bubble bubble = new Bubble(page.getId(), x, y, sequenceNumber);
        bubble.setInspectionType(InspectionType.NUMERIC);
        plan.addBubble(bubble);
        refreshPageBubbles();
        selectedBubble.set(bubble);
        return bubble;
    }

    public void selectBubble(Bubble bubble) {
        selectedBubble.set(bubble);
    }

    public void saveSelectedBubble(
            String characteristic,
            InspectionType inspectionType,
            String nominalValueText,
            String lowerToleranceText,
            String upperToleranceText,
            String note
    ) {
        Bubble bubble = selectedBubble.get();
        if (bubble == null) {
            return;
        }

        bubble.setCharacteristic(valueOrEmpty(characteristic));
        bubble.setInspectionType(inspectionType == null ? InspectionType.NUMERIC : inspectionType);
        bubble.setNominalValue(parseNullableDouble(nominalValueText));
        bubble.setLowerTolerance(parseNullableDouble(lowerToleranceText));
        bubble.setUpperTolerance(parseNullableDouble(upperToleranceText));
        bubble.setNote(valueOrEmpty(note));
        refreshPageBubbles();
        persistPlanSilently();
    }

    public void moveBubble(Bubble bubble, double x, double y) {
        if (bubble == null) {
            return;
        }

        bubble.setX(x);
        bubble.setY(y);
        refreshPageBubbles();
    }

    public void persistBubbleLayout() {
        persistPlanSilently();
    }

    private void loadPlan(InspectionPlan plan) {
        normalizeBubblePageIds(plan);
        currentPlan.set(plan);
        selectedBubble.set(null);
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
        selectedBubble.set(null);
        pageBubbles.clear();
        pageName.set("");
        drawingFileName.set("No drawing selected");
        drawingPath.set("");
        drawingLoaded.set(false);
    }

    private void persistCurrentPlanState() {
        persistPlanSilently();
    }

    private void persistPlanSilently() {
        InspectionPlan plan = requireCurrentPlan();
        PlanPage currentPage = selectedPage.get();
        Bubble currentBubble = selectedBubble.get();

        storageService.savePlan(plan);
        planPages.setAll(plan.getPages());

        if (planPages.isEmpty()) {
            clearDrawingState();
            refreshSavedPlans();
            return;
        }

        PlanPage matchingPage = currentPage == null
                ? planPages.getFirst()
                : planPages.stream()
                .filter(page -> page.getId().equals(currentPage.getId()))
                .findFirst()
                .orElse(planPages.getFirst());
        selectedPage.set(matchingPage);
        pageName.set(matchingPage.getName());
        updateDrawingState(matchingPage.getDrawing());
        refreshPageBubbles();

        Bubble matchingBubble = currentBubble == null
                ? null
                : plan.getBubbles().stream()
                .filter(bubble -> bubble.getId().equals(currentBubble.getId()))
                .findFirst()
                .orElse(null);
        selectedBubble.set(matchingBubble);
        refreshSavedPlans();
    }

    private void refreshPageBubbles() {
        InspectionPlan plan = currentPlan.get();
        PlanPage page = selectedPage.get();
        if (plan == null || page == null) {
            pageBubbles.clear();
            return;
        }

        pageBubbles.setAll(plan.getBubbles().stream()
                .filter(bubble -> page.getId().equals(bubble.getPageId()))
                .toList());
    }

    private void normalizeBubblePageIds(InspectionPlan plan) {
        if (plan == null || plan.getPages().isEmpty()) {
            return;
        }

        String defaultPageId = plan.getPages().getFirst().getId();
        for (Bubble bubble : plan.getBubbles()) {
            if (bubble.getPageId() == null || bubble.getPageId().isBlank()) {
                bubble.setPageId(defaultPageId);
            }
        }
    }

    private int nextBubbleSequenceNumberForPage(String pageId) {
        InspectionPlan plan = requireCurrentPlan();
        return plan.getBubbles().stream()
                .filter(bubble -> pageId.equals(bubble.getPageId()))
                .mapToInt(Bubble::getSequenceNumber)
                .max()
                .orElse(0) + 1;
    }

    private Double parseNullableDouble(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Double.parseDouble(value.trim());
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
