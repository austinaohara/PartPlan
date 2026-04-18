package view;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import model.auth.LoginResult;
import service.FirebaseAuthService;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LoginController {
    ExecutorService executor = Executors.newSingleThreadExecutor();
    FirebaseAuthService firebaseAuthService = new FirebaseAuthService();


    public TextField emailTextField;
    public TextField passwordTextField;
    public Button loginButton;

    public Label errorLabel;

    public LoginController() throws IOException {
    }

    public void onLoginPressed(ActionEvent event) {
    String email = emailTextField.getText();
    String password = passwordTextField.getText();

    setControlsEditable(false);
    errorLabel.setVisible(false);
    executor.execute(() -> {
        try {
            LoginResult result = firebaseAuthService.signIn(email, password);
            Platform.runLater(() -> {
                if (result.isSuccess()) {
                    String token = result.getIdToken();
                    showError("Success!");
                } else {
                    showError(result.getErrorMessage());
                }
                setControlsEditable(true);
            });
        } catch (Exception e) {
            Platform.runLater(() -> {
                setControlsEditable(true);
                showError("An error occurred: " + e.getMessage());
            });
        }
    });
}

    private void setControlsEditable(boolean input){
        emailTextField.setDisable(!input);
        passwordTextField.setDisable(!input);
        loginButton.setDisable(!input);

    }

    private void showError(String message){
        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }
}
