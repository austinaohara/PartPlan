package view;

import app.AppContext;
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
import javafx.util.StringConverter;
import model.PartBubbleDefinition;
import model.PartRecord;
import viewmodel.PartBubbleRowViewModel;
import viewmodel.PartEditorViewModel;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class PartEditorController {
    private static final int MAX_LOT_SIZE = 1000;

    private final PartEditorViewModel viewModel;
    private final BooleanProperty repositoryBusy = new SimpleBooleanProperty(false);
    private final Map<MasterCommentCellKey, MasterMeasurementTableCell> masterMeasurementCells = new HashMap<>();
    private boolean syncingLotSize;

    @FXML
    private TextField lotNameField;
    @FXML
    private Spinner<Integer> lotSizeSpinner;
    @FXML
    private Label lotSummaryLabel;
    @FXML
    private Label loadedPlanLabel;
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
    private void initialize() {
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
        viewModel.loadLot(lotId);
        rebuildMasterColumns();
        syncLoadedLotState();
        syncPartSelection();
    }

    @FXML
    private void onReturnToLotBrowser(ActionEvent event) throws IOException {
        AppNavigator.swapRoot((Node) event.getSource(), "/fxml/inspection-lot-browser.fxml", "PartPlan - Inspection Lots", loader -> {
            InspectionLotBrowserController controller = loader.getController();
            controller.selectLot(viewModel.getCurrentLotId());
        });
    }

    @FXML
    private void onReturnToHub(ActionEvent event) throws IOException {
        AppNavigator.swapRoot((Node) event.getSource(), "/fxml/welcome.fxml", "PartPlan");
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
    private void onPreviousPart() {
        viewModel.selectPreviousPart();
        syncPartSelection();
    }

    @FXML
    private void onNextPart() {
        viewModel.selectNextPart();
        syncPartSelection();
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

        lotNameField.disableProperty().bind(viewModel.lotLoadedProperty().not());
        lotSizeSpinner.disableProperty().bind(viewModel.lotLoadedProperty().not());
        partSelectorComboBox.disableProperty().bind(viewModel.lotLoadedProperty().not());
        previousPartButton.disableProperty().bind(viewModel.lotLoadedProperty().not()
                .or(viewModel.currentPartNumberProperty().lessThanOrEqualTo(1)));
        nextPartButton.disableProperty().bind(viewModel.lotLoadedProperty().not()
                .or(viewModel.currentPartNumberProperty().greaterThanOrEqualTo(viewModel.lotSizeProperty())));
        editorTabPane.disableProperty().bind(viewModel.lotLoadedProperty().not());

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

                Measurements are preserved for matching bubble IDs. New bubbles will start blank, and removed bubbles will be dropped from the lot.
                """.formatted(
                lot.getName(),
                lot.getPlanName(),
                lot.getPlanVersion(),
                targetPlan.getName(),
                targetPlan.getVersion()
        );
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
