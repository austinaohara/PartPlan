module edu.farmingdale.partplan {
    requires javafx.controls;
    requires javafx.fxml;


    opens edu.farmingdale.partplan to javafx.fxml;
    exports edu.farmingdale.partplan;
}