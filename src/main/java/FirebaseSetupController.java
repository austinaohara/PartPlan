import app.AppContext;
import app.AppMenuSupport;
import app.AppStoragePaths;
import app.UserFacingErrorMessages;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import service.auth.AuthService;
import service.config.FirebaseProjectConfig;
import service.config.FirebaseProjectConfigStore;
import view.AppNavigator;

import java.io.IOException;

public class FirebaseSetupController {
    private final AppContext appContext;
    private final FirebaseProjectConfigStore projectConfigStore;
    private final AuthService authService;

    @FXML
    private BorderPane rootPane;
    @FXML
    private PasswordField apiKeyField;
    @FXML
    private TextField projectIdField;
    @FXML
    private TextField appIdField;
    @FXML
    private TextField storageBucketField;
    @FXML
    private TextField authDomainField;
    @FXML
    private TextField databaseIdField;
    @FXML
    private Label storagePathLabel;
    @FXML
    private Label statusLabel;
    @FXML
    private Button continueButton;

    public FirebaseSetupController(AppContext appContext) {
        this.appContext = appContext;
        this.projectConfigStore = appContext.getProjectConfigStore();
        this.authService = appContext.getAuthService();
    }

    @FXML
    private void initialize() {
        AppMenuSupport.install(rootPane, AppMenuSupport.MenuContext.GENERAL, new AppMenuSupport.MenuCallbacks(
                null,
                null,
                () -> AppMenuSupport.openOpenAiSettingsWindow(rootPane)
        ));
        storagePathLabel.setText(AppStoragePaths.appDataDirectory().toString());

        try {
            projectConfigStore.load().ifPresent(config -> {
                apiKeyField.setText(config.apiKey());
                projectIdField.setText(config.projectId());
                appIdField.setText(config.appId());
                storageBucketField.setText(config.storageBucket());
                authDomainField.setText(config.authDomain());
                databaseIdField.setText(config.databaseId());
            });
        } catch (RuntimeException exception) {
            setStatus("Saved Firebase settings could not be read. Re-enter them here.", true);
        }

        continueButton.setDisable(!appContext.hasUsableProjectConfig());
        if (statusLabel.getText() == null || statusLabel.getText().isBlank()) {
            setStatus("Enter the Firebase web-app settings for this project.", false);
        }
    }

    @FXML
    private void onSaveAndContinue() {
        try {
            projectConfigStore.save(readConfigFromFields());
            authService.signOut();
            continueButton.setDisable(false);
            AppNavigator.swapRoot(rootPane, "/fxml/login.fxml", "PartPlan - Sign In");
        } catch (RuntimeException | IOException exception) {
            setStatus(UserFacingErrorMessages.format(exception, "Unable to save the Firebase settings."), true);
        }
    }

    @FXML
    private void onOpenLogin() {
        if (!appContext.hasUsableProjectConfig()) {
            setStatus("Save valid Firebase settings before continuing to sign in.", true);
            return;
        }

        try {
            AppNavigator.swapRoot(rootPane, "/fxml/login.fxml", "PartPlan - Sign In");
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to open the sign-in screen.", exception);
        }
    }

    private FirebaseProjectConfig readConfigFromFields() {
        FirebaseProjectConfig config = new FirebaseProjectConfig(
                apiKeyField.getText(),
                projectIdField.getText(),
                appIdField.getText(),
                storageBucketField.getText(),
                authDomainField.getText(),
                databaseIdField.getText()
        );
        if (!config.isComplete()) {
            throw new IllegalArgumentException("API key, project ID, app ID, and storage bucket are required.");
        }
        return config;
    }

    private void setStatus(String message, boolean error) {
        statusLabel.setText(message);
        statusLabel.setStyle(error ? "-fx-text-fill: #8c1d18;" : "-fx-text-fill: #48657d;");
    }
}
