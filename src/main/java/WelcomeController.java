import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

import java.io.IOException;

public class WelcomeController {
    @FXML
    private void onOpenPlanEditor(javafx.event.ActionEvent event) throws IOException {
        Node source = (Node) event.getSource();
        Stage stage = (Stage) source.getScene().getWindow();
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/plan-editor.fxml"));
        Parent root = loader.load();
        stage.setTitle("PartPlan - Plan Editor");
        source.getScene().setRoot(root);
    }

    @FXML
    private void onOpenPartEditor() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Part Editor");
        alert.setHeaderText("Part editor is not available yet.");
        alert.setContentText("This option is reserved for the future part editor view.");
        alert.showAndWait();
    }
}
