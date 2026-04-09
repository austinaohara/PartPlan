package view;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import model.Bubble;
import view.tableCells.ControlBarTableCell;
import viewmodel.DataEditorViewModel;
import viewmodel.PlanEditorViewModel;

import java.net.URL;
import java.util.ResourceBundle;

public class DataEditorController implements Initializable {
    private final PlanEditorViewModel planEditorViewModel = new PlanEditorViewModel();
    private final DataEditorViewModel dataEditorViewModel = new DataEditorViewModel();

    @FXML
    private Parent root;

    @FXML
    private VBox vbox;

    @FXML
    private TableView<Bubble> tableView;

    @FXML
    private TableColumn<Bubble, Integer> columnSpecNumber;

    @FXML
    private TableColumn<Bubble, Double>  columnSpecification;

    @FXML
    private TableColumn<Bubble, String>  columnType;

    @FXML
    private TableColumn<Bubble, Double>  columnMeasurement;

    @FXML
    private TableColumn<Bubble, Double>  columnControlBar;

    @FXML
    private TableColumn<Bubble, String>  columnInspectMethod;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        columnSpecNumber.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getSequenceNumber()));

        columnControlBar.setCellFactory(col -> new ControlBarTableCell());
        columnControlBar.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getMeasuredValue())); //TODO: fix this

        columnType.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getInspectionType().toString()));

        columnMeasurement.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getMeasuredValue()));

        columnInspectMethod.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getInspectionType().toString()));

        dataEditorViewModel.addBubble(new Bubble());
        dataEditorViewModel.addBubble(new Bubble());
        tableView.setItems(dataEditorViewModel.getBubbles());
        tableView.setEditable(true);
    }
}
