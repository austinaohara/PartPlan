module edu.farmingdale.partplan {
    requires javafx.controls;
    requires javafx.fxml;


    opens edu.farmingdale.partplan to javafx.fxml;
    exports edu.farmingdale.partplan;
    exports edu.farmingdale.partplan.ui;
    opens edu.farmingdale.partplan.ui to javafx.fxml;
}