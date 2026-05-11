package viewmodel;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import model.InspectionLot;
import model.InspectionPlan;
import model.PartBubbleDefinition;
import model.PartRecord;
import service.repository.LotRepository;
import service.repository.PlanRepository;
import service.util.ModelCopies;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class PartEditorViewModel {
    private static final String NO_LOT_SELECTED = "No inspection lot selected";
    private static final String NO_PLAN_SELECTED = "No plan selected";
    private static final String NO_UPVERSION_AVAILABLE = "No newer version";

    private final LotRepository lotRepository;
    private final PlanRepository planRepository;
    private final ObservableList<PartRecord> parts = FXCollections.observableArrayList();
    private final ObservableList<PartBubbleDefinition> bubbles = FXCollections.observableArrayList();
    private final ObservableList<PartBubbleRowViewModel> currentPartRows = FXCollections.observableArrayList();
    private final IntegerProperty lotSize = new SimpleIntegerProperty(1);
    private final IntegerProperty currentPartNumber = new SimpleIntegerProperty(1);
    private final StringProperty currentPartTitle = new SimpleStringProperty("Create or open an inspection lot to begin.");
    private final StringProperty lotSummary = new SimpleStringProperty(NO_LOT_SELECTED);
    private final StringProperty currentLotName = new SimpleStringProperty("");
    private final StringProperty currentPlanName = new SimpleStringProperty(NO_PLAN_SELECTED);
    private final StringProperty saveState = new SimpleStringProperty("");
    private final BooleanProperty lotLoaded = new SimpleBooleanProperty(false);
    private final BooleanProperty upversionAvailable = new SimpleBooleanProperty(false);
    private final BooleanProperty unsavedChanges = new SimpleBooleanProperty(false);
    private final BooleanProperty saveInProgress = new SimpleBooleanProperty(false);
    private final BooleanProperty canUndo = new SimpleBooleanProperty(false);
    private final BooleanProperty canRedo = new SimpleBooleanProperty(false);
    private final StringProperty upversionTargetLabel = new SimpleStringProperty(NO_UPVERSION_AVAILABLE);
    private final List<EditorState> history = new ArrayList<>();
    private int historyIndex = -1;
    private int cleanHistoryIndex = -1;
    private boolean showSavedStateWhenClean;

    private InspectionLot currentLot;
    private InspectionPlan latestUpversionTarget;
    private List<InspectionPlan> availableCompletePlans = List.of();

    public PartEditorViewModel(LotRepository lotRepository, PlanRepository planRepository) {
        this.lotRepository = lotRepository;
        this.planRepository = planRepository;
        refreshAll();
    }

    public ObservableList<PartRecord> getParts() {
        return parts;
    }

    public ObservableList<PartBubbleDefinition> getBubbles() {
        return bubbles;
    }

    public ObservableList<PartBubbleRowViewModel> getCurrentPartRows() {
        return currentPartRows;
    }

    public int getLotSize() {
        return lotSize.get();
    }

    public IntegerProperty lotSizeProperty() {
        return lotSize;
    }

    public int getCurrentPartNumber() {
        return currentPartNumber.get();
    }

    public IntegerProperty currentPartNumberProperty() {
        return currentPartNumber;
    }

    public StringProperty currentPartTitleProperty() {
        return currentPartTitle;
    }

    public StringProperty lotSummaryProperty() {
        return lotSummary;
    }

    public StringProperty currentLotNameProperty() {
        return currentLotName;
    }

    public StringProperty currentPlanNameProperty() {
        return currentPlanName;
    }

    public BooleanProperty lotLoadedProperty() {
        return lotLoaded;
    }

    public BooleanProperty upversionAvailableProperty() {
        return upversionAvailable;
    }

    public BooleanProperty unsavedChangesProperty() {
        return unsavedChanges;
    }

    public BooleanProperty saveInProgressProperty() {
        return saveInProgress;
    }

    public BooleanProperty canUndoProperty() {
        return canUndo;
    }

    public BooleanProperty canRedoProperty() {
        return canRedo;
    }

    public boolean canUndo() {
        return canUndo.get();
    }

    public boolean canRedo() {
        return canRedo.get();
    }

    public StringProperty saveStateProperty() {
        return saveState;
    }

    public StringProperty upversionTargetLabelProperty() {
        return upversionTargetLabel;
    }

    public void saveCurrentLotName(String proposedName) {
        if (currentLot == null) {
            return;
        }

        String normalizedName = normalizeLotName(proposedName, currentLot.getName());
        if (!normalizedName.equals(currentLot.getName())) {
            currentLot.setName(normalizedName);
            refreshAll();
            commitLotChange();
        }
    }

    public void setLotSize(int value) {
        if (currentLot == null) {
            lotSize.set(Math.max(1, value));
            return;
        }

        int normalizedValue = Math.max(1, value);
        if (currentLot.getLotSize() == normalizedValue) {
            return;
        }

        currentLot.setLotSize(normalizedValue);
        if (currentPartNumber.get() > currentLot.getLotSize()) {
            currentPartNumber.set(currentLot.getLotSize());
        }
        refreshAll();
        commitLotChange();
    }

    public void selectPart(int partNumber) {
        if (currentLot == null) {
            return;
        }

        int boundedPartNumber = Math.max(1, Math.min(partNumber, currentLot.getLotSize()));
        currentPartNumber.set(boundedPartNumber);
        refreshCurrentPartRows();
        refreshText();
    }

    public void selectNextPart() {
        selectPart(currentPartNumber.get() + 1);
    }

    public void selectPreviousPart() {
        selectPart(currentPartNumber.get() - 1);
    }

    public void updateCurrentPartMeasurement(String bubbleId, String value) {
        if (currentLot == null) {
            return;
        }

        PartRecord currentPart = currentLot.getPart(currentPartNumber.get() - 1);
        updatePartMeasurement(currentPart, bubbleId, value);
    }

    public void updateCurrentPartComment(String bubbleId, String value) {
        if (currentLot == null) {
            return;
        }

        PartRecord currentPart = currentLot.getPart(currentPartNumber.get() - 1);
        updatePartComment(currentPart, bubbleId, value);
    }

    public void updatePartMeasurement(PartRecord part, String bubbleId, String value) {
        if (currentLot == null || part == null) {
            return;
        }

        String normalizedValue = value == null ? "" : value.trim();
        if (Objects.equals(part.getMeasurement(bubbleId), normalizedValue)) {
            return;
        }

        int targetPartNumber = Math.max(1, Math.min(part.getPartNumber(), currentLot.getLotSize()));
        boolean switchedCurrentPart = currentPartNumber.get() != targetPartNumber;
        if (switchedCurrentPart) {
            currentPartNumber.set(targetPartNumber);
        }

        part.setMeasurement(bubbleId, normalizedValue);

        if (switchedCurrentPart) {
            refreshCurrentPartRows();
            refreshText();
        } else {
            refreshCurrentPartRows();
        }
        commitLotChange();
    }

    public void updatePartComment(PartRecord part, String bubbleId, String value) {
        if (currentLot == null || part == null) {
            return;
        }

        String normalizedValue = value == null ? "" : value.trim();
        if (Objects.equals(part.getComment(bubbleId), normalizedValue)) {
            return;
        }

        int targetPartNumber = Math.max(1, Math.min(part.getPartNumber(), currentLot.getLotSize()));
        boolean switchedCurrentPart = currentPartNumber.get() != targetPartNumber;
        if (switchedCurrentPart) {
            currentPartNumber.set(targetPartNumber);
        }

        part.setComment(bubbleId, normalizedValue);

        if (switchedCurrentPart) {
            refreshCurrentPartRows();
            refreshText();
        } else {
            refreshCurrentPartRows();
        }
        commitLotChange();
    }

    public InspectionLot beginSaveSnapshot() {
        if (currentLot == null) {
            throw new IllegalStateException("No inspection lot is loaded.");
        }

        InspectionLot snapshot = ModelCopies.copyLot(currentLot);
        saveInProgress.set(true);
        saveState.set("Saving...");
        refreshHistoryAvailability();
        return snapshot;
    }

    public void persistLotSnapshot(InspectionLot snapshot) {
        lotRepository.saveLotStructure(snapshot);
    }

    public void finishSaveSuccess(InspectionLot snapshot) {
        saveInProgress.set(false);
        if (currentLot != null && currentLot.getId().equals(snapshot.getId())) {
            currentLot.setUpdatedAt(snapshot.getUpdatedAt());
            refreshAll();
            replaceCurrentHistoryState();
        }
        cleanHistoryIndex = historyIndex;
        showSavedStateWhenClean = true;
        refreshHistoryAvailability();
    }

    public void finishSaveFailure(String lotId) {
        saveInProgress.set(false);
        if (currentLot != null && currentLot.getId().equals(lotId)) {
            refreshHistoryAvailability();
        }
    }

    public LoadedLotData loadLotData(String lotId) {
        if (lotId == null || lotId.isBlank()) {
            return new LoadedLotData(null, availableCompletePlans);
        }

        return new LoadedLotData(
                lotRepository.loadLot(lotId),
                planRepository.loadCompletePlans()
        );
    }

    public void applyLoadedLot(LoadedLotData loadedLotData) {
        currentLot = loadedLotData == null ? null : loadedLotData.lot();
        availableCompletePlans = loadedLotData == null || loadedLotData.completePlans() == null
                ? List.of()
                : List.copyOf(loadedLotData.completePlans());
        currentPartNumber.set(1);
        lotLoaded.set(currentLot != null);
        saveInProgress.set(false);
        refreshAll();
        resetHistory();
    }

    public InspectionLot upversionCurrentLotInRepository() {
        if (currentLot == null) {
            return null;
        }
        if (latestUpversionTarget == null) {
            throw new IllegalStateException("No newer completed plan version is available for this inspection lot.");
        }

        InspectionPlan fullTargetPlan = planRepository.loadPlan(latestUpversionTarget.getId());
        return lotRepository.upversionLot(currentLot.getId(), fullTargetPlan);
    }

    public void applyUpversionedLot(InspectionLot updatedLot) {
        currentLot = updatedLot;
        currentPartNumber.set(1);
        saveInProgress.set(false);
        refreshAll();
        resetHistory();
    }

    public String getCurrentLotId() {
        return currentLot == null ? "" : currentLot.getId();
    }

    public InspectionLot getCurrentLot() {
        return currentLot;
    }

    public InspectionPlan getLatestUpversionTarget() {
        return latestUpversionTarget;
    }

    private void refreshAll() {
        lotLoaded.set(currentLot != null);
        lotSize.set(currentLot == null ? Math.max(1, lotSize.get()) : currentLot.getLotSize());
        parts.setAll(currentLot == null ? List.of() : currentLot.getParts());
        bubbles.setAll(currentLot == null ? List.of() : currentLot.getBubbles());
        currentLotName.set(currentLot == null ? "" : currentLot.getName());
        currentPlanName.set(currentLot == null ? NO_PLAN_SELECTED : formatPlanReference(currentLot));
        refreshUpversionState();
        refreshCurrentPartRows();
        refreshText();
        refreshHistoryAvailability();
    }

    private void refreshCurrentPartRows() {
        if (currentLot == null) {
            currentPartRows.clear();
            return;
        }

        PartRecord currentPart = currentLot.getPart(currentPartNumber.get() - 1);
        if (currentPart == null) {
            currentPartRows.clear();
            return;
        }

        currentPartRows.setAll(currentLot.getBubbles().stream()
                .map(bubble -> new PartBubbleRowViewModel(
                        bubble.getId(),
                        bubble.getSequenceNumber(),
                        bubble.getName(),
                        bubble.getInspectionType(),
                        bubble.getExpectedPassFail(),
                        bubble.getNominalValue(),
                        bubble.getLowerTolerance(),
                        bubble.getUpperTolerance(),
                        bubble.getNote(),
                        currentPart.getMeasurement(bubble.getId()),
                        currentPart.getComment(bubble.getId())
                ))
                .toList());
    }

    private void refreshText() {
        if (currentLot == null) {
            currentPartTitle.set("Create or open an inspection lot to begin.");
            lotSummary.set(NO_LOT_SELECTED);
            return;
        }

        currentPartTitle.set("Inspection Results for Part " + currentPartNumber.get() + " of " + currentLot.getLotSize());
        int bubbleCount = currentLot.getBubbles().size();
        lotSummary.set("%d %s | %d %s".formatted(
                currentLot.getLotSize(),
                currentLot.getLotSize() == 1 ? "part" : "parts",
                bubbleCount,
                bubbleCount == 1 ? "bubble" : "bubbles"
        ));
    }

    private String normalizeLotName(String proposedName, String fallback) {
        if (proposedName == null || proposedName.isBlank()) {
            return fallback == null || fallback.isBlank() ? "Inspection Lot" : fallback;
        }
        return proposedName.trim();
    }

    private String formatPlanReference(InspectionLot lot) {
        String baseName = lot.getPlanName() == null || lot.getPlanName().isBlank() ? "Untitled Plan" : lot.getPlanName().trim();
        if (lot.getPlanVersion() <= 0) {
            return baseName;
        }
        return baseName + " v" + lot.getPlanVersion();
    }

    private void refreshUpversionState() {
        if (currentLot == null) {
            latestUpversionTarget = null;
            upversionAvailable.set(false);
            upversionTargetLabel.set(NO_UPVERSION_AVAILABLE);
            return;
        }

        latestUpversionTarget = availableCompletePlans.stream()
                .filter(plan -> currentLot.getPlanFamilyId().equals(plan.getFamilyId()))
                .filter(plan -> plan.getVersion() > currentLot.getPlanVersion())
                .max(java.util.Comparator.comparingInt(InspectionPlan::getVersion))
                .orElse(null);

        upversionAvailable.set(latestUpversionTarget != null);
        upversionTargetLabel.set(latestUpversionTarget == null
                ? NO_UPVERSION_AVAILABLE
                : formatPlanReference(latestUpversionTarget));
    }

    public record LoadedLotData(InspectionLot lot, List<InspectionPlan> completePlans) {
    }

    private String formatPlanReference(InspectionPlan plan) {
        if (plan == null || plan.getName() == null || plan.getName().isBlank()) {
            return latestUpversionTarget == null || latestUpversionTarget.getVersion() <= 0
                    ? "Untitled Plan"
                    : "Untitled Plan v" + latestUpversionTarget.getVersion();
        }

        String baseName = plan.getName().trim();
        if (plan.getVersion() <= 0) {
            return baseName;
        }
        return baseName + " v" + plan.getVersion();
    }

    public void undo() {
        ensureUndoRedoAllowed();
        if (historyIndex <= 0) {
            return;
        }
        historyIndex--;
        restoreEditorState(history.get(historyIndex));
        refreshHistoryAvailability();
    }

    public void redo() {
        ensureUndoRedoAllowed();
        if (historyIndex < 0 || historyIndex >= history.size() - 1) {
            return;
        }
        historyIndex++;
        restoreEditorState(history.get(historyIndex));
        refreshHistoryAvailability();
    }

    private void commitLotChange() {
        if (saveInProgress.get() || currentLot == null) {
            return;
        }

        EditorState nextState = captureEditorState();
        if (!history.isEmpty() && historyIndex >= 0 && history.get(historyIndex).signature().equals(nextState.signature())) {
            refreshHistoryAvailability();
            return;
        }

        truncateRedoHistory();
        history.add(nextState);
        historyIndex = history.size() - 1;
        refreshHistoryAvailability();
    }

    private void resetHistory() {
        history.clear();
        if (currentLot == null) {
            historyIndex = -1;
            cleanHistoryIndex = -1;
            showSavedStateWhenClean = false;
            refreshHistoryAvailability();
            return;
        }

        history.add(captureEditorState());
        historyIndex = 0;
        cleanHistoryIndex = 0;
        showSavedStateWhenClean = false;
        refreshHistoryAvailability();
    }

    private void replaceCurrentHistoryState() {
        if (historyIndex < 0 || historyIndex >= history.size()) {
            return;
        }
        history.set(historyIndex, captureEditorState());
    }

    private void truncateRedoHistory() {
        if (historyIndex < history.size() - 1) {
            history.subList(historyIndex + 1, history.size()).clear();
            if (cleanHistoryIndex > historyIndex) {
                cleanHistoryIndex = -1;
                showSavedStateWhenClean = false;
            }
        }
    }

    private EditorState captureEditorState() {
        InspectionLot snapshot = ModelCopies.copyLot(currentLot);
        return new EditorState(
                snapshot,
                currentPartNumber.get(),
                buildLotSignature(snapshot)
        );
    }

    private void restoreEditorState(EditorState state) {
        if (state == null) {
            return;
        }
        applyLotState(ModelCopies.copyLot(state.lot()), state.currentPartNumber());
    }

    private void applyLotState(InspectionLot lot, int selectedPartNumber) {
        currentLot = lot;
        if (currentLot == null) {
            currentPartNumber.set(1);
            refreshAll();
            return;
        }

        int boundedPartNumber = Math.max(1, Math.min(selectedPartNumber, currentLot.getLotSize()));
        currentPartNumber.set(boundedPartNumber);
        refreshAll();
    }

    private void refreshHistoryAvailability() {
        if (currentLot == null) {
            canUndo.set(false);
            canRedo.set(false);
            unsavedChanges.set(false);
            if (!saveInProgress.get()) {
                saveState.set("");
            }
            return;
        }

        boolean undoAvailable = !saveInProgress.get() && historyIndex > 0;
        boolean redoAvailable = !saveInProgress.get() && historyIndex >= 0 && historyIndex < history.size() - 1;
        canUndo.set(undoAvailable);
        canRedo.set(redoAvailable);

        boolean dirty = historyIndex != cleanHistoryIndex;
        unsavedChanges.set(dirty);
        if (saveInProgress.get()) {
            return;
        }
        if (dirty) {
            saveState.set("Unsaved changes");
            return;
        }
        saveState.set(showSavedStateWhenClean ? "Saved" : "");
    }

    private void ensureUndoRedoAllowed() {
        if (currentLot == null) {
            throw new IllegalStateException("No inspection lot is loaded.");
        }
        if (saveInProgress.get()) {
            throw new IllegalStateException("Please wait for the current save operation to finish.");
        }
    }

    private String buildLotSignature(InspectionLot lot) {
        if (lot == null) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        builder.append(valueOrEmpty(lot.getName())).append('|')
                .append(valueOrEmpty(lot.getPlanId())).append('|')
                .append(valueOrEmpty(lot.getPlanFamilyId())).append('|')
                .append(valueOrEmpty(lot.getPlanName())).append('|')
                .append(lot.getPlanVersion()).append('|')
                .append(lot.getLotSize()).append('\n');

        for (PartBubbleDefinition bubble : lot.getBubbles()) {
            builder.append("B|")
                    .append(valueOrEmpty(bubble.getId())).append('|')
                    .append(valueOrEmpty(bubble.getName())).append('|')
                    .append(bubble.getSequenceNumber()).append('|')
                    .append(bubble.getInspectionType()).append('|')
                    .append(bubble.getExpectedPassFail()).append('|')
                    .append(valueOrEmpty(bubble.getNominalValue())).append('|')
                    .append(valueOrEmpty(bubble.getLowerTolerance())).append('|')
                    .append(valueOrEmpty(bubble.getUpperTolerance())).append('|')
                    .append(valueOrEmpty(bubble.getNote())).append('\n');
        }

        for (PartRecord part : lot.getParts()) {
            builder.append("P|")
                    .append(valueOrEmpty(part.getId())).append('|')
                    .append(part.getPartNumber()).append('\n');
            for (PartBubbleDefinition bubble : lot.getBubbles()) {
                String bubbleId = bubble.getId();
                builder.append("V|")
                        .append(valueOrEmpty(bubbleId)).append('|')
                        .append(valueOrEmpty(part.getMeasurement(bubbleId))).append('|')
                        .append(valueOrEmpty(part.getComment(bubbleId))).append('\n');
            }
        }
        return builder.toString();
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private record EditorState(InspectionLot lot, int currentPartNumber, String signature) {
    }
}
