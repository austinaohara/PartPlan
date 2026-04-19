package view;

import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import service.FirebaseAuthService;
import viewmodel.LoginViewModel;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LoginController {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final FirebaseAuthService firebaseAuthService = new FirebaseAuthService();
    private final LoginViewModel viewModel = new LoginViewModel();

    public TextField emailTextField;
    public TextField passwordTextField;
    public Button loginButton;

    public Label errorLabel;

    public LoginController() throws IOException {
    }

    public void initialize(){
        viewModel.loading.addListener((obs, old, isLoading) -> {
            setControlsEditable(!isLoading);
        });

        viewModel.errorMessage.addListener((obs, old, msg) -> {
            if (msg != null){
                showError(msg);
            }
        });

        viewModel.loginSuccess.addListener((obs, old, success) -> {
            if (success) {
                // something
                Stage stage = (Stage) emailTextField.getScene().getWindow();
                stage.close();
            }
        });
    }

    public void onLoginPressed(ActionEvent event) {
        String email = emailTextField.getText();
        String password = passwordTextField.getText();

        setControlsEditable(false);
        errorLabel.setVisible(false);

        viewModel.login(email, password);
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
