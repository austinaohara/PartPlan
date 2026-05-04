import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import repository.AuthRepository;

import java.io.IOException;

public class WelcomeViewModel {
    private final AuthRepository authRepository = new AuthRepository();

    public BooleanProperty isSignedIn = new SimpleBooleanProperty(false);

    public WelcomeViewModel() throws IOException {
    }

    public void signOut(){
        authRepository.clear();
        refreshSignInStatus();
    }

    public boolean isSessionValid(){
        return authRepository.isSessionValid();
    }

    public void refreshSignInStatus(){
        isSignedIn.set(authRepository.isSessionValid());
    }

    public String getUsername(){
        return authRepository.getUsername();
    }
}
