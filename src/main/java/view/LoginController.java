package view;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import viewmodel.LoginViewModel;

import java.io.IOException;

public class LoginController {
    private final LoginViewModel viewModel = new LoginViewModel();

    public TextField emailTextField;
    public TextField passwordTextField;
    public Button loginButton;

    public Label errorLabel;

    public LoginController() throws IOException {
    }

    public void initialize(){
        viewModel.loading.addListener((_, _, isLoading) -> {
            setControlsEditable(!isLoading);
        });

        viewModel.errorMessage.addListener((_, _, msg) -> {
            if (msg != null){
                showError(msg);
            }
        });

        viewModel.loginSuccess.addListener((_, _, success) -> {
            if (success) {
                Stage stage = (Stage) emailTextField.getScene().getWindow();
                stage.close();
            }
        });
    }

    public void onLoginPressed() {
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
