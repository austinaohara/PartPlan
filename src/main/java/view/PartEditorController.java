package view;

import app.AppContext;
import app.AppMenuSupport;
import app.BackgroundTaskRunner;
import app.UnsavedChangesDialogs;
import app.UserFacingErrorMessages;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TablePosition;
import javafx.scene.control.TableView;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.Tooltip;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import javafx.util.StringConverter;
import model.InspectionLot;
import model.InspectionPlan;
import model.PartBubbleDefinition;
import model.PartRecord;
import service.auth.AuthService;
import viewmodel.PartBubbleRowViewModel;
import viewmodel.PartEditorViewModel;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class PartEditorController {
    private static final int MAX_LOT_SIZE = 1000;

    private final PartEditorViewModel viewModel;
    private final AuthService authService;
    private final BooleanProperty repositoryBusy = new SimpleBooleanProperty(false);
    private final Map<MasterCommentCellKey, MasterMeasurementTableCell> masterMeasurementCells = new HashMap<>();
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
        this.authService = appContext.getAuthService();
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
        AppMenuSupport.install(root, AppMenuSupport.MenuContext.LOT_EDITOR, new AppMenuSupport.MenuCallbacks(
                this::signOutFromMenu,
                this::openFirebaseSettingsFromMenu,
                () -> AppMenuSupport.openOpenAiSettingsWindow(root)
        ));
        bindMenuActions();
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
        configureTableCellSelection(partTableView);

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
            selectBubbleCell(row.getBubbleId(), partMeasurementColumn);
        });

        partCommentColumn.setCellValueFactory(data -> data.getValue().commentValueProperty());
        partCommentColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        partCommentColumn.setOnEditCommit(event -> {
            PartBubbleRowViewModel row = event.getRowValue();
            String updatedComment = event.getNewValue() == null ? "" : event.getNewValue();
            row.setCommentValue(updatedComment);
            viewModel.updateCurrentPartComment(row.getBubbleId(), updatedComment);
            masterTableView.refresh();
            selectBubbleCell(row.getBubbleId(), partCommentColumn);
        });
    }

    private void configureMasterTable() {
        masterTableView.setItems(viewModel.getParts());
        masterTableView.setEditable(true);
        masterTableView.setPlaceholder(new Label("Create or open an inspection lot to enter or review saved measurements."));
        masterTableView.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        configureTableCellSelection(masterTableView);
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
            bubbleColumn.setUserData(bubble.getId());
            bubbleColumn.setGraphic(buildBubbleHeader(bubble));
            bubbleColumn.setText("");
            bubbleColumn.setCellFactory(column -> new MasterMeasurementTableCell(bubble.getId()));
            bubbleColumn.setOnEditCommit(event -> {
                viewModel.updatePartMeasurement(event.getRowValue(), bubble.getId(), event.getNewValue());
                partTableView.refresh();
                masterTableView.refresh();
                selectTableCell(masterTableView, event.getTablePosition().getRow(), event.getTableColumn());
            });
            bubbleColumn.setMinWidth(120.0);
            bubbleColumn.setPrefWidth(140.0);
            masterTableView.getColumns().add(bubbleColumn);
        }

        if (!viewModel.getParts().isEmpty() && masterTableView.getColumns().size() > 1
                && masterTableView.getSelectionModel().getSelectedCells().isEmpty()) {
            selectTableCell(masterTableView, 0, masterTableView.getColumns().get(1));
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
        } else if (partTableView.getSelectionModel().getSelectedCells().isEmpty()) {
            selectTableCell(partTableView, 0, partMeasurementColumn);
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

    private void selectBubbleCell(String bubbleId, TableColumn<PartBubbleRowViewModel, ?> column) {
        if (bubbleId == null || bubbleId.isBlank()) {
            partTableView.getSelectionModel().clearSelection();
            return;
        }

        for (int index = 0; index < viewModel.getCurrentPartRows().size(); index++) {
            PartBubbleRowViewModel row = viewModel.getCurrentPartRows().get(index);
            if (bubbleId.equals(row.getBubbleId())) {
                selectTableCell(partTableView, index, column);
                return;
            }
        }

        partTableView.getSelectionModel().clearSelection();
    }

    private void openMasterCommentEditorForSelection() {
        TablePosition<PartRecord, ?> focusedCell = masterTableView.getFocusModel().getFocusedCell();
        if (focusedCell == null || focusedCell.getRow() < 0) {
            return;
        }

        TableColumn<PartRecord, ?> column = focusedCell.getTableColumn();
        String bubbleId = bubbleIdForColumn(column);
        if (bubbleId == null || bubbleId.isBlank()) {
            return;
        }

        PartRecord part = focusedCell.getRow() >= masterTableView.getItems().size()
                ? null
                : masterTableView.getItems().get(focusedCell.getRow());
        if (part == null) {
            return;
        }

        MasterMeasurementTableCell cell = masterMeasurementCells.get(new MasterCommentCellKey(part.getId(), bubbleId));
        showMasterCommentEditor(cell, part, bubbleId);
    }

    private void showMasterCommentEditor(MasterMeasurementTableCell anchorCell, PartRecord part, String bubbleId) {
        if (part == null || bubbleId == null || bubbleId.isBlank()) {
            return;
        }

        TextArea commentArea = new TextArea(part.getComment(bubbleId));
        commentArea.setPromptText("Add inspection comment");
        commentArea.setWrapText(true);
        commentArea.setPrefColumnCount(28);
        commentArea.setPrefRowCount(4);

        Button saveButton = new Button("Save");
        saveButton.getStyleClass().add("primary-button");
        Button clearButton = new Button("Clear");
        Button cancelButton = new Button("Cancel");
        clearButton.getStyleClass().add("copy-button");

        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
        HBox buttonRow = new HBox(8.0, saveButton, clearButton, spacer, cancelButton);
        buttonRow.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(10.0, new Label("Inspection Comment"), commentArea, buttonRow);
        content.setPadding(new Insets(12.0));
        content.setPrefWidth(320.0);

        CustomMenuItem customMenuItem = new CustomMenuItem(content, false);
        ContextMenu editorMenu = new ContextMenu(customMenuItem);

        saveButton.setOnAction(event -> {
            String updatedComment = commentArea.getText() == null ? "" : commentArea.getText().trim();
            viewModel.updatePartComment(part, bubbleId, updatedComment);
            partTableView.refresh();
            masterTableView.refresh();
            editorMenu.hide();
            reselectMasterCommentCell(part, bubbleId);
        });

        clearButton.setOnAction(event -> {
            viewModel.updatePartComment(part, bubbleId, "");
            partTableView.refresh();
            masterTableView.refresh();
            editorMenu.hide();
            reselectMasterCommentCell(part, bubbleId);
        });

        cancelButton.setOnAction(event -> editorMenu.hide());

        Bounds anchorBounds = anchorCell == null ? null : anchorCell.localToScreen(anchorCell.getBoundsInLocal());
        if (anchorBounds != null) {
            editorMenu.show(anchorCell, anchorBounds.getMinX(), anchorBounds.getMaxY());
        } else {
            Bounds tableBounds = masterTableView.localToScreen(masterTableView.getBoundsInLocal());
            if (tableBounds == null) {
                return;
            }
            editorMenu.show(masterTableView, tableBounds.getMinX() + 24.0, tableBounds.getMinY() + 24.0);
        }

        Platform.runLater(commentArea::requestFocus);
    }

    private void showMasterCommentContextMenu(MasterMeasurementTableCell cell, PartRecord part, String bubbleId, double screenX, double screenY) {
        if (cell == null || part == null || bubbleId == null || bubbleId.isBlank()) {
            return;
        }

        ContextMenu menu = new ContextMenu();

        MenuItem editCommentItem = new MenuItem("Edit Comment");
        editCommentItem.setOnAction(event -> showMasterCommentEditor(cell, part, bubbleId));
        menu.getItems().add(editCommentItem);

        if (!part.getComment(bubbleId).isBlank()) {
            MenuItem clearCommentItem = new MenuItem("Clear Comment");
            clearCommentItem.setOnAction(event -> {
                viewModel.updatePartComment(part, bubbleId, "");
                partTableView.refresh();
                masterTableView.refresh();
                reselectMasterCommentCell(part, bubbleId);
            });
            menu.getItems().add(clearCommentItem);
        }

        menu.show(cell, screenX, screenY);
    }

    private void reselectMasterCommentCell(PartRecord part, String bubbleId) {
        if (part == null || bubbleId == null || bubbleId.isBlank()) {
            return;
        }

        int rowIndex = masterTableView.getItems().indexOf(part);
        if (rowIndex < 0) {
            return;
        }

        for (TableColumn<PartRecord, ?> column : masterTableView.getColumns()) {
            if (bubbleId.equals(bubbleIdForColumn(column))) {
                selectTableCell(masterTableView, rowIndex, column);
                return;
            }
        }
    }

    private String bubbleIdForColumn(TableColumn<PartRecord, ?> column) {
        if (column == null) {
            return null;
        }
        Object userData = column.getUserData();
        return userData instanceof String text && !text.isBlank() ? text : null;
    }

    private <S> void configureTableCellSelection(TableView<S> tableView) {
        tableView.getSelectionModel().setCellSelectionEnabled(true);
        tableView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        tableView.addEventFilter(KeyEvent.KEY_PRESSED, event -> handleTableKeyPressed(tableView, event));
    }

    private <S> void handleTableKeyPressed(TableView<S> tableView, KeyEvent event) {
        if (event.getTarget() instanceof TextInputControl) {
            return;
        }

        if (tableView == masterTableView
                && event.isControlDown()
                && event.isShiftDown()
                && event.getCode() == KeyCode.C) {
            openMasterCommentEditorForSelection();
            event.consume();
            return;
        }

        if (isInlineEditCharacter(event)) {
            startTypingInFocusedCell(tableView, event.getText());
            event.consume();
            return;
        }

        if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.F2) {
            editFocusedCell(tableView);
            event.consume();
        }
    }

    private <S> void editFocusedCell(TableView<S> tableView) {
        TablePosition<S, ?> focusedCell = tableView.getFocusModel().getFocusedCell();
        if (focusedCell == null || focusedCell.getRow() < 0) {
            return;
        }

        TableColumn<S, ?> column = focusedCell.getTableColumn();
        if (column == null || !tableView.isEditable() || !column.isEditable()) {
            return;
        }

        selectTableCell(tableView, focusedCell.getRow(), column);
        tableView.edit(focusedCell.getRow(), column);
    }

    private <S> void startTypingInFocusedCell(TableView<S> tableView, String typedText) {
        TablePosition<S, ?> focusedCell = tableView.getFocusModel().getFocusedCell();
        if (focusedCell == null || focusedCell.getRow() < 0) {
            return;
        }

        TableColumn<S, ?> column = focusedCell.getTableColumn();
        if (column == null || !tableView.isEditable() || !column.isEditable()) {
            return;
        }

        String replacementText = typedText == null ? "" : typedText;
        selectTableCell(tableView, focusedCell.getRow(), column);
        tableView.edit(focusedCell.getRow(), column);

        Platform.runLater(() -> {
            if (tableView.getScene() == null) {
                return;
            }
            if (tableView.getScene().getFocusOwner() instanceof TextInputControl input) {
                input.setText(replacementText);
                input.positionCaret(input.getText().length());
            }
        });
    }

    private boolean isInlineEditCharacter(KeyEvent event) {
        if (event.getCode() == KeyCode.TAB
                || event.getCode() == KeyCode.ESCAPE
                || event.getCode() == KeyCode.ENTER
                || event.getCode() == KeyCode.F2) {
            return false;
        }
        if (event.isControlDown() || event.isAltDown() || event.isMetaDown()) {
            return false;
        }
        String text = event.getText();
        if (text == null || text.isEmpty()) {
            return false;
        }
        char character = text.charAt(0);
        return !Character.isISOControl(character);
    }

    private Tooltip buildCommentTooltip(String comment) {
        if (comment == null || comment.isBlank()) {
            return null;
        }

        Tooltip tooltip = new Tooltip(comment);
        tooltip.setWrapText(true);
        tooltip.setMaxWidth(320.0);
        return tooltip;
    }

    private final class MasterMeasurementTableCell extends TableCell<PartRecord, String> {
        private final String bubbleId;
        private final Label valueLabel = new Label();
        private final Label commentMarker = new Label("*");
        private final StackPane displayPane = new StackPane();
        private TextField textField;
        private MasterCommentCellKey registeredKey;

        private MasterMeasurementTableCell(String bubbleId) {
            this.bubbleId = bubbleId;

            valueLabel.setMaxWidth(Double.MAX_VALUE);
            commentMarker.getStyleClass().add("master-comment-marker");

            displayPane.getChildren().addAll(valueLabel, commentMarker);
            StackPane.setAlignment(valueLabel, Pos.CENTER_LEFT);
            StackPane.setAlignment(commentMarker, Pos.TOP_RIGHT);
            StackPane.setMargin(commentMarker, new Insets(2.0, 4.0, 0.0, 0.0));

            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            setEditable(true);

            setOnMousePressed(event -> {
                if (isEmpty()) {
                    return;
                }
                selectTableCell(masterTableView, getIndex(), getTableColumn());
                if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2 && getTableColumn().isEditable()) {
                    startEdit();
                    event.consume();
                }
            });

            setOnContextMenuRequested(event -> {
                if (isEmpty()) {
                    return;
                }
                PartRecord part = getTableRow() == null ? null : getTableRow().getItem();
                if (part == null) {
                    return;
                }
                selectTableCell(masterTableView, getIndex(), getTableColumn());
                showMasterCommentContextMenu(this, part, bubbleId, event.getScreenX(), event.getScreenY());
                event.consume();
            });
        }

        @Override
        public void startEdit() {
            if (isEmpty() || !getTableView().isEditable() || !getTableColumn().isEditable()) {
                return;
            }

            super.startEdit();
            if (textField == null) {
                createTextField();
            }
            textField.setText(getItem() == null ? "" : getItem());
            setGraphic(textField);
            setText(null);
            Platform.runLater(() -> {
                textField.requestFocus();
                textField.selectAll();
            });
        }

        @Override
        public void cancelEdit() {
            super.cancelEdit();
            updateDisplay();
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            registerCurrentCell();

            if (empty) {
                unregisterCell();
                setText(null);
                setGraphic(null);
                setTooltip(null);
                return;
            }

            if (isEditing()) {
                if (textField != null) {
                    textField.setText(item == null ? "" : item);
                }
                setGraphic(textField);
                setText(null);
                return;
            }

            updateDisplay();
        }

        private void updateDisplay() {
            PartRecord part = getTableRow() == null ? null : getTableRow().getItem();
            String comment = part == null ? "" : part.getComment(bubbleId);

            valueLabel.setText(getItem() == null ? "" : getItem());
            valueLabel.setPadding(new Insets(0.0, 12.0, 0.0, 0.0));
            commentMarker.setVisible(comment != null && !comment.isBlank());
            commentMarker.setManaged(commentMarker.isVisible());
            setTooltip(buildCommentTooltip(comment));
            setGraphic(displayPane);
            setText(null);
        }

        private void createTextField() {
            textField = new TextField(getItem() == null ? "" : getItem());
            textField.setOnAction(event -> commitEdit(textField.getText()));
            textField.focusedProperty().addListener((observable, oldValue, focused) -> {
                if (!focused && isEditing()) {
                    commitEdit(textField.getText());
                }
            });
            textField.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                if (event.getCode() == KeyCode.ESCAPE) {
                    cancelEdit();
                    event.consume();
                }
            });
        }

        private void registerCurrentCell() {
            unregisterCell();
            PartRecord part = getTableRow() == null ? null : getTableRow().getItem();
            if (part == null || bubbleId == null || bubbleId.isBlank()) {
                return;
            }
            registeredKey = new MasterCommentCellKey(part.getId(), bubbleId);
            masterMeasurementCells.put(registeredKey, this);
        }

        private void unregisterCell() {
            if (registeredKey != null) {
                masterMeasurementCells.remove(registeredKey, this);
                registeredKey = null;
            }
        }
    }

    private record MasterCommentCellKey(String partId, String bubbleId) {
    }

    private <S> void selectTableCell(TableView<S> tableView, int rowIndex, TableColumn<S, ?> column) {
        if (tableView == null || column == null || rowIndex < 0 || rowIndex >= tableView.getItems().size()) {
            return;
        }

        tableView.requestFocus();
        tableView.getSelectionModel().clearAndSelect(rowIndex, column);
        tableView.getFocusModel().focus(rowIndex, column);
        tableView.scrollTo(rowIndex);
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

    private void bindMenuActions() {
        AppMenuSupport.bindAction(root, AppMenuSupport.MenuAction.FILE_OPEN_INSPECTION_LOTS, this::returnToLotBrowserFromMenu);
        AppMenuSupport.bindAction(root, AppMenuSupport.MenuAction.LOT_SAVE_LOT, this::onSaveLot);
        AppMenuSupport.bindAction(root, AppMenuSupport.MenuAction.LOT_UPVERSION_LOT, this::onUpversionLot);
        AppMenuSupport.bindAction(root, AppMenuSupport.MenuAction.LOT_PREVIOUS_PART, this::onPreviousPart);
        AppMenuSupport.bindAction(root, AppMenuSupport.MenuAction.LOT_NEXT_PART, this::onNextPart);
    }

    private void returnToLotBrowserFromMenu() {
        requestProceedWithPotentialUnsavedChanges("return to inspection lots", true, () -> {
            try {
                AppNavigator.swapRoot(root, "/fxml/inspection-lot-browser.fxml", "PartPlan - Inspection Lots", loader -> {
                    InspectionLotBrowserController controller = loader.getController();
                    controller.selectLot(viewModel.getCurrentLotId());
                });
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to return to inspection lots.", exception);
            }
        });
    }

    private void signOutFromMenu() {
        requestProceedWithPotentialUnsavedChanges("sign out", true, () -> {
            try {
                authService.signOut();
                AppNavigator.swapRoot(root, "/fxml/login.fxml", "PartPlan - Sign In");
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to sign out.", exception);
            }
        });
    }

    private void openFirebaseSettingsFromMenu() {
        requestProceedWithPotentialUnsavedChanges("open Firebase settings", true, () -> {
            try {
                AppNavigator.swapRoot(root, "/fxml/firebase-config.fxml", "PartPlan - Firebase Setup");
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to open the Firebase settings screen.", exception);
            }
        });
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
