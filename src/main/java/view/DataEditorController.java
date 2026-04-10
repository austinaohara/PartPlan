package view;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.util.converter.BooleanStringConverter;
import javafx.util.converter.DefaultStringConverter;
import javafx.util.converter.DoubleStringConverter;
import javafx.util.converter.IntegerStringConverter;
import model.Bubble;
import model.BubbleStatus;
import model.InspectionType;
import viewmodel.DataEditorViewModel;
import viewmodel.PlanEditorViewModel;

import java.net.URL;
import java.util.ResourceBundle;

public class DataEditorController implements Initializable {
    private final DataEditorViewModel dataEditorViewModel;

    @FXML
    private Parent root;

    @FXML
    private Label currentPlanLabel;

    @FXML
    private TableView<Bubble> tableView;

    @FXML
    private TableColumn<Bubble, Integer> columnSequenceNumber;

    @FXML
    private TableColumn<Bubble, Double> columnRadius;

    @FXML
    private TableColumn<Bubble, String> columnLabel;

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
    private TableColumn<Bubble, Boolean> columnExpectedPassFail;

    @FXML
    private TableColumn<Bubble, Double> columnMeasuredValue;

    @FXML
    private TableColumn<Bubble, Boolean> columnActualPassFail;

    @FXML
    private TableColumn<Bubble, BubbleStatus> columnStatus;

    @FXML
    private TableColumn<Bubble, String> columnNote;


    public DataEditorController(PlanEditorViewModel planEditorViewModel) { //requires planEditorViewmodel
        this.dataEditorViewModel = new DataEditorViewModel(planEditorViewModel);
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        currentPlanLabel.textProperty().bind(dataEditorViewModel.getPlanEditorViewModel().planNameProperty());

        columnSequenceNumber.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue().getSequenceNumber()));

        columnLabel.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getLabel()));
        columnSequenceNumber.setCellFactory(TextFieldTableCell.forTableColumn(new IntegerStringConverter()));
        columnSequenceNumber.setOnEditCommit(event -> {
            event.getRowValue().setSequenceNumber(event.getNewValue());
        });

        columnCharacteristic.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getCharacteristic()));
        columnCharacteristic.setCellFactory(TextFieldTableCell.forTableColumn(new DefaultStringConverter()));
        columnCharacteristic.setOnEditCommit(event -> {
            event.getRowValue().setCharacteristic(event.getNewValue());
        });

        columnInspectionType.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getInspectionType()));

        columnNominalValue.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getNominalValue()));
        columnNominalValue.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleStringConverter()));
        columnNominalValue.setOnEditCommit(event -> {
            event.getRowValue().setNominalValue(event.getNewValue());
        });

        columnLowerTolerance.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getLowerTolerance()));
        columnLowerTolerance.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleStringConverter()));
        columnLowerTolerance.setOnEditCommit(event -> {
        event.getRowValue().setLowerTolerance(event.getNewValue());});

        columnUpperTolerance.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getUpperTolerance()));
        columnUpperTolerance.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleStringConverter()));
        columnUpperTolerance.setOnEditCommit(event -> {
        event.getRowValue().setUpperTolerance(event.getNewValue());});

        columnExpectedPassFail.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getExpectedPassFail()));
        columnExpectedPassFail.setCellFactory(TextFieldTableCell.forTableColumn(new BooleanStringConverter()));
        columnExpectedPassFail.setOnEditCommit(event -> {
        event.getRowValue().setExpectedPassFail(event.getNewValue());});

        columnMeasuredValue.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getMeasuredValue()));
        columnMeasuredValue.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleStringConverter()));
        columnMeasuredValue.setOnEditCommit(event -> {
        event.getRowValue().setMeasuredValue(event.getNewValue());});

        columnActualPassFail.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getActualPassFail()));
        columnActualPassFail.setCellFactory(TextFieldTableCell.forTableColumn(new BooleanStringConverter()));
        columnActualPassFail.setOnEditCommit(event -> {
        event.getRowValue().setActualPassFail(event.getNewValue());});

        columnStatus.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getStatus()));
//        columnStatus.setCellFactory(TextFieldTableCell.forTableColumn(new ???);
//        columnStatus.setOnEditCommit(event -> {
//        event.getRowValue().setStatus(event.getNewValue());});

        columnNote.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getNote()));
        columnNote.setCellFactory(TextFieldTableCell.forTableColumn(new DefaultStringConverter()));
        columnNote.setOnEditCommit(event -> {
        event.getRowValue().setNote(event.getNewValue());});

        tableView.setItems(dataEditorViewModel.getBubbles());
        tableView.setEditable(true);
    }

    public DataEditorViewModel getDataEditorViewModel(){
        return this.dataEditorViewModel;
    }
}
