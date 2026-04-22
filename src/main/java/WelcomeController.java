import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import view.AppNavigator;

import java.io.IOException;

public class WelcomeController {
    @FXML
    private void onOpenPlanEditor(ActionEvent event) throws IOException {
        AppNavigator.swapRoot((Node) event.getSource(), "/fxml/plan-editor.fxml", "PartPlan - Plan Editor");
    }

    @FXML
    private void onOpenPartEditor(ActionEvent event) throws IOException {
        AppNavigator.swapRoot((Node) event.getSource(), "/fxml/inspection-lot-browser.fxml", "PartPlan - Inspection Lots");
    }
}
