package view;

import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import model.InspectionPlan;
import viewmodel.DataEditorViewModel;

public class DataEditorController {
    private final DataEditorViewModel viewModel = new DataEditorViewModel();

    @FXML
    private Parent root;

    @FXML
    private VBox vbox;

    @FXML
    private TableView<InspectionPlan> tableView;

    @FXML
    private TableColumn<InspectionPlan, Integer> columnSpecNumber;

    @FXML
    private TableColumn<InspectionPlan, Integer>  columnPlace;

    /*TODO: figure out what variable types to use.
    *  some of them seem to be images + text. use hbox? or use more columns?*/
//    @FXML
//    private TableColumn<InspectionPlan, VALUE>  columnSpecification;

    @FXML
    private TableColumn<InspectionPlan, String>  columnType;

//    @FXML
//    private TableColumn<InspectionPlan, VALUE>  columnBonusTol;

//    @FXML
//    private TableColumn<InspectionPlan, VALUE>  columnMeasurement;

//    // TODO: make control bar a resizable bar of colors
//    @FXML
//    private TableColumn<InspectionPlan, VALUE>  columnControlBar;

    @FXML
    private TableColumn<InspectionPlan, String>  columnInspectMethod;

}
