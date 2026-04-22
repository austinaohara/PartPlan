package view;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;
import model.InspectionLot;
import model.InspectionLotSummary;
import model.InspectionPlan;
import viewmodel.InspectionLotBrowserViewModel;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class InspectionLotBrowserController {
    private static final int MAX_LOT_SIZE = 1000;
    private static final DateTimeFormatter UPDATED_AT_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a");

    private final InspectionLotBrowserViewModel viewModel = new InspectionLotBrowserViewModel();

    @FXML
    private TableView<InspectionLotSummary> savedLotsTableView;
    @FXML
    private TableColumn<InspectionLotSummary, String> lotNameColumn;
    @FXML
    private TableColumn<InspectionLotSummary, String> lotPlanColumn;
    @FXML
    private TableColumn<InspectionLotSummary, Number> lotSizeColumn;
    @FXML
    private TableColumn<InspectionLotSummary, String> lotUpdatedColumn;
    @FXML
    private ComboBox<InspectionPlan> planSelectorComboBox;
    @FXML
    private TextField lotNameField;
    @FXML
    private Spinner<Integer> lotSizeSpinner;
    @FXML
    private Button openLotButton;
    @FXML
    private Button createLotButton;
    @FXML
    private Label savedLotCountLabel;

    @FXML
    private void initialize() {
        configureSavedLotsTable();
        configurePlanSelector();
        configureLotSizeSpinner();
        bindViewModel();
        syncDefaults();
        updateSavedLotCount();
    }

    public void selectLot(String lotId) {
        if (lotId == null || lotId.isBlank()) {
            return;
        }

        viewModel.getSavedLots().stream()
                .filter(lot -> lot.getId().equals(lotId))
                .findFirst()
                .ifPresent(lot -> {
                    savedLotsTableView.getSelectionModel().select(lot);
                    savedLotsTableView.scrollTo(lot);
                });
    }

    @FXML
    private void onRefreshData() {
        String selectedLotId = getSelectedLotId();
        viewModel.refresh();
        syncDefaults();
        updateSavedLotCount();
        selectLot(selectedLotId);
    }

    @FXML
    private void onOpenLot() throws IOException {
        InspectionLotSummary selectedLot = savedLotsTableView.getSelectionModel().getSelectedItem();
        if (selectedLot == null) {
            return;
        }

        openPartEditor(savedLotsTableView, selectedLot.getId());
    }

    @FXML
    private void onCreateLot() throws IOException {
        commitLotSizeEditor();
        InspectionPlan selectedPlan = planSelectorComboBox.getSelectionModel().getSelectedItem();
        Integer requestedSize = lotSizeSpinner.getValue();
        InspectionLot createdLot = viewModel.createLot(
                selectedPlan,
                lotNameField.getText(),
                requestedSize == null ? 1 : requestedSize
        );

        if (createdLot == null) {
            return;
        }

        updateSavedLotCount();
        openPartEditor(createLotButton, createdLot.getId());
    }

    @FXML
    private void onReturnToHub() throws IOException {
        AppNavigator.swapRoot(savedLotsTableView, "/fxml/welcome.fxml", "PartPlan");
    }

    private void configureSavedLotsTable() {
        savedLotsTableView.setItems(viewModel.getSavedLots());
        savedLotsTableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        savedLotsTableView.setPlaceholder(new Label("No inspection lots have been created yet."));

        lotNameColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getName()));
        lotPlanColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getPlanName()));
        lotSizeColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getLotSize()));
        lotUpdatedColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(formatTimestamp(data.getValue().getUpdatedAt())));

        savedLotsTableView.setRowFactory(tableView -> {
            TableRow<InspectionLotSummary> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    try {
                        openPartEditor(row, row.getItem().getId());
                    } catch (IOException exception) {
                        throw new IllegalStateException("Unable to open inspection lot.", exception);
                    }
                }
            });
            return row;
        });
    }

    private void configurePlanSelector() {
        planSelectorComboBox.setItems(viewModel.getSavedPlans());
        planSelectorComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(InspectionPlan plan) {
                return plan == null ? "" : displayPlanName(plan);
            }

            @Override
            public InspectionPlan fromString(String string) {
                return null;
            }
        });
    }

    private void configureLotSizeSpinner() {
        lotSizeSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, MAX_LOT_SIZE, 5));
        lotSizeSpinner.setEditable(true);
        lotSizeSpinner.getEditor().setOnAction(event -> commitLotSizeEditor());
        lotSizeSpinner.focusedProperty().addListener((observable, oldValue, focused) -> {
            if (!focused) {
                commitLotSizeEditor();
            }
        });
    }

    private void bindViewModel() {
        openLotButton.disableProperty().bind(savedLotsTableView.getSelectionModel().selectedItemProperty().isNull());
        createLotButton.disableProperty().bind(planSelectorComboBox.getSelectionModel().selectedItemProperty().isNull());
        viewModel.getSavedLots().addListener((javafx.collections.ListChangeListener<InspectionLotSummary>) change -> updateSavedLotCount());
    }

    private void syncDefaults() {
        if (planSelectorComboBox.getSelectionModel().getSelectedItem() == null && !viewModel.getSavedPlans().isEmpty()) {
            planSelectorComboBox.getSelectionModel().selectFirst();
        }
    }

    private void updateSavedLotCount() {
        int lotCount = viewModel.getSavedLots().size();
        savedLotCountLabel.setText("%d saved %s".formatted(lotCount, lotCount == 1 ? "lot" : "lots"));
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
            valueFactory.setValue(5);
        }
    }

    private String getSelectedLotId() {
        InspectionLotSummary selectedLot = savedLotsTableView.getSelectionModel().getSelectedItem();
        return selectedLot == null ? "" : selectedLot.getId();
    }

    private void openPartEditor(Node source, String lotId) throws IOException {
        AppNavigator.swapRoot(source, "/fxml/part-editor.fxml", "PartPlan - Part Editor", loader -> {
            PartEditorController controller = loader.getController();
            controller.loadLot(lotId);
        });
    }

    private String formatTimestamp(LocalDateTime value) {
        return value == null ? "" : UPDATED_AT_FORMAT.format(value);
    }

    private String displayPlanName(InspectionPlan plan) {
        if (plan == null || plan.getName() == null || plan.getName().isBlank()) {
            return "Untitled Plan";
        }
        return plan.getName().trim();
    }
}
