package view;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.text.TextAlignment;
import javafx.util.StringConverter;
import model.PartBubbleDefinition;
import model.PartRecord;
import viewmodel.PartBubbleRowViewModel;
import viewmodel.PartEditorViewModel;

import java.io.IOException;

public class PartEditorController {
    private static final int MAX_LOT_SIZE = 1000;

    private final PartEditorViewModel viewModel = new PartEditorViewModel();
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
    private TableColumn<PartBubbleRowViewModel, String> partBubbleNameColumn;
    @FXML
    private TableColumn<PartBubbleRowViewModel, String> partNominalColumn;
    @FXML
    private TableColumn<PartBubbleRowViewModel, String> partLowerToleranceColumn;
    @FXML
    private TableColumn<PartBubbleRowViewModel, String> partUpperToleranceColumn;
    @FXML
    private TableColumn<PartBubbleRowViewModel, String> partNoteColumn;
    @FXML
    private TableColumn<PartBubbleRowViewModel, String> partMeasurementColumn;
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

        partSequenceColumn.setCellValueFactory(data -> data.getValue().sequenceNumberProperty().asObject());
        partBubbleNameColumn.setCellValueFactory(data -> data.getValue().bubbleNameProperty());
        partNominalColumn.setCellValueFactory(data -> data.getValue().nominalValueProperty());
        partLowerToleranceColumn.setCellValueFactory(data -> data.getValue().lowerToleranceProperty());
        partUpperToleranceColumn.setCellValueFactory(data -> data.getValue().upperToleranceProperty());
        partNoteColumn.setCellValueFactory(data -> data.getValue().noteProperty());

        partMeasurementColumn.setCellValueFactory(data -> data.getValue().measurementValueProperty());
        partMeasurementColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        partMeasurementColumn.setOnEditCommit(event -> {
            PartBubbleRowViewModel row = event.getRowValue();
            viewModel.updateCurrentPartMeasurement(row.getBubbleId(), event.getNewValue());
            masterTableView.refresh();
        });
    }

    private void configureMasterTable() {
        masterTableView.setItems(viewModel.getParts());
        masterTableView.setEditable(false);
        masterTableView.setPlaceholder(new Label("Create or open an inspection lot to review the lot table."));
        masterTableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
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
        masterTableView.getColumns().add(partColumn);

        for (PartBubbleDefinition bubble : viewModel.getBubbles()) {
            TableColumn<PartRecord, String> bubbleColumn = new TableColumn<>(bubble.getName());
            bubbleColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getMeasurement(bubble.getId())));
            bubbleColumn.setEditable(false);
            bubbleColumn.setGraphic(buildBubbleHeader(bubble));
            bubbleColumn.setText("");
            bubbleColumn.setPrefWidth(150.0);
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

    private Label buildBubbleHeader(PartBubbleDefinition bubble) {
        Label label = new Label(buildHeaderText(bubble));
        label.getStyleClass().add("master-column-header");
        label.setWrapText(true);
        label.setTextAlignment(TextAlignment.CENTER);
        label.setMaxWidth(140.0);

        if (!bubble.getNote().isBlank()) {
            label.setTooltip(new Tooltip("Note: " + bubble.getNote()));
        }

        return label;
    }

    private String buildHeaderText(PartBubbleDefinition bubble) {
        return "%s%nNom %s%n-%s / +%s".formatted(
                bubble.getName(),
                displaySpecValue(bubble.getNominalValue()),
                displaySpecValue(bubble.getLowerTolerance()),
                displaySpecValue(bubble.getUpperTolerance())
        );
    }

    private String displaySpecValue(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
