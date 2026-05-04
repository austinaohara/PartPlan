package service.session;

import java.util.Optional;

public class InMemorySessionManager implements SessionManager {
    private UserSession currentSession;

    @Override
    public Optional<UserSession> getCurrentSession() {
        return Optional.ofNullable(currentSession);
    }

    @Override
    public UserSession requireCurrentSession() {
        if (currentSession == null) {
            throw new IllegalStateException("No active user session is available.");
        }
        return currentSession;
    }

    @Override
    public void setCurrentSession(UserSession session) {
        if (session == null) {
            throw new IllegalArgumentException("session must not be null");
        }
        currentSession = session;
    }

    @Override
    public void clearCurrentSession() {
        currentSession = null;
    }
}
