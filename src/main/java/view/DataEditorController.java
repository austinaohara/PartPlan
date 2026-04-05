package view;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.VBox;
import javafx.util.converter.IntegerStringConverter;
import model.InspectionPlan;
import model.Part;
import view.tableCells.ControlBarCell;
import viewmodel.DataEditorViewModel;

import java.net.URL;
import java.util.ResourceBundle;

public class DataEditorController implements Initializable {
    private final DataEditorViewModel viewModel = new DataEditorViewModel();


    @FXML
    private Parent root;

    @FXML
    private VBox vbox;

    @FXML
    private TableView<Part> tableView;

    @FXML
    private TableColumn<Part, Integer> columnSpecNumber;

    @FXML
    private TableColumn<Part, Integer>  columnPlace;

    @FXML
    private TableColumn<Part, Double>  columnSpecification;

    @FXML
    private TableColumn<Part, String>  columnType;

    @FXML
    private TableColumn<Part, Double>  columnBonusTol; // TODO: unknown what this is

    @FXML
    private TableColumn<Part, Double>  columnMeasurement;

    @FXML
    private TableColumn<Part, Double>  columnControlBar;

    @FXML
    private TableColumn<Part, String>  columnInspectMethod;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        columnSpecNumber.setCellValueFactory(data -> data.getValue().specNumberProperty().asObject());

        columnPlace.setCellValueFactory(data -> data.getValue().placeProperty().asObject());
//        columnPlace.setCellFactory(TextFieldTableCell.forTableColumn(new IntegerStringConverter()));

        columnControlBar.setCellFactory(col -> new ControlBarCell());
        columnControlBar.setCellValueFactory(data -> data.getValue().specDeviation().asObject());

        columnType.setCellValueFactory(data -> data.getValue().typeProperty());

        columnBonusTol.setCellValueFactory(data -> data.getValue().bonusTolProperty().asObject());

        columnInspectMethod.setCellValueFactory(data -> data.getValue().inspectMethodProperty());

        tableView.setItems(viewModel.getParts());
        tableView.setEditable(true);

        viewModel.addPart(new Part(1, 1, "1", 5.0, 1, "1", 1, 2.0, "1"));
    }
}
