package view;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TablePosition;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.util.Callback;
import javafx.util.converter.DefaultStringConverter;
import javafx.util.StringConverter;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import model.Bubble;
import model.InspectionType;
import viewmodel.DataEditorViewModel;
import viewmodel.PlanEditorViewModel;

import java.net.URL;
import java.util.Locale;
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

        configureTableCellSelection();

        SequenceIntegerStringConverter sequenceConverter = new SequenceIntegerStringConverter();
        columnSequenceNumber.setCellFactory(editableIntegerCellFactory(sequenceConverter));
        columnSequenceNumber.setOnEditCommit(event -> {
            if (isInvalidSequenceValue(event.getNewValue())) {
                tableView.refresh();
                selectTableCell(event.getTablePosition().getRow(), event.getTableColumn());
                return;
            }
            saveRow(event.getRowValue(), event.getTableColumn(), event.getNewValue(), null, null, null, null, null, null);
        });
        columnSequenceNumber.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getSequenceNumber()));

        columnCharacteristic.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getCharacteristic()));
        columnCharacteristic.setCellFactory(editableTextCellFactory());
        columnCharacteristic.setOnEditCommit(event -> {
            saveRow(event.getRowValue(), event.getTableColumn(), null, event.getNewValue(), null, null, null, null, null);
        });

        columnInspectionType.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getInspectionType()));
        columnInspectionType.setCellFactory(editableInspectionTypeCellFactory());
        columnInspectionType.setOnEditCommit(event -> {
            if (event.getNewValue() == null) {
                tableView.refresh();
                selectTableCell(event.getTablePosition().getRow(), event.getTableColumn());
                return;
            }
            saveRow(event.getRowValue(), event.getTableColumn(), null, null, event.getNewValue(), null, null, null, null);
        });

        StringConverter<Double> nullableDoubleConverter = new NullableDoubleStringConverter();
        columnNominalValue.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(numericValueForDisplay(data.getValue(), data.getValue().getNominalValue())));
        columnNominalValue.setCellFactory(editableNumericCellFactory(nullableDoubleConverter));
        columnNominalValue.setOnEditCommit(event -> {
            if (isInvalidNumericValue(event.getNewValue())) {
                tableView.refresh();
                selectTableCell(event.getTablePosition().getRow(), event.getTableColumn());
                return;
            }
            saveRow(event.getRowValue(), event.getTableColumn(), null, null, null, nullableDoubleConverter.toString(event.getNewValue()), null, null, null);
        });

        columnLowerTolerance.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(numericValueForDisplay(data.getValue(), data.getValue().getLowerTolerance())));
        columnLowerTolerance.setCellFactory(editableNumericCellFactory(nullableDoubleConverter));
        columnLowerTolerance.setOnEditCommit(event -> {
            if (isInvalidNumericValue(event.getNewValue())) {
                tableView.refresh();
                selectTableCell(event.getTablePosition().getRow(), event.getTableColumn());
                return;
            }
            saveRow(event.getRowValue(), event.getTableColumn(), null, null, null, null, nullableDoubleConverter.toString(event.getNewValue()), null, null);
        });

        columnUpperTolerance.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(numericValueForDisplay(data.getValue(), data.getValue().getUpperTolerance())));
        columnUpperTolerance.setCellFactory(editableNumericCellFactory(nullableDoubleConverter));
        columnUpperTolerance.setOnEditCommit(event -> {
            if (isInvalidNumericValue(event.getNewValue())) {
                tableView.refresh();
                selectTableCell(event.getTablePosition().getRow(), event.getTableColumn());
                return;
            }
            saveRow(event.getRowValue(), event.getTableColumn(), null, null, null, null, null, nullableDoubleConverter.toString(event.getNewValue()), null);
        });

        columnNote.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getNote()));
        columnNote.setCellFactory(editableTextCellFactory());
        columnNote.setOnEditCommit(event -> {
            saveRow(event.getRowValue(), event.getTableColumn(), null, null, null, null, null, null, event.getNewValue());
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
        dataEditorViewModel.getBubbles().addListener((ListChangeListener<Bubble>) change ->
                Platform.runLater(this::ensureTableSelection));
        ensureTableSelection();
    }

    public DataEditorViewModel getDataEditorViewModel(){
        return this.dataEditorViewModel;
    }

    private void saveRow(
            Bubble bubble,
            TableColumn<Bubble, ?> column,
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
        tableView.refresh();
        tableView.sort();
        selectBubbleCell(bubble, column);
    }

    private Callback<TableColumn<Bubble, String>, TableCell<Bubble, String>> editableTextCellFactory() {
        return column -> {
            TextFieldTableCell<Bubble, String> cell = new TextFieldTableCell<>(new DefaultStringConverter());
            configureEditableCellBehavior(cell);
            cell.itemProperty().addListener((observable, oldValue, newValue) -> updateTooltip(cell, newValue));
            return cell;
        };
    }

    private Callback<TableColumn<Bubble, Integer>, TableCell<Bubble, Integer>> editableIntegerCellFactory(StringConverter<Integer> converter) {
        return column -> {
            TextFieldTableCell<Bubble, Integer> cell = new TextFieldTableCell<>(converter);
            configureEditableCellBehavior(cell);
            return cell;
        };
    }

    private Callback<TableColumn<Bubble, InspectionType>, TableCell<Bubble, InspectionType>> editableInspectionTypeCellFactory() {
        return column -> {
            TextFieldTableCell<Bubble, InspectionType> cell = new TextFieldTableCell<>(inspectionTypeConverter());
            configureEditableCellBehavior(cell);
            cell.itemProperty().addListener((observable, oldValue, newValue) ->
                    updateTooltip(cell, inspectionTypeConverter().toString(newValue)));
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
            configureEditableCellBehavior(cell);
            cell.itemProperty().addListener((observable, oldValue, newValue) -> updateTooltip(cell, converter.toString(newValue)));
            return cell;
        };
    }

    private void configureEditableCellBehavior(TableCell<Bubble, ?> cell) {
        cell.setOnMousePressed(event -> {
            if (cell.isEmpty()) {
                return;
            }
            selectTableCell(cell.getIndex(), cell.getTableColumn());
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2 && cell.getTableColumn().isEditable()) {
                cell.startEdit();
                event.consume();
            }
        });
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
            selectBubbleCell(bubble, preferredSelectionColumn());
        } finally {
            syncingSelection = false;
        }
    }

    private void configureTableCellSelection() {
        tableView.getSelectionModel().setCellSelectionEnabled(true);
        tableView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        if (!tableView.getStyleClass().contains("cell-outline-table")) {
            tableView.getStyleClass().add("cell-outline-table");
        }
        tableView.addEventFilter(KeyEvent.KEY_PRESSED, this::handleTableKeyPressed);
    }

    private void handleTableKeyPressed(KeyEvent event) {
        if (event.getTarget() instanceof TextInputControl) {
            return;
        }

        if (event.getCode() == KeyCode.DELETE || event.getCode() == KeyCode.BACK_SPACE) {
            clearFocusedCell();
            event.consume();
            return;
        }

        if (isInlineEditCharacter(event)) {
            startTypingInFocusedCell(event.getText());
            event.consume();
            return;
        }

        if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.F2) {
            editFocusedCell();
            event.consume();
        }
    }

    private void editFocusedCell() {
        TablePosition<Bubble, ?> focusedCell = tableView.getFocusModel().getFocusedCell();
        if (focusedCell == null || focusedCell.getRow() < 0) {
            return;
        }

        TableColumn<Bubble, ?> column = focusedCell.getTableColumn();
        if (column == null || !tableView.isEditable() || !column.isEditable()) {
            return;
        }

        if (column != columnInspectionType) {
            Bubble bubble = focusedCell.getRow() >= tableView.getItems().size() ? null : tableView.getItems().get(focusedCell.getRow());
            if (bubble != null && column != columnSequenceNumber && isPassFailNumericColumn(column, bubble)) {
                return;
            }
        }

        selectTableCell(focusedCell.getRow(), column);
        tableView.edit(focusedCell.getRow(), column);
    }

    private void startTypingInFocusedCell(String typedText) {
        TablePosition<Bubble, ?> focusedCell = tableView.getFocusModel().getFocusedCell();
        if (focusedCell == null || focusedCell.getRow() < 0) {
            return;
        }

        TableColumn<Bubble, ?> column = focusedCell.getTableColumn();
        if (column == null || !tableView.isEditable() || !column.isEditable()) {
            return;
        }

        Bubble bubble = focusedCell.getRow() >= tableView.getItems().size() ? null : tableView.getItems().get(focusedCell.getRow());
        if (bubble == null || isPassFailNumericColumn(column, bubble)) {
            return;
        }

        String replacementText = typedText == null ? "" : typedText;
        selectTableCell(focusedCell.getRow(), column);
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

    private void clearFocusedCell() {
        TablePosition<Bubble, ?> focusedCell = tableView.getFocusModel().getFocusedCell();
        if (focusedCell == null || focusedCell.getRow() < 0) {
            return;
        }

        TableColumn<Bubble, ?> column = focusedCell.getTableColumn();
        if (column == null || !column.isEditable() || focusedCell.getRow() >= tableView.getItems().size()) {
            return;
        }

        Bubble bubble = tableView.getItems().get(focusedCell.getRow());
        if (column == columnCharacteristic) {
            saveRow(bubble, column, null, "", null, null, null, null, null);
            return;
        }
        if (column == columnNominalValue) {
            saveRow(bubble, column, null, null, null, "", null, null, null);
            return;
        }
        if (column == columnLowerTolerance) {
            saveRow(bubble, column, null, null, null, null, "", null, null);
            return;
        }
        if (column == columnUpperTolerance) {
            saveRow(bubble, column, null, null, null, null, null, "", null);
            return;
        }
        if (column == columnNote) {
            saveRow(bubble, column, null, null, null, null, null, null, "");
        }
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

    private void ensureTableSelection() {
        if (tableView.getItems().isEmpty()) {
            tableView.getSelectionModel().clearSelection();
            return;
        }

        Bubble selectedBubble = dataEditorViewModel.getPlanEditorViewModel().getSelectedBubble();
        if (selectedBubble != null && tableView.getItems().contains(selectedBubble)) {
            syncSelection(selectedBubble);
            return;
        }

        if (tableView.getSelectionModel().getSelectedCells().isEmpty()) {
            selectTableCell(0, columnCharacteristic);
        }
    }

    private void selectTableCell(int rowIndex, TableColumn<Bubble, ?> column) {
        if (column == null || rowIndex < 0 || rowIndex >= tableView.getItems().size()) {
            return;
        }

        tableView.requestFocus();
        tableView.getSelectionModel().clearAndSelect(rowIndex, column);
        tableView.getFocusModel().focus(rowIndex, column);
        tableView.scrollTo(rowIndex);
    }

    private void selectBubbleCell(Bubble bubble, TableColumn<Bubble, ?> column) {
        if (bubble == null) {
            tableView.getSelectionModel().clearSelection();
            return;
        }

        int rowIndex = tableView.getItems().indexOf(bubble);
        if (rowIndex < 0) {
            tableView.getSelectionModel().clearSelection();
            return;
        }
        selectTableCell(rowIndex, column == null ? columnCharacteristic : column);
    }

    private TableColumn<Bubble, ?> preferredSelectionColumn() {
        TablePosition<Bubble, ?> focusedCell = tableView.getFocusModel().getFocusedCell();
        TableColumn<Bubble, ?> column = focusedCell == null ? null : focusedCell.getTableColumn();
        return column == null ? columnCharacteristic : column;
    }

    private boolean isPassFailNumericColumn(TableColumn<Bubble, ?> column, Bubble bubble) {
        return bubble != null
                && bubble.getInspectionType() == InspectionType.PASS_FAIL
                && (column == columnNominalValue || column == columnLowerTolerance || column == columnUpperTolerance);
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
                    return null;
                }
                String normalized = value.trim().toLowerCase(Locale.ROOT)
                        .replace(" ", "")
                        .replace("-", "")
                        .replace("/", "");
                return switch (normalized) {
                    case "numeric", "number", "num", "n" -> InspectionType.NUMERIC;
                    case "passfail", "pf", "p", "pass", "fail" -> InspectionType.PASS_FAIL;
                    default -> null;
                };
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
            try {
                return Double.valueOf(value.trim());
            } catch (NumberFormatException exception) {
                return Double.NaN;
            }
        }
    }

    private static final class SequenceIntegerStringConverter extends StringConverter<Integer> {
        private static final int INVALID_SEQUENCE = Integer.MIN_VALUE;

        @Override
        public String toString(Integer value) {
            return value == null ? "" : value.toString();
        }

        @Override
        public Integer fromString(String value) {
            if (value == null || value.isBlank()) {
                return INVALID_SEQUENCE;
            }
            try {
                return Integer.valueOf(value.trim());
            } catch (NumberFormatException exception) {
                return INVALID_SEQUENCE;
            }
        }
    }

    private boolean isInvalidNumericValue(Double value) {
        return value != null && value.isNaN();
    }

    private boolean isInvalidSequenceValue(Integer value) {
        return value != null && value == SequenceIntegerStringConverter.INVALID_SEQUENCE;
    }
}
