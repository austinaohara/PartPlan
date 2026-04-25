import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.stage.Stage;
import repository.AuthRepository;
import view.AppNavigator;

import java.io.IOException;

public class WelcomeController {

    private Stage loginStage;

    private final WelcomeViewModel viewModel = new WelcomeViewModel();

    public WelcomeController() throws IOException {
    }

    @FXML
    private void initialize() throws IOException {
        viewModel.refreshSignInStatus();

        openPlanEditorButton.disableProperty().bind(viewModel.isSignedIn.not());
        openPartEditorButton.disableProperty().bind(viewModel.isSignedIn.not());

        viewModel.isSignedIn.addListener((obs, wasSignedIn, isNowSignedIn) -> {
            if (isNowSignedIn) {
                welcomeLabel.setText("Welcome, " + viewModel.getUid());
            }
        });

        if (!viewModel.isSessionValid()){
            openLoginWindow();
        }
    }

    @FXML
    private void onOpenPlanEditor(ActionEvent event) throws IOException {
        if (AuthRepository.getToken() == null) {
            openLoginWindow();
        } else {
            AppNavigator.swapRoot((Node) event.getSource(), "/fxml/plan-editor.fxml", "PartPlan - Plan Editor");
        }
    }

    @FXML
    private void onOpenPartEditor(ActionEvent event) throws IOException {
        AppNavigator.swapRoot((Node) event.getSource(), "/fxml/inspection-lot-browser.fxml", "PartPlan - Inspection Lots");
    }

    private void openLoginWindow() throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login-window.fxml"));
        Scene scene = new Scene(loader.load());

        if (loginStage == null) {
            loginStage = new Stage();
            loginStage.setAlwaysOnTop(true);
            loginStage.setTitle("Login");
            loginStage.setScene(scene);
            loginStage.show();
        } else {
            loginStage.toFront();
        }
    }
}
