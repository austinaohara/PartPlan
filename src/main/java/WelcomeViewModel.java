import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import repository.AuthRepository;
import service.FirebaseAuthService;

import java.io.IOException;

public class WelcomeViewModel {
    private final FirebaseAuthService firebaseAuthService = new FirebaseAuthService();
    private final AuthRepository authRepository = new AuthRepository();

    public BooleanProperty isSignedIn = new SimpleBooleanProperty(false);

    public WelcomeViewModel() throws IOException {
    }

    public void signOut(){
        authRepository.clear();
        refreshSignInStatus();
    }

    public boolean isSessionValid(){
        return authRepository.isSessionValid(firebaseAuthService);
    }

    public void refreshSignInStatus(){
        isSignedIn.set(authRepository.isSessionValid(firebaseAuthService));
    }

    public String getUid(){
        return authRepository.getUid();
    }
}
