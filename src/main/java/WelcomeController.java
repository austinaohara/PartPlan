import app.AppContext;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import service.auth.AuthService;
import service.session.SessionManager;
import service.session.UserSession;
import view.AppNavigator;

import java.io.IOException;

public class WelcomeController {
    private final AppContext appContext;
    private final SessionManager sessionManager;
    private final AuthService authService;

    @FXML
    private Label welcomeLabel;

    public WelcomeController(AppContext appContext) {
        this.appContext = appContext;
        this.sessionManager = appContext.getSessionManager();
        this.authService = appContext.getAuthService();
    }

    @FXML
    private void initialize() {
        if (sessionManager.getCurrentSession().isEmpty()) {
            Platform.runLater(this::openLogin);
            return;
        }

        UserSession session = sessionManager.requireCurrentSession();
        String identity = session.getEmail() == null || session.getEmail().isBlank()
                ? session.getUid()
                : session.getEmail();
        welcomeLabel.setText("Welcome, " + identity);
    }

    @FXML
    private void onOpenPlanEditor(ActionEvent event) throws IOException {
        AppNavigator.swapRoot((Node) event.getSource(), "/fxml/plan-editor.fxml", "PartPlan - Plan Editor");
    }

    @FXML
    private void onOpenPartEditor(ActionEvent event) throws IOException {
        AppNavigator.swapRoot((Node) event.getSource(), "/fxml/inspection-lot-browser.fxml", "PartPlan - Inspection Lots");
    }

    @FXML
    private void onOpenAutoBalloonSettings(ActionEvent event) throws IOException {
        AppNavigator.swapRoot((Node) event.getSource(), "/fxml/auto-balloon-config.fxml", "PartPlan - OpenAI Settings");
    }

    @FXML
    private void onSignOut(ActionEvent event) throws IOException {
        authService.signOut();
        AppNavigator.swapRoot((Node) event.getSource(), "/fxml/login.fxml", "PartPlan - Sign In");
    }

    private void openLogin() {
        if (welcomeLabel.getScene() == null) {
            Platform.runLater(this::openLogin);
            return;
        }

        try {
            AppNavigator.swapRoot(welcomeLabel, "/fxml/login.fxml", "PartPlan - Sign In");
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to open the sign-in screen.", exception);
        }
    }
}
