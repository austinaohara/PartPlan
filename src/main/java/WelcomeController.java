import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import view.AppNavigator;

import java.io.IOException;

public class WelcomeController {
    public Label welcomeLabel;

    public Button signInButton;
    public Button registerButton;
    public Button signOutButton;

    public Button openPlanEditorButton;
    public Button openPartEditorButton;

    private Stage loginStage;
    private Stage registerStage;

    private final WelcomeViewModel viewModel = new WelcomeViewModel();

    public WelcomeController() throws IOException {
    }

    @FXML
    private void initialize() throws IOException {
        viewModel.refreshSignInStatus();

        openPlanEditorButton.disableProperty().bind(viewModel.isSignedIn.not());
        openPartEditorButton.disableProperty().bind(viewModel.isSignedIn.not());

        signInButton.managedProperty().bind(signInButton.visibleProperty()); // done so that layout isnt changed when invisible
        registerButton.managedProperty().bind(registerButton.visibleProperty());
        signOutButton.managedProperty().bind(signOutButton.visibleProperty());

        updateSignInUI(viewModel.isSignedIn.get());

        viewModel.isSignedIn.addListener((obs, wasSignedIn, isNowSignedIn) -> {
            updateSignInUI(isNowSignedIn);
        });
    }

    @FXML
    private void onOpenPlanEditor(ActionEvent event) throws IOException {
        AppNavigator.swapRoot((Node) event.getSource(), "/fxml/plan-editor.fxml", "PartPlan - Plan Editor");
    }

    @FXML
    private void onOpenPartEditor(ActionEvent event) throws IOException {
        AppNavigator.swapRoot((Node) event.getSource(), "/fxml/inspection-lot-browser.fxml", "PartPlan - Inspection Lots");
    }

    public void onSignInPressed() throws IOException {
        openLoginWindow();
    }

    public void onRegisterPressed() throws IOException {
        openRegisterWindow();
    }

    public void onSignOutPressed() {
        viewModel.signOut();
        updateSignInUI(false);
    }

    private void openLoginWindow() throws IOException {
        if (loginStage == null) {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login-window.fxml"));
            Scene scene = new Scene(loader.load());

            loginStage = new Stage();
            loginStage.setAlwaysOnTop(true);
            loginStage.setTitle("Login");
            loginStage.setScene(scene);

            loginStage.setOnCloseRequest(e -> loginStage = null);
            loginStage.setOnHidden(e -> viewModel.refreshSignInStatus());

            loginStage.show();
        } else {
            loginStage.toFront();
        }
    }

    private void updateSignInUI(boolean isSignedIn){
        signInButton.setVisible(!isSignedIn);
        registerButton.setVisible(!isSignedIn);
        signOutButton.setVisible(isSignedIn);

        if(isSignedIn){
            String companyName = viewModel.getCompanyName();
            if (companyName == null || companyName.isBlank()) {
                welcomeLabel.setText("Welcome, " + viewModel.getUsername());
            } else {
                welcomeLabel.setText("Welcome, " + viewModel.getUsername() + " @ " + companyName);
            }
        } else {
            welcomeLabel.setText("Please sign in to continue.");
        }
    }

    private void openRegisterWindow() throws IOException {
        if (registerStage == null) {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/register-window.fxml"));
            Scene scene = new Scene(loader.load());

            registerStage = new Stage();
            registerStage.setAlwaysOnTop(true);
            registerStage.setTitle("Register");
            registerStage.setScene(scene);

            registerStage.setOnCloseRequest(e -> registerStage = null);
            registerStage.setOnHidden(e -> viewModel.refreshSignInStatus());

            registerStage.show();
        } else {
            registerStage.toFront();
        }
    }
}
