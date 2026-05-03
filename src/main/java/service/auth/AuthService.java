package service.auth;

import service.session.UserSession;

public interface AuthService {
    UserSession signIn(String email, String password);

    UserSession signUp(String email, String password);

    void signOut();
}
