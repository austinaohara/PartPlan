package view;

import app.AppContext;
import app.BackgroundTaskRunner;
import app.UnsavedChangesDialogs;
import app.UserFacingErrorMessages;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.BorderPane;
import javafx.scene.text.TextAlignment;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import javafx.util.StringConverter;
import model.InspectionLot;
import model.InspectionPlan;
import model.PartBubbleDefinition;
import model.PartRecord;
import viewmodel.PartBubbleRowViewModel;
import viewmodel.PartEditorViewModel;

import java.io.IOException;
import java.util.function.Consumer;

public class PartEditorController {
    private static final int MAX_LOT_SIZE = 1000;

    private final PartEditorViewModel viewModel;
    private final BooleanProperty repositoryBusy = new SimpleBooleanProperty(false);
    private boolean syncingLotSize;
    private Window guardedWindow;
    private boolean allowWindowClose;
    private final javafx.event.EventHandler<WindowEvent> closeRequestHandler = this::handleCloseRequest;

    private void handleCloseRequest(WindowEvent event) {
        if (allowWindowClose) {
            allowWindowClose = false;
            return;
        }
        if (repositoryBusy.get()) {
            event.consume();
            return;
        }
        if (!viewModel.unsavedChangesProperty().get()) {
            return;
        }
        event.consume();
        requestProceedWithPotentialUnsavedChanges("close the lot editor", false, this::closeWindowAfterSaveOrDiscard);
    }

    public PartEditorController(AppContext appContext) {
        this.viewModel = new PartEditorViewModel(
                appContext.getLotRepository(),
                appContext.getPlanRepository()
        );
    }

    @FXML
    private BorderPane root;
    @FXML
    private TextField lotNameField;
    @FXML
    private Spinner<Integer> lotSizeSpinner;
    @FXML
    private Label lotSummaryLabel;
    @FXML
    private Label loadedPlanLabel;
    @FXML
    private Label nextPlanVersionLabel;
    @FXML
    private Label lotUnsavedLabel;
    @FXML
    private ComboBox<PartRecord> partSelectorComboBox;
    @FXML
    private Label currentPartTitleLabel;
    @FXML
    private TableView<PartBubbleRowViewModel> partTableView;
    @FXML
    private TableColumn<PartBubbleRowViewModel, Integer> partSequenceColumn;
    @FXML
    private TableColumn<PartBubbleRowViewModel, String> partRequirementColumn;
    @FXML
    private TableColumn<PartBubbleRowViewModel, String> partMeasurementColumn;
    @FXML
    private TableColumn<PartBubbleRowViewModel, String> partCommentColumn;
    @FXML
    private TableView<PartRecord> masterTableView;
    @FXML
    private TabPane editorTabPane;
    @FXML
    private Button previousPartButton;
    @FXML
    private Button nextPartButton;
    @FXML
    private Button upversionLotButton;
    @FXML
    private Button saveLotButton;

    @FXML
    private void initialize() {
        root.disableProperty().bind(repositoryBusy);
        root.sceneProperty().addListener((observable, oldScene, newScene) -> registerWindowCloseGuard(newScene));
        configureLotNameField();
        configureLotSizeSpinner();
        configurePartSelector();
        configurePartTable();
        configureMasterTable();
        bindViewModel();
        rebuildMasterColumns();
        syncLoadedLotState();
        syncPartSelection();
    }

    public void loadLot(String lotId) {
        repositoryBusy.set(true);
        BackgroundTaskRunner.run("lot-load", () -> viewModel.loadLotData(lotId), loadedLotData -> {
            repositoryBusy.set(false);
            viewModel.applyLoadedLot(loadedLotData);
            rebuildMasterColumns();
            syncLoadedLotState();
            syncPartSelection();
        }, failure -> {
            repositoryBusy.set(false);
            showFailure(failure, "Unable to load the inspection lot.");
        });
    }

    @FXML
    private void onReturnToLotBrowser(ActionEvent event) throws IOException {
        requestProceedWithPotentialUnsavedChanges("return to inspection lots", true, () -> {
            try {
                AppNavigator.swapRoot((Node) event.getSource(), "/fxml/inspection-lot-browser.fxml", "PartPlan - Inspection Lots", loader -> {
                    InspectionLotBrowserController controller = loader.getController();
                    controller.selectLot(viewModel.getCurrentLotId());
                });
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to return to inspection lots.", exception);
            }
        });
    }

    @FXML
    private void onReturnToHub(ActionEvent event) throws IOException {
        requestProceedWithPotentialUnsavedChanges("return to the hub", true, () -> {
            try {
                AppNavigator.swapRoot((Node) event.getSource(), "/fxml/welcome.fxml", "PartPlan");
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to return to the hub.", exception);
            }
        });
    }

    @FXML
    private void onLotNameCommitted() {
        if (!viewModel.lotLoadedProperty().get()) {
            return;
        }

        viewModel.saveCurrentLotName(lotNameField.getText());
        syncLoadedLotState();
    }

    @FXML
    private void onSaveLot() {
        saveCurrentLotAsync(null);
    }

    private void saveCurrentLotAsync(Runnable onSuccessContinuation) {
        if (!viewModel.lotLoadedProperty().get()) {
            return;
        }

        onLotNameCommitted();
        InspectionLot snapshot;
        try {
            snapshot = viewModel.beginSaveSnapshot();
        } catch (IllegalStateException exception) {
            showInformation(exception.getMessage());
            return;
        }

        saveLotButton.setText("Saving...");
        repositoryBusy.set(true);
        BackgroundTaskRunner.run("lot-save", () -> {
            viewModel.persistLotSnapshot(snapshot);
            return snapshot;
        }, savedLot -> {
            repositoryBusy.set(false);
            viewModel.finishSaveSuccess(savedLot);
            saveLotButton.setText("Save Lot");
            syncLoadedLotState();
            syncPartSelection();
            rebuildMasterColumns();
            if (onSuccessContinuation != null) {
                onSuccessContinuation.run();
            }
        }, failure -> {
            repositoryBusy.set(false);
            viewModel.finishSaveFailure(snapshot.getId());
            saveLotButton.setText("Save Lot");
            showFailure(failure, "Unable to save the inspection lot.");
        });
    }

    @FXML
    private void onPreviousPart() {
        viewModel.selectPreviousPart();
        syncPartSelection();
    }

    @FXML
    private void onNextPart() {
        viewModel.selectNextPart();
        syncPartSelection();
    }

    @FXML
    private void onUpversionLot() {
        InspectionLot currentLot = viewModel.getCurrentLot();
        InspectionPlan targetPlan = viewModel.getLatestUpversionTarget();
        if (currentLot == null || targetPlan == null) {
            return;
        }

        requestProceedWithPotentialUnsavedChanges("upversion this inspection lot", true, () -> {
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.CONFIRMATION);
            alert.setTitle("Upversion Inspection Lot");
            alert.setHeaderText("Move this inspection lot to a newer plan version?");
            alert.setContentText(buildUpversionMessage(currentLot, targetPlan));
            java.util.Optional<javafx.scene.control.ButtonType> result = alert.showAndWait();
            if (result.isEmpty() || result.get() != javafx.scene.control.ButtonType.OK) {
                return;
            }

            repositoryBusy.set(true);
            BackgroundTaskRunner.run("lot-upversion", viewModel::upversionCurrentLotInRepository, updatedLot -> {
                repositoryBusy.set(false);
                viewModel.applyUpversionedLot(updatedLot);
                rebuildMasterColumns();
                syncLoadedLotState();
                syncPartSelection();
                if (updatedLot != null) {
                    showInformation("Inspection lot moved to " + updatedLot.getPlanName() + " v" + updatedLot.getPlanVersion() + ".");
                }
            }, failure -> {
                repositoryBusy.set(false);
                showFailure(failure, "Unable to upversion the inspection lot.");
            });
        });
    }

    private void configureLotNameField() {
        lotNameField.setOnAction(event -> onLotNameCommitted());
        lotNameField.focusedProperty().addListener((observable, oldValue, focused) -> {
            if (!focused) {
                onLotNameCommitted();
            }
        });
    }

    private void configureLotSizeSpinner() {
        SpinnerValueFactory.IntegerSpinnerValueFactory valueFactory =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, MAX_LOT_SIZE, viewModel.getLotSize());
        lotSizeSpinner.setValueFactory(valueFactory);
        lotSizeSpinner.setEditable(true);
        lotSizeSpinner.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (syncingLotSize || newValue == null) {
                return;
            }
            if (newValue != null) {
                viewModel.setLotSize(newValue);
                syncPartSelection();
                rebuildMasterColumns();
                syncLoadedLotState();
            }
        });
        lotSizeSpinner.getEditor().setOnAction(event -> commitLotSizeEditor());
        lotSizeSpinner.focusedProperty().addListener((observable, oldValue, focused) -> {
            if (!focused) {
                commitLotSizeEditor();
            }
        });
    }

    private void configurePartSelector() {
        partSelectorComboBox.setItems(viewModel.getParts());
        partSelectorComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(PartRecord part) {
                return part == null ? "" : "Part " + part.getPartNumber();
            }

            @Override
            public PartRecord fromString(String string) {
                return null;
            }
        });
        partSelectorComboBox.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && newValue.getPartNumber() != viewModel.getCurrentPartNumber()) {
                viewModel.selectPart(newValue.getPartNumber());
            }
        });
    }

    private void configurePartTable() {
        partTableView.setItems(viewModel.getCurrentPartRows());
        partTableView.setEditable(true);
        partTableView.setPlaceholder(new Label("Create or open an inspection lot to begin entering part measurements."));
        partTableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        partSequenceColumn.setCellValueFactory(data -> data.getValue().sequenceNumberProperty().asObject());
        partRequirementColumn.setCellValueFactory(data -> data.getValue().requirementProperty());
        partRequirementColumn.setCellFactory(column -> new TableCell<>() {
            private final Label label = new Label();

            {
                label.getStyleClass().add("part-requirement-label");
                label.setWrapText(true);
                label.maxWidthProperty().bind(column.widthProperty().subtract(24.0));
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null || item.isBlank()) {
                    label.setText(null);
                    setGraphic(null);
                    setTooltip(null);
                    return;
                }

                PartBubbleRowViewModel row = getTableRow() == null ? null : getTableRow().getItem();
                label.setText(item);
                setGraphic(label);
                setTooltip(buildRequirementTooltip(row, item));
            }
        });

        partMeasurementColumn.setCellValueFactory(data -> data.getValue().measurementValueProperty());
        partMeasurementColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        partMeasurementColumn.setOnEditCommit(event -> {
            PartBubbleRowViewModel row = event.getRowValue();
            viewModel.updateCurrentPartMeasurement(row.getBubbleId(), event.getNewValue());
            masterTableView.refresh();
            selectBubbleRow(row.getBubbleId());
        });

        partCommentColumn.setCellValueFactory(data -> data.getValue().commentValueProperty());
        partCommentColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        partCommentColumn.setOnEditCommit(event -> {
            PartBubbleRowViewModel row = event.getRowValue();
            String updatedComment = event.getNewValue() == null ? "" : event.getNewValue();
            row.setCommentValue(updatedComment);
            viewModel.updateCurrentPartComment(row.getBubbleId(), updatedComment);
            masterTableView.refresh();
            selectBubbleRow(row.getBubbleId());
        });
    }

    private void configureMasterTable() {
        masterTableView.setItems(viewModel.getParts());
        masterTableView.setEditable(true);
        masterTableView.setPlaceholder(new Label("Create or open an inspection lot to enter or review saved measurements."));
        masterTableView.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
    }

    private void bindViewModel() {
        lotSummaryLabel.textProperty().bind(viewModel.lotSummaryProperty());
        loadedPlanLabel.textProperty().bind(viewModel.currentPlanNameProperty());
        currentPartTitleLabel.textProperty().bind(viewModel.currentPartTitleProperty());
        nextPlanVersionLabel.textProperty().bind(viewModel.upversionTargetLabelProperty());

        lotNameField.disableProperty().bind(viewModel.lotLoadedProperty().not());
        lotSizeSpinner.disableProperty().bind(viewModel.lotLoadedProperty().not());
        partSelectorComboBox.disableProperty().bind(viewModel.lotLoadedProperty().not());
        saveLotButton.disableProperty().bind(viewModel.lotLoadedProperty().not()
                .or(viewModel.unsavedChangesProperty().not())
                .or(viewModel.saveInProgressProperty()));
        upversionLotButton.disableProperty().bind(viewModel.lotLoadedProperty().not().or(viewModel.upversionAvailableProperty().not()));
        previousPartButton.disableProperty().bind(viewModel.lotLoadedProperty().not()
                .or(viewModel.currentPartNumberProperty().lessThanOrEqualTo(1)));
        nextPartButton.disableProperty().bind(viewModel.lotLoadedProperty().not()
                .or(viewModel.currentPartNumberProperty().greaterThanOrEqualTo(viewModel.lotSizeProperty())));
        editorTabPane.disableProperty().bind(viewModel.lotLoadedProperty().not());
        lotUnsavedLabel.textProperty().bind(viewModel.saveStateProperty());
        lotUnsavedLabel.visibleProperty().bind(viewModel.saveStateProperty().isNotEmpty());
        lotUnsavedLabel.managedProperty().bind(lotUnsavedLabel.visibleProperty());

        viewModel.currentPartNumberProperty().addListener((observable, oldValue, newValue) -> syncPartSelection());
    }

    private void rebuildMasterColumns() {
        masterTableView.getColumns().clear();

        if (!viewModel.lotLoadedProperty().get()) {
            return;
        }

        TableColumn<PartRecord, Number> partColumn = new TableColumn<>("Part");
        partColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getPartNumber()));
        partColumn.setEditable(false);
        partColumn.setMinWidth(70.0);
        partColumn.setPrefWidth(80.0);
        masterTableView.getColumns().add(partColumn);

        for (PartBubbleDefinition bubble : viewModel.getBubbles()) {
            TableColumn<PartRecord, String> bubbleColumn = new TableColumn<>(bubble.getName());
            bubbleColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getMeasurement(bubble.getId())));
            bubbleColumn.setEditable(true);
            bubbleColumn.setGraphic(buildBubbleHeader(bubble));
            bubbleColumn.setText("");
            bubbleColumn.setCellFactory(TextFieldTableCell.forTableColumn());
            bubbleColumn.setOnEditCommit(event -> {
                viewModel.updatePartMeasurement(event.getRowValue(), bubble.getId(), event.getNewValue());
                partTableView.refresh();
                masterTableView.refresh();
            });
            bubbleColumn.setMinWidth(120.0);
            bubbleColumn.setPrefWidth(140.0);
            masterTableView.getColumns().add(bubbleColumn);
        }
    }

    private void syncLoadedLotState() {
        if (viewModel.lotLoadedProperty().get()) {
            lotNameField.setText(viewModel.currentLotNameProperty().get());
        } else {
            lotNameField.clear();
        }
    }

    private void syncPartSelection() {
        PartRecord currentPart = viewModel.getParts().stream()
                .filter(part -> part.getPartNumber() == viewModel.getCurrentPartNumber())
                .findFirst()
                .orElse(null);
        if (currentPart != null) {
            partSelectorComboBox.getSelectionModel().select(currentPart);
        } else {
            partSelectorComboBox.getSelectionModel().clearSelection();
        }
        syncingLotSize = true;
        try {
            lotSizeSpinner.getValueFactory().setValue(viewModel.getLotSize());
        } finally {
            syncingLotSize = false;
        }

        if (viewModel.getCurrentPartRows().isEmpty()) {
            partTableView.getSelectionModel().clearSelection();
        } else if (partTableView.getSelectionModel().getSelectedItem() == null) {
            partTableView.getSelectionModel().selectFirst();
        }
    }

    private void commitLotSizeEditor() {
        SpinnerValueFactory.IntegerSpinnerValueFactory valueFactory =
                (SpinnerValueFactory.IntegerSpinnerValueFactory) lotSizeSpinner.getValueFactory();
        String text = lotSizeSpinner.getEditor().getText();

        try {
            int parsed = Integer.parseInt(text.trim());
            int bounded = Math.max(valueFactory.getMin(), Math.min(valueFactory.getMax(), parsed));
            valueFactory.setValue(bounded);
        } catch (NumberFormatException exception) {
            valueFactory.setValue(viewModel.getLotSize());
        }
    }

    private Tooltip buildRequirementTooltip(PartBubbleRowViewModel row, String visibleText) {
        if (row == null) {
            return null;
        }

        String note = row.getNote();
        if (note == null || note.isBlank() || note.equals(visibleText)) {
            return null;
        }

        return new Tooltip(note);
    }

    private Label buildBubbleHeader(PartBubbleDefinition bubble) {
        boolean noteOnly = isNoteOnlyBubble(bubble);
        Label label = new Label(noteOnly ? bubble.getNote() : buildHeaderText(bubble));
        label.getStyleClass().add("master-column-header");
        label.setWrapText(!noteOnly);
        label.setTextAlignment(noteOnly ? TextAlignment.LEFT : TextAlignment.CENTER);
        label.setMaxWidth(noteOnly ? 132.0 : 140.0);

        if (noteOnly) {
            label.setMinWidth(0.0);
            label.setPrefWidth(132.0);
            label.setTextOverrun(OverrunStyle.ELLIPSIS);
            label.setTooltip(new Tooltip(bubble.getNote()));
        } else if (!bubble.getNote().isBlank()) {
            label.setTooltip(new Tooltip("Note: " + bubble.getNote()));
        }

        return label;
    }

    private String buildHeaderText(PartBubbleDefinition bubble) {
        return "%s%nNom %s%n+%s / -%s".formatted(
                bubble.getName(),
                displaySpecValue(bubble.getNominalValue()),
                displaySpecValue(bubble.getUpperTolerance()),
                displaySpecValue(bubble.getLowerTolerance())
        );
    }

    private String displaySpecValue(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private boolean isNoteOnlyBubble(PartBubbleDefinition bubble) {
        return bubble != null
                && !bubble.getNote().isBlank()
                && bubble.getNominalValue().isBlank()
                && bubble.getLowerTolerance().isBlank()
                && bubble.getUpperTolerance().isBlank();
    }

    private String buildUpversionMessage(InspectionLot lot, InspectionPlan targetPlan) {
        return """
                Lot: %s
                Current plan: %s v%d
                New plan: %s v%d

                Measurements and comments are preserved for matching bubble IDs. New bubbles will start blank, and removed bubbles will be dropped from the lot.
                """.formatted(
                lot.getName(),
                lot.getPlanName(),
                lot.getPlanVersion(),
                targetPlan.getName(),
                targetPlan.getVersion()
        );
    }

    private void selectBubbleRow(String bubbleId) {
        if (bubbleId == null || bubbleId.isBlank()) {
            partTableView.getSelectionModel().clearSelection();
            return;
        }

        for (PartBubbleRowViewModel row : viewModel.getCurrentPartRows()) {
            if (bubbleId.equals(row.getBubbleId())) {
                partTableView.getSelectionModel().select(row);
                partTableView.scrollTo(row);
                return;
            }
        }

        partTableView.getSelectionModel().clearSelection();
    }

    private void showInformation(String message) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
        alert.setTitle("Part Editor");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showFailure(Throwable failure, String fallbackMessage) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
        alert.setTitle("Part Editor");
        alert.setHeaderText("Action failed");
        alert.setContentText(UserFacingErrorMessages.format(failure, fallbackMessage));
        alert.showAndWait();
    }

    private void requestProceedWithPotentialUnsavedChanges(String actionLabel, boolean showBusyMessage, Runnable continuation) {
        if (repositoryBusy.get()) {
            if (showBusyMessage) {
                showInformation("Please wait for the current database operation to finish.");
            }
            return;
        }
        if (!viewModel.unsavedChangesProperty().get()) {
            continuation.run();
            return;
        }

        switch (UnsavedChangesDialogs.promptToSaveDiscardOrCancel("inspection lot", actionLabel)) {
            case SAVE -> saveCurrentLotAsync(continuation);
            case DISCARD -> continuation.run();
            case CANCEL -> {
            }
        }
    }

    private void registerWindowCloseGuard(javafx.scene.Scene scene) {
        if (guardedWindow != null) {
            guardedWindow.removeEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, closeRequestHandler);
            guardedWindow = null;
        }
        if (scene == null) {
            return;
        }

        Consumer<Window> installer = window -> {
            if (window == null || window == guardedWindow) {
                return;
            }
            guardedWindow = window;
            guardedWindow.addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, closeRequestHandler);
        };

        installer.accept(scene.getWindow());
        scene.windowProperty().addListener((observable, oldWindow, newWindow) -> {
            if (oldWindow != null && oldWindow != guardedWindow) {
                oldWindow.removeEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, closeRequestHandler);
            }
            if (oldWindow != null && oldWindow == guardedWindow) {
                oldWindow.removeEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, closeRequestHandler);
                guardedWindow = null;
            }
            installer.accept(newWindow);
        });
    }

    private void closeWindowAfterSaveOrDiscard() {
        if (guardedWindow == null) {
            return;
        }
        allowWindowClose = true;
        if (guardedWindow instanceof javafx.stage.Stage stage) {
            stage.close();
            return;
        }
        guardedWindow.hide();
    }
}
