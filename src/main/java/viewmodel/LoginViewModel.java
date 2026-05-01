package viewmodel;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import model.auth.LoginResult;
import repository.AuthRepository;
import service.CompanyService;
import service.FirebaseAuthService;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class LoginViewModel {
    private final AuthRepository auth = new AuthRepository();
    private final FirebaseAuthService firebaseAuthService = new FirebaseAuthService();
    private final CompanyService companyService = new CompanyService();
    private final Executor executor = Executors.newSingleThreadExecutor();

    public final StringProperty errorMessage = new SimpleStringProperty();
    public final StringProperty infoMessage = new SimpleStringProperty();
    public final BooleanProperty loginSuccess = new SimpleBooleanProperty(false);
    public final BooleanProperty loading = new SimpleBooleanProperty(false);

    public LoginViewModel() throws IOException {
    }

    public void signInWithEmail(String email, String password, String companyName){
        runCompanyAuth(email, password, () -> {
            LoginResult result = firebaseAuthService.signIn(email, password);
            if (!result.isSuccess()) {
                throw new IllegalStateException(result.getErrorMessage());
            }

            CompanyService.CompanySession company = companyService.authenticateCompany(companyName, result, email);
            saveSession(result, email, company);
            Platform.runLater(() -> loginSuccess.set(true));
        });
    }

    public void createCompany(String email, String password, String companyName) {
        runCompanyAuth(email, password, () -> {
            LoginResult result = firebaseAuthService.signIn(email, password);
            if (!result.isSuccess()) {
                throw new IllegalStateException(result.getErrorMessage());
            }

            CompanyService.CompanySession company = companyService.createCompany(companyName, result, email);
            saveSession(result, email, company);
            Platform.runLater(() -> loginSuccess.set(true));
        });
    }

    public void registerAndCreateCompany(String email, String password, String companyName) {
        runCompanyAuth(email, password, () -> {
            LoginResult result = firebaseAuthService.register(email, password);
            if (!result.isSuccess()) {
                throw new IllegalStateException(result.getErrorMessage());
            }

            CompanyService.CompanySession company = companyService.createCompany(companyName, result, email);
            saveSession(result, email, company);
            Platform.runLater(() -> loginSuccess.set(true));
        });
    }

    public void acceptInvite(String email, String password, String inviteCode) {
        runCompanyAuth(email, password, () -> {
            LoginResult result = firebaseAuthService.signIn(email, password);
            if (!result.isSuccess()) {
                throw new IllegalStateException(result.getErrorMessage());
            }

            CompanyService.CompanySession company = companyService.acceptInvite(inviteCode, result, email);
            saveSession(result, email, company);
            Platform.runLater(() -> loginSuccess.set(true));
        });
    }

    public void createInvite(String email, String password, String companyName, String inviteEmail) {
        runCompanyAuth(email, password, () -> {
            LoginResult result = firebaseAuthService.signIn(email, password);
            if (!result.isSuccess()) {
                throw new IllegalStateException(result.getErrorMessage());
            }

            String inviteCode = companyService.createInvite(companyName, inviteEmail, result, email);
            Platform.runLater(() -> infoMessage.set("Invite code: " + inviteCode));
        });
    }

    private void saveSession(LoginResult result, String email, CompanyService.CompanySession company) {
        auth.saveAuthResult(result.getIdToken(), result.getRefreshToken(), result.getUid(), email);
        auth.saveCompany(company.companyId(), company.companyName(), company.role());
    }

    private void runCompanyAuth(String email, String password, LoginAction action){
        loading.set(true);
        errorMessage.set(null); // set to null so that listener in LoginController sees change
        infoMessage.set(null);

        executor.execute(() -> { // using different thread for login so that the ui doesnt hang
            try {
                action.run();
                Platform.runLater(() -> loading.set(false));
            } catch (Exception e) {
                Platform.runLater(() -> {
                    loading.set(false);
                    errorMessage.set("An error occurred: " + e.getMessage());
                });
            }
        });
    }

    @FunctionalInterface
    private interface LoginAction {
        void run() throws Exception;
    }
}
