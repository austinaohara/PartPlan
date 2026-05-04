package service.auth;

import service.session.UserSession;

public interface AuthService {
    boolean isConfigured();

    UserSession signIn(String email, String password);

    UserSession signUp(String email, String password);

    UserSession refreshSession(UserSession session);

    void signOut();
}
