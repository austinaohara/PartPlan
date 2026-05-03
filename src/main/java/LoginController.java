import app.AppContext;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import service.auth.AuthService;
import service.auth.AuthenticationException;
import service.config.FirebaseProjectConfig;
import service.config.FirebaseProjectConfigStore;
import service.session.SessionManager;
import service.session.UserSession;
import view.AppNavigator;

import java.io.IOException;
import java.util.concurrent.Callable;

public class LoginController {
    private final AppContext appContext;
    private final FirebaseProjectConfigStore projectConfigStore;
    private final SessionManager sessionManager;
    private final AuthService authService;

    @FXML
    private BorderPane rootPane;
    @FXML
    private Label projectSummaryLabel;
    @FXML
    private TextField emailField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private Label statusLabel;
    @FXML
    private Button editProjectButton;
    @FXML
    private Button signInButton;
    @FXML
    private Button signUpButton;
    @FXML
    private ProgressIndicator progressIndicator;

    public LoginController(AppContext appContext) {
        this.appContext = appContext;
        this.projectConfigStore = appContext.getProjectConfigStore();
        this.sessionManager = appContext.getSessionManager();
        this.authService = appContext.getAuthService();
    }

    @FXML
    private void initialize() {
        progressIndicator.setVisible(false);
        progressIndicator.setManaged(false);

        FirebaseProjectConfig config = loadProjectConfig();
        if (config == null) {
            openFirebaseSetup();
            return;
        }

        projectSummaryLabel.setText("Project: " + config.projectId());

        if (sessionManager.getCurrentSession().isPresent()) {
            UserSession savedSession = sessionManager.requireCurrentSession();
            runAuthTask(
                    "Restoring saved session...",
                    () -> savedSession.getRefreshToken() == null || savedSession.getRefreshToken().isBlank()
                            ? savedSession
                            : authService.refreshSession(savedSession),
                    this::openWelcomeAsync
            );
        } else {
            setStatus("Sign in with the Firebase project configured for this workspace.", false);
        }
    }

    @FXML
    private void onEditProjectSettings() {
        openFirebaseSetup();
    }

    @FXML
    private void onSignIn() {
        authenticate(() -> authService.signIn(emailField.getText(), passwordField.getText()));
    }

    @FXML
    private void onSignUp() {
        authenticate(() -> authService.signUp(emailField.getText(), passwordField.getText()));
    }

    private void authenticate(Callable<UserSession> authCall) {
        try {
            validateCredentials();
        } catch (RuntimeException exception) {
            setStatus(exception.getMessage(), true);
            return;
        }

        runAuthTask("Contacting Firebase...", authCall, session -> {
            passwordField.clear();
            openWelcome();
        });
    }

    private void runAuthTask(String busyMessage, Callable<UserSession> authCall, java.util.function.Consumer<UserSession> onSuccess) {
        setBusy(true, busyMessage);

        Task<UserSession> task = new Task<>() {
            @Override
            protected UserSession call() throws Exception {
                return authCall.call();
            }
        };

        task.setOnSucceeded(event -> {
            setBusy(false, null);
            onSuccess.accept(task.getValue());
        });
        task.setOnFailed(event -> {
            Throwable exception = task.getException();
            authService.signOut();
            if (exception instanceof AuthenticationException authenticationException
                    && authenticationException.getMessage() != null) {
                setStatus(authenticationException.getMessage(), true);
            } else if (exception != null && exception.getMessage() != null) {
                setStatus(exception.getMessage(), true);
            } else {
                setStatus("Unable to complete the Firebase sign-in flow.", true);
            }
            setBusy(false, null);
        });

        Thread thread = new Thread(task, "firebase-auth");
        thread.setDaemon(true);
        thread.start();
    }

    private void validateCredentials() {
        if (emailField.getText() == null || emailField.getText().isBlank()) {
            throw new IllegalArgumentException("Email is required.");
        }
        if (passwordField.getText() == null || passwordField.getText().isBlank()) {
            throw new IllegalArgumentException("Password is required.");
        }
    }

    private FirebaseProjectConfig loadProjectConfig() {
        try {
            return projectConfigStore.load().orElse(null);
        } catch (RuntimeException exception) {
            authService.signOut();
            setStatus("Saved Firebase settings could not be read. Re-enter them.", true);
            return null;
        }
    }

    private void openFirebaseSetup() {
        try {
            if (rootPane.getScene() == null) {
                Platform.runLater(this::openFirebaseSetup);
                return;
            }
            AppNavigator.swapRoot(rootPane, "/fxml/firebase-config.fxml", "PartPlan - Firebase Setup");
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to open the Firebase setup screen.", exception);
        }
    }

    private void openWelcomeAsync(UserSession ignored) {
        Platform.runLater(this::openWelcome);
    }

    private void openWelcome() {
        try {
            if (rootPane.getScene() == null) {
                Platform.runLater(this::openWelcome);
                return;
            }
            AppNavigator.swapRoot(rootPane, "/fxml/welcome.fxml", "PartPlan");
        } catch (IOException exception) {
            setStatus("Signed in, but the home screen could not be opened.", true);
        }
    }

    private void setBusy(boolean busy, String message) {
        progressIndicator.setManaged(busy);
        progressIndicator.setVisible(busy);
        editProjectButton.setDisable(busy);
        signInButton.setDisable(busy);
        signUpButton.setDisable(busy);
        if (message != null) {
            setStatus(message, false);
        }
    }

    private void setStatus(String message, boolean error) {
        statusLabel.setText(message);
        statusLabel.setStyle(error ? "-fx-text-fill: #8c1d18;" : "-fx-text-fill: #48657d;");
    }
}
