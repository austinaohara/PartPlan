import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
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
}
