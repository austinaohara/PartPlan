package view;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import viewmodel.LoginViewModel;

import java.io.IOException;

public class LoginController {
    private final LoginViewModel viewModel = new LoginViewModel();

    public TextField emailTextField;
    public PasswordField passwordField;
    public TextField companyTextField;
    public TextField inviteCodeTextField;
    public TextField inviteEmailTextField;
    public Button loginButton;
    public Button createCompanyButton;
    public Button acceptInviteButton;
    public Button createInviteButton;

    public Label errorLabel;
    public Label infoLabel;

    public LoginController() throws IOException {
    }

    public void initialize(){
        viewModel.loading.addListener((observable, oldValue, isLoading) -> {
            setControlsEditable(!isLoading);
        });

        viewModel.errorMessage.addListener((observable, oldValue, msg) -> {
            if (msg != null){
                showError(msg);
            }
        });

        viewModel.infoMessage.addListener((observable, oldValue, msg) -> {
            if (msg != null) {
                showInfo(msg);
            }
        });

        viewModel.loginSuccess.addListener((observable, oldValue, success) -> {
            if (success) {
                Stage stage = (Stage) emailTextField.getScene().getWindow();
                stage.close();
            }
        });
    }

    public void onLoginPressed() {
        String email = emailTextField.getText();
        String password = passwordField.getText();
        String companyName = companyTextField.getText();

        clearMessages();

        viewModel.signInWithEmail(email, password, companyName);
    }

    public void onCreateCompanyPressed() {
        clearMessages();
        viewModel.createCompany(
                emailTextField.getText(),
                passwordField.getText(),
                companyTextField.getText()
        );
    }

    public void onAcceptInvitePressed() {
        clearMessages();
        viewModel.acceptInvite(
                emailTextField.getText(),
                passwordField.getText(),
                inviteCodeTextField.getText()
        );
    }

    public void onCreateInvitePressed() {
        clearMessages();
        viewModel.createInvite(
                emailTextField.getText(),
                passwordField.getText(),
                companyTextField.getText(),
                inviteEmailTextField.getText()
        );
    }

    private void setControlsEditable(boolean input){
        emailTextField.setDisable(!input);
        passwordField.setDisable(!input);
        companyTextField.setDisable(!input);
        inviteCodeTextField.setDisable(!input);
        inviteEmailTextField.setDisable(!input);
        loginButton.setDisable(!input);
        createCompanyButton.setDisable(!input);
        acceptInviteButton.setDisable(!input);
        createInviteButton.setDisable(!input);
    }

    private void showError(String message){
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        infoLabel.setVisible(false);
    }

    private void showInfo(String message) {
        infoLabel.setText(message);
        infoLabel.setVisible(true);
        errorLabel.setVisible(false);
    }

    private void clearMessages() {
        errorLabel.setVisible(false);
        infoLabel.setVisible(false);
    }
}
