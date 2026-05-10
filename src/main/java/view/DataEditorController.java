package view;

import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.util.Callback;
import javafx.util.converter.DefaultStringConverter;
import javafx.util.converter.IntegerStringConverter;
import javafx.util.StringConverter;
import model.Bubble;
import model.InspectionType;
import viewmodel.DataEditorViewModel;
import viewmodel.PlanEditorViewModel;

import java.net.URL;
import java.util.ResourceBundle;

public class DataEditorController implements Initializable {
    private final DataEditorViewModel dataEditorViewModel;
    private boolean syncingSelection;

    @FXML
    private Parent root;

    @FXML
    private Label currentPlanLabel;
    @FXML
    private Label modeLabel;

    @FXML
    private TableView<Bubble> tableView;

    @FXML
    private TableColumn<Bubble, Integer> columnSequenceNumber;

    @FXML
    private TableColumn<Bubble, String> columnCharacteristic;

    @FXML
    private TableColumn<Bubble, InspectionType> columnInspectionType;

    @FXML
    private TableColumn<Bubble, Double> columnNominalValue;

    @FXML
    private TableColumn<Bubble, Double> columnLowerTolerance;

    @FXML
    private TableColumn<Bubble, Double> columnUpperTolerance;

    @FXML
    private TableColumn<Bubble, String> columnNote;


    public DataEditorController(PlanEditorViewModel planEditorViewModel) { //requires planEditorViewmodel
        this.dataEditorViewModel = new DataEditorViewModel(planEditorViewModel);
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        currentPlanLabel.textProperty().bind(dataEditorViewModel.getPlanEditorViewModel().planNameProperty());
        modeLabel.textProperty().bind(Bindings.when(dataEditorViewModel.getPlanEditorViewModel().currentPlanEditableProperty())
                .then("Editable draft")
                .otherwise("Read-only complete plan"));

        columnSequenceNumber.setCellFactory(TextFieldTableCell.forTableColumn(new IntegerStringConverter()));
        columnSequenceNumber.setOnEditCommit(event -> {
            saveRow(event.getRowValue(), event.getNewValue(), null, null, null, null, null, null);
        });
        columnSequenceNumber.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getSequenceNumber()));

        columnCharacteristic.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getCharacteristic()));
        columnCharacteristic.setCellFactory(editableTextCellFactory());
        columnCharacteristic.setOnEditCommit(event -> {
            saveRow(event.getRowValue(), null, event.getNewValue(), null, null, null, null, null);
        });

        columnInspectionType.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getInspectionType()));
        columnInspectionType.setCellFactory(ComboBoxTableCell.forTableColumn(inspectionTypeConverter(),
                InspectionType.NUMERIC,
                InspectionType.PASS_FAIL));
        columnInspectionType.setOnEditCommit(event ->
                saveRow(event.getRowValue(), null, null, event.getNewValue(), null, null, null, null));

        StringConverter<Double> nullableDoubleConverter = new NullableDoubleStringConverter();
        columnNominalValue.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(numericValueForDisplay(data.getValue(), data.getValue().getNominalValue())));
        columnNominalValue.setCellFactory(editableNumericCellFactory(nullableDoubleConverter));
        columnNominalValue.setOnEditCommit(event -> {
            saveRow(event.getRowValue(), null, null, null, nullableDoubleConverter.toString(event.getNewValue()), null, null, null);
        });

        columnLowerTolerance.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(numericValueForDisplay(data.getValue(), data.getValue().getLowerTolerance())));
        columnLowerTolerance.setCellFactory(editableNumericCellFactory(nullableDoubleConverter));
        columnLowerTolerance.setOnEditCommit(event -> {
            saveRow(event.getRowValue(), null, null, null, null, nullableDoubleConverter.toString(event.getNewValue()), null, null);
        });

        columnUpperTolerance.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(numericValueForDisplay(data.getValue(), data.getValue().getUpperTolerance())));
        columnUpperTolerance.setCellFactory(editableNumericCellFactory(nullableDoubleConverter));
        columnUpperTolerance.setOnEditCommit(event -> {
            saveRow(event.getRowValue(), null, null, null, null, null, nullableDoubleConverter.toString(event.getNewValue()), null);
        });

        columnNote.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getNote()));
        columnNote.setCellFactory(editableTextCellFactory());
        columnNote.setOnEditCommit(event -> {
            saveRow(event.getRowValue(), null, null, null, null, null, null, event.getNewValue());
        });

        tableView.setItems(dataEditorViewModel.getBubbles());
        tableView.editableProperty().bind(dataEditorViewModel.getPlanEditorViewModel().currentPlanEditableProperty());
        tableView.setPlaceholder(new Label("No bubbles on this page."));
        columnSequenceNumber.setSortType(TableColumn.SortType.ASCENDING);
        tableView.getSortOrder().setAll(columnSequenceNumber);
        tableView.sort();

        tableView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (syncingSelection) {
                return;
            }
            dataEditorViewModel.selectBubble(newValue);
        });
        dataEditorViewModel.getPlanEditorViewModel().selectedBubbleProperty().addListener((observable, oldValue, newValue) ->
                syncSelection(newValue));
    }

    public DataEditorViewModel getDataEditorViewModel(){
        return this.dataEditorViewModel;
    }

    private void saveRow(
            Bubble bubble,
            Integer sequenceNumber,
            String characteristic,
            InspectionType inspectionType,
            String nominalValue,
            String lowerTolerance,
            String upperTolerance,
            String note
    ) {
        if (bubble == null) {
            return;
        }

        dataEditorViewModel.updateBubblePrintFields(
                bubble,
                sequenceNumber == null ? bubble.getSequenceNumber() : sequenceNumber,
                characteristic == null ? bubble.getCharacteristic() : characteristic,
                inspectionType == null ? bubble.getInspectionType() : inspectionType,
                nominalValue == null ? formatNullableNumber(bubble.getNominalValue()) : nominalValue,
                lowerTolerance == null ? formatNullableNumber(bubble.getLowerTolerance()) : lowerTolerance,
                upperTolerance == null ? formatNullableNumber(bubble.getUpperTolerance()) : upperTolerance,
                note == null ? bubble.getNote() : note
        );
        tableView.sort();
        syncSelection(bubble);
        tableView.refresh();
    }

    private Callback<TableColumn<Bubble, String>, TableCell<Bubble, String>> editableTextCellFactory() {
        return column -> {
            TextFieldTableCell<Bubble, String> cell = new TextFieldTableCell<>(new DefaultStringConverter());
            cell.itemProperty().addListener((observable, oldValue, newValue) -> updateTooltip(cell, newValue));
            return cell;
        };
    }

    private Callback<TableColumn<Bubble, Double>, TableCell<Bubble, Double>> editableNumericCellFactory(StringConverter<Double> converter) {
        return column -> {
            TextFieldTableCell<Bubble, Double> cell = new TextFieldTableCell<>(converter) {
                @Override
                public void startEdit() {
                    Bubble bubble = getTableRow() == null ? null : getTableRow().getItem();
                    if (bubble == null || bubble.getInspectionType() == InspectionType.PASS_FAIL) {
                        return;
                    }
                    super.startEdit();
                }

                @Override
                public void updateItem(Double item, boolean empty) {
                    super.updateItem(item, empty);
                    Bubble bubble = getTableRow() == null ? null : getTableRow().getItem();
                    boolean numericApplicable = !empty && bubble != null && bubble.getInspectionType() != InspectionType.PASS_FAIL;
                    setOpacity(empty || numericApplicable ? 1.0 : 0.55);
                }
            };
            cell.itemProperty().addListener((observable, oldValue, newValue) -> updateTooltip(cell, converter.toString(newValue)));
            return cell;
        };
    }

    private void updateTooltip(TableCell<?, ?> cell, String value) {
        if (value == null || value.isBlank()) {
            cell.setTooltip(null);
            return;
        }
        cell.setTooltip(new Tooltip(value));
    }

    private void syncSelection(Bubble bubble) {
        syncingSelection = true;
        try {
            if (bubble == null) {
                tableView.getSelectionModel().clearSelection();
                return;
            }

            tableView.getSelectionModel().select(bubble);
            tableView.scrollTo(bubble);
        } finally {
            syncingSelection = false;
        }
    }

    private Double numericValueForDisplay(Bubble bubble, Double value) {
        if (bubble == null || bubble.getInspectionType() == InspectionType.PASS_FAIL) {
            return null;
        }
        return value;
    }

    private String formatNullableNumber(Double value) {
        return value == null ? "" : value.toString();
    }

    private StringConverter<InspectionType> inspectionTypeConverter() {
        return new StringConverter<>() {
            @Override
            public String toString(InspectionType inspectionType) {
                if (inspectionType == null) {
                    return "";
                }
                return inspectionType == InspectionType.PASS_FAIL ? "Pass/Fail" : "Numeric";
            }

            @Override
            public InspectionType fromString(String value) {
                if (value == null || value.isBlank()) {
                    return InspectionType.NUMERIC;
                }
                return "Pass/Fail".equalsIgnoreCase(value.trim())
                        ? InspectionType.PASS_FAIL
                        : InspectionType.NUMERIC;
            }
        };
    }

    private static final class NullableDoubleStringConverter extends StringConverter<Double> {
        @Override
        public String toString(Double value) {
            return value == null ? "" : value.toString();
        }

        @Override
        public Double fromString(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            return Double.valueOf(value.trim());
        }
    }
}
