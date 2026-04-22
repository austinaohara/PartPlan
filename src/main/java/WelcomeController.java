import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.stage.Stage;

import java.io.IOException;

public class WelcomeController {
    @FXML
    private void onOpenPlanEditor(ActionEvent event) throws IOException {
        openEditor(event, "/fxml/plan-editor.fxml", "PartPlan - Plan Editor");
    }

    @FXML
    private void onOpenPartEditor(ActionEvent event) throws IOException {
        openEditor(event, "/fxml/part-editor.fxml", "PartPlan - Part Editor");
    }

    private void openEditor(ActionEvent event, String fxmlPath, String title) throws IOException {
        Node source = (Node) event.getSource();
        Stage stage = (Stage) source.getScene().getWindow();
        FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
        Parent root = loader.load();
        stage.setTitle(title);
        source.getScene().setRoot(root);
    }
}
