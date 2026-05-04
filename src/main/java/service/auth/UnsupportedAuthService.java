package service.auth;

import service.session.SessionManager;
import service.session.UserSession;

public class UnsupportedAuthService implements AuthService {
    private final SessionManager sessionManager;

    public UnsupportedAuthService(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    public boolean isConfigured() {
        return false;
    }

    @Override
    public UserSession signIn(String email, String password) {
        throw new UnsupportedOperationException("Authentication is not implemented yet.");
    }

    @Override
    public UserSession signUp(String email, String password) {
        throw new UnsupportedOperationException("Authentication is not implemented yet.");
    }

    @Override
    public UserSession refreshSession(UserSession session) {
        throw new UnsupportedOperationException("Authentication is not implemented yet.");
    }

    @Override
    public void signOut() {
        sessionManager.clearCurrentSession();
    }
}
