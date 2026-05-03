package service.session;

import java.time.Instant;

public class UserSession {
    private final String uid;
    private final String email;
    private final String idToken;
    private final String refreshToken;
    private final Instant expiresAt;

    public UserSession(String uid, String email, String idToken, String refreshToken, Instant expiresAt) {
        this.uid = uid;
        this.email = email == null ? "" : email;
        this.idToken = idToken;
        this.refreshToken = refreshToken;
        this.expiresAt = expiresAt;
    }

    public static UserSession localSession(String uid) {
        return new UserSession(uid, "", null, null, null);
    }

    public String getUid() {
        return uid;
    }

    public String getEmail() {
        return email;
    }

    public String getIdToken() {
        return idToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(Instant.now());
    }
}
