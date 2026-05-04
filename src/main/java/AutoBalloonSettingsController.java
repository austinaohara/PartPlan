import app.AppContext;
import app.AppStoragePaths;
import app.UserFacingErrorMessages;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import service.config.AutoBalloonConfig;
import service.config.AutoBalloonConfigStore;

public class AutoBalloonSettingsController {
    private final AutoBalloonConfigStore configStore;

    @FXML
    private PasswordField apiKeyField;
    @FXML
    private ComboBox<String> modelComboBox;
    @FXML
    private Label storagePathLabel;
    @FXML
    private Label statusLabel;

    public AutoBalloonSettingsController(AppContext appContext) {
        this.configStore = appContext.getAutoBalloonConfigStore();
    }

    @FXML
    private void initialize() {
        storagePathLabel.setText(AppStoragePaths.autoBalloonConfigPath().toString());
        modelComboBox.setItems(FXCollections.observableArrayList(
                "gpt-5.4-mini",
                "gpt-5.4",
                "gpt-5.5"
        ));
        modelComboBox.setValue(AutoBalloonConfig.DEFAULT_MODEL);
        try {
            configStore.load().ifPresent(config -> {
                apiKeyField.setText(config.apiKey());
                modelComboBox.setValue(config.resolvedModel());
            });
        } catch (RuntimeException exception) {
            setStatus(UserFacingErrorMessages.format(exception, "Saved auto-balloon settings could not be read."), true);
        }
    }

    @FXML
    private void onSaveSettings() {
        try {
            configStore.save(readConfig());
            setStatus("Auto-balloon settings saved.", false);
        } catch (RuntimeException exception) {
            setStatus(UserFacingErrorMessages.format(exception, "Unable to save auto-balloon settings."), true);
        }
    }

    @FXML
    private void onClearSettings() {
        try {
            configStore.clear();
            apiKeyField.clear();
            modelComboBox.setValue(AutoBalloonConfig.DEFAULT_MODEL);
            setStatus("Auto-balloon settings cleared.", false);
        } catch (RuntimeException exception) {
            setStatus(UserFacingErrorMessages.format(exception, "Unable to clear auto-balloon settings."), true);
        }
    }

    private AutoBalloonConfig readConfig() {
        AutoBalloonConfig config = new AutoBalloonConfig(
                apiKeyField.getText(),
                modelComboBox.getValue()
        );
        if (!config.isComplete()) {
            throw new IllegalArgumentException("An OpenAI API key is required.");
        }
        return config;
    }

    private void setStatus(String message, boolean error) {
        statusLabel.setText(message == null ? "" : message);
        statusLabel.getStyleClass().removeAll("status-error", "status-ok");
        statusLabel.getStyleClass().add(error ? "status-error" : "status-ok");
    }
}
