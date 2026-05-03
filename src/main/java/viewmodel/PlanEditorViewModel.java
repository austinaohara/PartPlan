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
import model.InspectionLotSummary;
import model.InspectionPlan;
import model.InspectionType;
import model.PlanDrawing;
import model.PlanPage;
import service.PdfPageRenderingService;
import service.asset.ImportWorkspace;
import service.autoballoon.AutoBalloonCandidate;
import service.autoballoon.AutoBalloonDetectionService;
import service.autoballoon.AutoBalloonRequest;
import service.repository.LotRepository;
import service.repository.PlanRepository;
import service.util.ModelCopies;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Objects;

public class PlanEditorViewModel {
    private static final String DEFAULT_PLAN_NAME = "New Inspection Plan";
    private static final Set<String> GDT_CHARACTERISTICS = Set.of(
            "position",
            "parallelism",
            "perpendicularity",
            "flatness",
            "straightness",
            "circularity",
            "cylindricity",
            "concentricity",
            "symmetry",
            "circular runout",
            "total runout",
            "profile of a line",
            "profile of a surface",
            "profile",
            "angularity"
    );

    private final PlanRepository storageService;
    private final LotRepository lotRepository;
    private final ImportWorkspace assetStore;
    private final PdfPageRenderingService pdfPageRenderingService;
    private final AutoBalloonDetectionService autoBalloonDetectionService;
    private final ObjectProperty<InspectionPlan> currentPlan = new SimpleObjectProperty<>();
    private final ObjectProperty<PlanPage> selectedPage = new SimpleObjectProperty<>();
    private final ObjectProperty<Bubble> selectedBubble = new SimpleObjectProperty<>();
    private final ObservableList<InspectionPlan> savedPlans = FXCollections.observableArrayList();
    private final ObservableList<PlanPage> planPages = FXCollections.observableArrayList();
    private final ObservableList<Bubble> pageBubbles = FXCollections.observableArrayList();
    private final StringProperty planName = new SimpleStringProperty();
    private final StringProperty planStatus = new SimpleStringProperty("Pending");
    private final StringProperty planVersion = new SimpleStringProperty("Draft");
    private final StringProperty drawingFileName = new SimpleStringProperty("No drawing selected");
    private final StringProperty drawingPath = new SimpleStringProperty("");
    private final StringProperty pageName = new SimpleStringProperty("");
    private final StringProperty saveState = new SimpleStringProperty("");
    private final BooleanProperty drawingLoaded = new SimpleBooleanProperty(false);
    private final BooleanProperty currentPlanEditable = new SimpleBooleanProperty(true);
    private final BooleanProperty currentPlanComplete = new SimpleBooleanProperty(false);
    private final BooleanProperty unsavedChanges = new SimpleBooleanProperty(false);
    private final BooleanProperty saveInProgress = new SimpleBooleanProperty(false);

    public PlanEditorViewModel(
            PlanRepository storageService,
            LotRepository lotRepository,
            ImportWorkspace assetStore,
            PdfPageRenderingService pdfPageRenderingService,
            AutoBalloonDetectionService autoBalloonDetectionService
    ) {
        this.storageService = Objects.requireNonNull(storageService, "storageService must not be null");
        this.lotRepository = Objects.requireNonNull(lotRepository, "lotRepository must not be null");
        this.assetStore = Objects.requireNonNull(assetStore, "assetStore must not be null");
        this.pdfPageRenderingService = Objects.requireNonNull(pdfPageRenderingService, "pdfPageRenderingService must not be null");
        this.autoBalloonDetectionService = Objects.requireNonNull(autoBalloonDetectionService, "autoBalloonDetectionService must not be null");
        createNewPlan();
    }

    public void createNewPlan() {
        InspectionPlan plan = new InspectionPlan(DEFAULT_PLAN_NAME);
        loadPlan(plan);
    }

    public void renamePlan(String newName) {
        ensureCurrentPlanEditable();
        InspectionPlan plan = requireCurrentPlan();
        String sanitizedName = sanitizePlanName(newName);
        plan.rename(sanitizedName);
        planName.set(plan.getName());
        markDirty();
    }

    public void importDrawing(File drawingFile) {
        Objects.requireNonNull(drawingFile, "drawingFile must not be null");
        ensureCurrentPlanEditable();

        InspectionPlan plan = requireCurrentPlan();
        if (isPdf(drawingFile)) {
            importPdfPages(plan, drawingFile);
            return;
        }

        addPageFromFile(plan, drawingFile);
    }

    private void importPdfPages(InspectionPlan plan, File pdfFile) {
        Path outputDirectory = assetStore.createImportDirectory(plan.getId(), stripExtension(pdfFile.getName()));
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
        markDirty();
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

    public List<InspectionPlan> loadSavedPlansFromRepository() {
        return storageService.loadPlans();
    }

    public void applySavedPlans(List<InspectionPlan> plans) {
        List<InspectionPlan> copies = plans == null
                ? List.of()
                : plans.stream()
                .map(ModelCopies::copyPlan)
                .toList();
        savedPlans.setAll(copies);
        savedPlans.sort(java.util.Comparator.comparing(InspectionPlan::getUpdatedAt).reversed());
    }

    public InspectionPlan loadPlanFromRepository(String planId) {
        return storageService.loadPlan(planId);
    }

    public InspectionPlan completeCurrentPlanInRepository() {
        InspectionPlan plan = requireCurrentPlan();
        ensureCurrentPlanEditable();
        if (unsavedChanges.get()) {
            storageService.savePlan(ModelCopies.copyPlan(plan));
        }
        return storageService.completePlan(plan.getId());
    }

    public InspectionPlan createRevisionFromCurrentPlanInRepository() {
        InspectionPlan plan = requireCurrentPlan();
        if (!plan.isComplete()) {
            throw new IllegalStateException("Only complete plans can create a revision.");
        }
        return storageService.createRevision(plan.getId());
    }

    public void applyLoadedPlan(InspectionPlan plan) {
        loadPlan(plan);
    }

    public void addOrUpdateSavedPlan(InspectionPlan plan) {
        upsertSavedPlan(plan);
    }

    public void deletePlanInRepository(String planId) {
        if (planId == null || planId.isBlank()) {
            return;
        }
        lotRepository.deleteLotsForPlan(planId);
        storageService.deletePlan(planId);
    }

    public void applyDeletedPlan(String planId) {
        removeSavedPlan(planId);

        InspectionPlan plan = currentPlan.get();
        if (plan != null && plan.getId().equals(planId)) {
            createNewPlan();
        }
    }

    public List<InspectionLotSummary> loadAffectedLotsForPlan(String planId) {
        if (planId == null || planId.isBlank()) {
            return List.of();
        }
        return lotRepository.loadLotSummariesForPlan(planId);
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

    public StringProperty planStatusProperty() {
        return planStatus;
    }

    public StringProperty planVersionProperty() {
        return planVersion;
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

    public StringProperty saveStateProperty() {
        return saveState;
    }

    public boolean isDrawingLoaded() {
        return drawingLoaded.get();
    }

    public BooleanProperty drawingLoadedProperty() {
        return drawingLoaded;
    }

    public BooleanProperty currentPlanEditableProperty() {
        return currentPlanEditable;
    }

    public boolean isCurrentPlanEditable() {
        return currentPlanEditable.get();
    }

    public BooleanProperty currentPlanCompleteProperty() {
        return currentPlanComplete;
    }

    public boolean isCurrentPlanComplete() {
        return currentPlanComplete.get();
    }

    public BooleanProperty unsavedChangesProperty() {
        return unsavedChanges;
    }

    public boolean hasUnsavedChanges() {
        return unsavedChanges.get();
    }

    public BooleanProperty saveInProgressProperty() {
        return saveInProgress;
    }

    public InspectionPlan beginSaveSnapshot() {
        ensureCurrentPlanEditable();
        InspectionPlan snapshot = ModelCopies.copyPlan(requireCurrentPlan());
        unsavedChanges.set(false);
        saveInProgress.set(true);
        saveState.set("Saving...");
        return snapshot;
    }

    public void persistPlanSnapshot(InspectionPlan snapshot) {
        storageService.savePlan(snapshot);
    }

    public void finishSaveSuccess(InspectionPlan snapshot) {
        saveInProgress.set(false);
        upsertSavedPlan(snapshot);

        InspectionPlan livePlan = currentPlan.get();
        if (livePlan != null && livePlan.getId().equals(snapshot.getId()) && !unsavedChanges.get()) {
            livePlan.setCreatedAt(snapshot.getCreatedAt());
            livePlan.setUpdatedAt(snapshot.getUpdatedAt());
            refreshCurrentPlanMetadata(livePlan);
            saveState.set("Saved");
            return;
        }

        if (unsavedChanges.get()) {
            saveState.set("Unsaved changes");
        }
    }

    public void finishSaveFailure(String planId) {
        saveInProgress.set(false);
        InspectionPlan livePlan = currentPlan.get();
        if (livePlan != null && livePlan.getId().equals(planId) && livePlan.isPending()) {
            unsavedChanges.set(true);
            saveState.set("Unsaved changes");
        }
    }

    public AutoBalloonRequest createAutoBalloonRequest(int imageWidth, int imageHeight) {
        ensureCurrentPlanEditable();
        PlanPage page = selectedPage.get();
        if (page == null || page.getDrawing() == null) {
            throw new IllegalStateException("Select a drawing page before running auto-balloon.");
        }

        String drawingPath = page.getDrawing().getStoredPath();
        if (drawingPath == null || drawingPath.isBlank()) {
            throw new IllegalStateException("The selected page has no local drawing image available for auto-ballooning.");
        }

        return new AutoBalloonRequest(
                page.getId(),
                page.getName(),
                Path.of(drawingPath),
                imageWidth,
                imageHeight
        );
    }

    public List<AutoBalloonCandidate> detectAutoBalloonCandidates(AutoBalloonRequest request) {
        return autoBalloonDetectionService.detectBalloons(request);
    }

    public int applyAutoBalloonCandidates(List<AutoBalloonCandidate> candidates, int imageWidth, int imageHeight) {
        ensureCurrentPlanEditable();
        PlanPage page = selectedPage.get();
        if (page == null) {
            throw new IllegalStateException("Select a drawing page before applying auto-balloon suggestions.");
        }
        if (candidates == null || candidates.isEmpty()) {
            return 0;
        }
        if (imageWidth <= 0 || imageHeight <= 0) {
            throw new IllegalArgumentException("The selected drawing image dimensions are invalid.");
        }

        int addedCount = 0;
        for (AutoBalloonCandidate candidate : candidates) {
            String characteristic = valueOrEmpty(candidate.characteristic());
            String detectedText = valueOrEmpty(candidate.detectedText());
            String noteText = valueOrEmpty(candidate.noteText());
            if (characteristic.isBlank() && detectedText.isBlank() && noteText.isBlank()) {
                continue;
            }

            double x = clampNormalized(candidate.anchorX()) * imageWidth;
            double y = clampNormalized(candidate.anchorY()) * imageHeight;
            String resolvedCharacteristic = characteristic;
            if (resolvedCharacteristic.isBlank()) {
                resolvedCharacteristic = noteText.isBlank() ? detectedText : "Note";
            }
            String resolvedNote = noteText;
            if (resolvedNote.isBlank() && "Note".equalsIgnoreCase(resolvedCharacteristic)) {
                resolvedNote = detectedText;
            }
            boolean noteCharacteristic = "Note".equalsIgnoreCase(resolvedCharacteristic);
            boolean gdtCharacteristic = isGdtCharacteristic(resolvedCharacteristic);
            InspectionType inspectionType = noteCharacteristic
                    ? InspectionType.PASS_FAIL
                    : InspectionType.NUMERIC;
            Double nominal = candidate.nominal();
            Double lowerTolerance = absoluteOrNull(candidate.lowerTolerance());
            Double upperTolerance = absoluteOrNull(candidate.upperTolerance());
            if (gdtCharacteristic) {
                nominal = 0.0;
                lowerTolerance = 0.0;
                upperTolerance = resolveGdtUpperTolerance(candidate);
            } else if (noteCharacteristic) {
                nominal = null;
                lowerTolerance = null;
                upperTolerance = null;
            }
            placeBubble(
                    x,
                    y,
                    18.0,
                    true,
                    "#E53935",
                    true,
                    resolvedCharacteristic,
                    inspectionType,
                    nominal,
                    lowerTolerance,
                    upperTolerance,
                    resolvedNote
            );
            addedCount++;
        }

        if (addedCount > 0) {
            markDirty();
        }
        return addedCount;
    }

    public int currentPageBubbleCount() {
        return pageBubbles.size();
    }

    public void clearSelectedPageBubbles() {
        ensureCurrentPlanEditable();
        InspectionPlan plan = requireCurrentPlan();
        PlanPage page = selectedPage.get();
        if (page == null) {
            throw new IllegalStateException("No page is currently selected.");
        }

        List<Bubble> bubblesToRemove = plan.getBubbles().stream()
                .filter(bubble -> page.getId().equals(bubble.getPageId()))
                .toList();
        for (Bubble bubble : bubblesToRemove) {
            plan.removeBubble(bubble);
        }
        selectedBubble.set(null);
        refreshPageBubbles();
        if (!bubblesToRemove.isEmpty()) {
            markDirty();
        }
    }

    public Bubble placeBubble(double x, double y) {
        return placeBubble(x, y, 18.0, true, "#E53935", true, "", InspectionType.NUMERIC, null, null, null, "");
    }

    public Bubble placeBubble(
            double x,
            double y,
            double radius,
            boolean useDefaultDiameter,
            String color,
            boolean useDefaultColor,
            String characteristic,
            InspectionType inspectionType,
            Double nominalValue,
            Double lowerTolerance,
            Double upperTolerance,
            String note
    ) {
        ensureCurrentPlanEditable();
        InspectionPlan plan = requireCurrentPlan();
        PlanPage page = selectedPage.get();
        if (page == null) {
            throw new IllegalStateException("No page is currently selected.");
        }

        int sequenceNumber = nextBubbleSequenceNumberForPage(page.getId());
        Bubble bubble = new Bubble(page.getId(), x, y, sequenceNumber);
        bubble.setRadius(radius);
        bubble.setUseDefaultDiameter(useDefaultDiameter);
        bubble.setColor(color == null || color.isBlank() ? "#E53935" : color.trim());
        bubble.setUseDefaultColor(useDefaultColor);
        bubble.setCharacteristic(valueOrEmpty(characteristic));
        bubble.setInspectionType(inspectionType == null ? InspectionType.NUMERIC : inspectionType);
        bubble.setNominalValue(nominalValue);
        bubble.setLowerTolerance(lowerTolerance);
        bubble.setUpperTolerance(upperTolerance);
        bubble.setNote(valueOrEmpty(note));
        plan.addBubble(bubble);
        refreshPageBubbles();
        selectedBubble.set(bubble);
        return bubble;
    }

    public void selectBubble(Bubble bubble) {
        selectedBubble.set(bubble);
    }

    public void saveSelectedBubble(
            int sequenceNumber,
            double radius,
            boolean useDefaultDiameter,
            String color,
            boolean useDefaultColor,
            String characteristic,
            InspectionType inspectionType,
            String nominalValueText,
            String lowerToleranceText,
            String upperToleranceText,
            String note
    ) {
        ensureCurrentPlanEditable();
        Bubble bubble = selectedBubble.get();
        if (bubble == null) {
            return;
        }

        InspectionPlan plan = requireCurrentPlan();
        plan.moveBubbleToSequence(bubble, sequenceNumber);
        bubble.setRadius(radius);
        bubble.setUseDefaultDiameter(useDefaultDiameter);
        bubble.setColor(color == null || color.isBlank() ? "#E53935" : color.trim());
        bubble.setUseDefaultColor(useDefaultColor);
        bubble.setCharacteristic(valueOrEmpty(characteristic));
        bubble.setInspectionType(inspectionType == null ? InspectionType.NUMERIC : inspectionType);
        bubble.setNominalValue(parseNullableDouble(nominalValueText));
        bubble.setLowerTolerance(parseNullableDouble(lowerToleranceText));
        bubble.setUpperTolerance(parseNullableDouble(upperToleranceText));
        bubble.setNote(valueOrEmpty(note));
        refreshPageBubbles();
        markDirty();
    }

    public void moveBubble(Bubble bubble, double x, double y) {
        ensureCurrentPlanEditable();
        if (bubble == null) {
            return;
        }

        bubble.setX(x);
        bubble.setY(y);
        refreshPageBubbles();
        markDirty();
    }

    public void persistBubbleLayout() {
        ensureCurrentPlanEditable();
        markDirty();
    }

    public Bubble copySelectedBubble() {
        ensureCurrentPlanEditable();
        Bubble source = selectedBubble.get();
        if (source == null) {
            return null;
        }
        InspectionPlan plan = requireCurrentPlan();
        PlanPage page = selectedPage.get();
        if (page == null) {
            return null;
        }
        int sequenceNumber = nextBubbleSequenceNumberForPage(page.getId());
        Bubble copy = new Bubble(
                java.util.UUID.randomUUID().toString(),
                source.getPageId(),
                source.getX() + 20.0,
                source.getY() + 20.0,
                source.getRadius(),
                source.isUseDefaultDiameter(),
                source.getColor(),
                source.isUseDefaultColor(),
                String.valueOf(sequenceNumber),
                source.getCharacteristic(),
                source.getInspectionType(),
                source.getNominalValue(),
                source.getLowerTolerance(),
                source.getUpperTolerance(),
                source.getExpectedPassFail(),
                null,
                null,
                model.BubbleStatus.OPEN,
                source.getNote(),
                sequenceNumber,
                java.time.LocalDateTime.now(),
                java.time.LocalDateTime.now()
        );
        plan.addBubble(copy);
        refreshPageBubbles();
        selectedBubble.set(copy);
        markDirty();
        return copy;
    }

    public void deleteSelectedBubble() {
        ensureCurrentPlanEditable();
        Bubble bubble = selectedBubble.get();
        if (bubble == null) {
            return;
        }

        InspectionPlan plan = requireCurrentPlan();
        plan.removeBubble(bubble);
        selectedBubble.set(null);
        refreshPageBubbles();
        markDirty();
    }

    public void applyBubbleDefaults(double diameter, String color) {
        ensureCurrentPlanEditable();
        InspectionPlan plan = requireCurrentPlan();
        double radius = diameter / 2.0;
        String normalizedColor = color == null || color.isBlank() ? "#E53935" : color.trim();

        for (Bubble bubble : plan.getBubbles()) {
            if (bubble.isUseDefaultDiameter()) {
                bubble.setRadius(radius);
            }
            if (bubble.isUseDefaultColor()) {
                bubble.setColor(normalizedColor);
            }
        }

        refreshPageBubbles();
        markDirty();
    }

    private void loadPlan(InspectionPlan plan) {
        normalizeBubblePageIds(plan);
        currentPlan.set(plan);
        selectedBubble.set(null);
        planName.set(plan.getName());
        refreshCurrentPlanMetadata(plan);
        planPages.setAll(plan.getPages());
        unsavedChanges.set(false);
        saveInProgress.set(false);
        saveState.set("");

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

    private void markDirty() {
        InspectionPlan plan = currentPlan.get();
        if (plan != null && plan.isPending()) {
            unsavedChanges.set(true);
            saveState.set("Unsaved changes");
        }
    }

    private double clampNormalized(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.5;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }

    private Double absoluteOrNull(Double value) {
        return value == null ? null : Math.abs(value);
    }

    private boolean isGdtCharacteristic(String characteristic) {
        if (characteristic == null || characteristic.isBlank()) {
            return false;
        }
        return GDT_CHARACTERISTICS.contains(characteristic.trim().toLowerCase(Locale.ROOT));
    }

    private Double resolveGdtUpperTolerance(AutoBalloonCandidate candidate) {
        if (candidate.upperTolerance() != null) {
            return Math.abs(candidate.upperTolerance());
        }
        if (candidate.lowerTolerance() != null) {
            return Math.abs(candidate.lowerTolerance());
        }
        if (candidate.nominal() != null) {
            return Math.abs(candidate.nominal());
        }
        return null;
    }

    private void upsertSavedPlan(InspectionPlan plan) {
        InspectionPlan copy = ModelCopies.copyPlan(plan);
        for (int index = 0; index < savedPlans.size(); index++) {
            if (savedPlans.get(index).getId().equals(copy.getId())) {
                savedPlans.set(index, copy);
                savedPlans.sort(java.util.Comparator.comparing(InspectionPlan::getUpdatedAt).reversed());
                return;
            }
        }
        savedPlans.add(copy);
        savedPlans.sort(java.util.Comparator.comparing(InspectionPlan::getUpdatedAt).reversed());
    }

    private void removeSavedPlan(String planId) {
        if (planId == null || planId.isBlank()) {
            return;
        }
        savedPlans.removeIf(plan -> planId.equals(plan.getId()));
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

    private void refreshCurrentPlanMetadata(InspectionPlan plan) {
        planStatus.set(plan == null ? "Pending" : plan.getStatus().name().charAt(0) + plan.getStatus().name().substring(1).toLowerCase(Locale.ROOT));
        planVersion.set(plan == null || plan.getVersion() <= 0 ? "Draft" : "v" + plan.getVersion());
        currentPlanEditable.set(plan != null && plan.isEditable());
        currentPlanComplete.set(plan != null && plan.isComplete());
    }

    private void ensureCurrentPlanEditable() {
        InspectionPlan plan = requireCurrentPlan();
        if (!plan.isEditable()) {
            throw new IllegalStateException("Complete plans are read-only. Create a revision to make changes.");
        }
    }
}
