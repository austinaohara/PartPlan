package viewmodel;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import model.auth.LoginResult;
import repository.AuthRepository;
import service.FirebaseAuthService;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class LoginViewModel {
    private final AuthRepository auth = new AuthRepository();
    private final FirebaseAuthService firebaseAuthService = new FirebaseAuthService();
    private final Executor executor = Executors.newSingleThreadExecutor();

    public final StringProperty errorMessage = new SimpleStringProperty();
    public final BooleanProperty loginSuccess = new SimpleBooleanProperty(false);
    public final BooleanProperty loading = new SimpleBooleanProperty(false);

    public LoginViewModel() throws IOException {
    }

    public void login(String email, String password){
        loading.set(true);
        errorMessage.set(null); // set to null so that listener in LoginController sees change

        executor.execute(() -> { // using different thread for login
            try {
                LoginResult result = firebaseAuthService.signIn(email, password);
                Platform.runLater(() -> {
                    if (result.isSuccess()) {
                        auth.saveToken(result.getIdToken());
                        loginSuccess.set(true);
                    } else {
                        errorMessage.set(result.getErrorMessage());
                    }
                    loading.set(false);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    loading.set(false);
                    errorMessage.set("An error occurred: " + e.getMessage());
                });
            }
        });
    }
}
