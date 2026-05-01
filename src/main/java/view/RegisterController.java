package view;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import viewmodel.LoginViewModel;

import java.io.IOException;

public class RegisterController {
    private final LoginViewModel viewModel = new LoginViewModel();

    public TextField emailTextField;
    public PasswordField passwordField;
    public TextField companyTextField;
    public Button registerButton;
    public Label errorLabel;

    public RegisterController() throws IOException {
    }

    public void initialize() {
        viewModel.loading.addListener((observable, oldValue, isLoading) -> setControlsEditable(!isLoading));

        viewModel.errorMessage.addListener((observable, oldValue, message) -> {
            if (message != null) {
                showError(message);
            }
        });

        viewModel.loginSuccess.addListener((observable, oldValue, success) -> {
            if (success) {
                Stage stage = (Stage) emailTextField.getScene().getWindow();
                stage.close();
            }
        });
    }

    public void onRegisterPressed() {
        errorLabel.setVisible(false);
        viewModel.registerAndCreateCompany(
                emailTextField.getText(),
                passwordField.getText(),
                companyTextField.getText()
        );
    }

    private void setControlsEditable(boolean input) {
        emailTextField.setDisable(!input);
        passwordField.setDisable(!input);
        companyTextField.setDisable(!input);
        registerButton.setDisable(!input);
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }
}
