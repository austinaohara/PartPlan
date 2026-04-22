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
import model.PartBubbleDefinition;
import model.PartRecord;
import service.InspectionLotDatabaseService;

import java.util.List;

public class PartEditorViewModel {
    private static final String NO_LOT_SELECTED = "No inspection lot selected";
    private static final String NO_PLAN_SELECTED = "No plan selected";

    private final InspectionLotDatabaseService lotDatabaseService = new InspectionLotDatabaseService();
    private final ObservableList<PartRecord> parts = FXCollections.observableArrayList();
    private final ObservableList<PartBubbleDefinition> bubbles = FXCollections.observableArrayList();
    private final ObservableList<PartBubbleRowViewModel> currentPartRows = FXCollections.observableArrayList();
    private final IntegerProperty lotSize = new SimpleIntegerProperty(1);
    private final IntegerProperty currentPartNumber = new SimpleIntegerProperty(1);
    private final StringProperty currentPartTitle = new SimpleStringProperty("Create or open an inspection lot to begin.");
    private final StringProperty lotSummary = new SimpleStringProperty(NO_LOT_SELECTED);
    private final StringProperty currentLotName = new SimpleStringProperty("");
    private final StringProperty currentPlanName = new SimpleStringProperty(NO_PLAN_SELECTED);
    private final BooleanProperty lotLoaded = new SimpleBooleanProperty(false);

    private InspectionLot currentLot;

    public PartEditorViewModel() {
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

    public void loadLot(String lotId) {
        if (lotId == null || lotId.isBlank()) {
            return;
        }

        currentLot = lotDatabaseService.loadLot(lotId);
        currentPartNumber.set(1);
        lotLoaded.set(true);
        refreshAll();
    }

    public void saveCurrentLotName(String proposedName) {
        if (currentLot == null) {
            return;
        }

        String normalizedName = normalizeLotName(proposedName, currentLot.getName());
        currentLot.setName(normalizedName);
        lotDatabaseService.saveLotName(currentLot.getId(), normalizedName);
        refreshAll();
    }

    public void setLotSize(int value) {
        if (currentLot == null) {
            lotSize.set(Math.max(1, value));
            return;
        }

        currentLot.setLotSize(value);
        if (currentPartNumber.get() > currentLot.getLotSize()) {
            currentPartNumber.set(currentLot.getLotSize());
        }
        lotDatabaseService.saveLotStructure(currentLot);
        refreshAll();
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

    public void updatePartMeasurement(PartRecord part, String bubbleId, String value) {
        if (currentLot == null || part == null) {
            return;
        }

        PartRecord currentPart = currentLot.getPart(currentPartNumber.get() - 1);
        boolean refreshSelectedPart = currentPart != null && currentPart.getId().equals(part.getId());

        part.setMeasurement(bubbleId, value);
        lotDatabaseService.saveMeasurement(currentLot.getId(), part.getId(), bubbleId, value);

        if (refreshSelectedPart) {
            refreshCurrentPartRows();
        }
    }

    public String getCurrentLotId() {
        return currentLot == null ? "" : currentLot.getId();
    }

    private void refreshAll() {
        lotLoaded.set(currentLot != null);
        lotSize.set(currentLot == null ? Math.max(1, lotSize.get()) : currentLot.getLotSize());
        parts.setAll(currentLot == null ? List.of() : currentLot.getParts());
        bubbles.setAll(currentLot == null ? List.of() : currentLot.getBubbles());
        currentLotName.set(currentLot == null ? "" : currentLot.getName());
        currentPlanName.set(currentLot == null ? NO_PLAN_SELECTED : currentLot.getPlanName());
        refreshCurrentPartRows();
        refreshText();
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
                        bubble.getNominalValue(),
                        bubble.getLowerTolerance(),
                        bubble.getUpperTolerance(),
                        bubble.getNote(),
                        currentPart.getMeasurement(bubble.getId())
                ))
                .toList());
    }

    private void refreshText() {
        if (currentLot == null) {
            currentPartTitle.set("Create or open an inspection lot to begin.");
            lotSummary.set(NO_LOT_SELECTED);
            return;
        }

        currentPartTitle.set("Measurements for Part " + currentPartNumber.get() + " of " + currentLot.getLotSize());
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
}
