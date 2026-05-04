package service.session;

import java.util.Optional;

public interface SessionManager {
    Optional<UserSession> getCurrentSession();

    UserSession requireCurrentSession();

    void setCurrentSession(UserSession session);

    void clearCurrentSession();
}
