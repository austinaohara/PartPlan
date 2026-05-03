package service.session;

import app.AppStoragePaths;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.Properties;

public class FileBackedSessionManager implements SessionManager {
    private static final String UID = "uid";
    private static final String EMAIL = "email";
    private static final String ID_TOKEN = "idToken";
    private static final String REFRESH_TOKEN = "refreshToken";
    private static final String EXPIRES_AT = "expiresAt";

    private final Path sessionPath;
    private UserSession currentSession;

    public FileBackedSessionManager() {
        this(defaultSessionPath());
    }

    FileBackedSessionManager(Path sessionPath) {
        this.sessionPath = sessionPath;
        this.currentSession = loadPersistedSession().orElse(null);
    }

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
        persistSession(session);
    }

    @Override
    public void clearCurrentSession() {
        currentSession = null;
        try {
            Files.deleteIfExists(sessionPath);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to clear the saved user session.", exception);
        }
    }

    private Optional<UserSession> loadPersistedSession() {
        if (Files.notExists(sessionPath)) {
            return Optional.empty();
        }

        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(sessionPath)) {
            properties.load(inputStream);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load the saved user session.", exception);
        }

        String uid = normalize(properties.getProperty(UID));
        if (uid.isBlank()) {
            return Optional.empty();
        }

        String email = normalize(properties.getProperty(EMAIL));
        String idToken = blankToNull(properties.getProperty(ID_TOKEN));
        String refreshToken = blankToNull(properties.getProperty(REFRESH_TOKEN));
        Instant expiresAt = parseInstant(properties.getProperty(EXPIRES_AT));

        return Optional.of(new UserSession(uid, email, idToken, refreshToken, expiresAt));
    }

    private void persistSession(UserSession session) {
        Properties properties = new Properties();
        properties.setProperty(UID, session.getUid());
        properties.setProperty(EMAIL, session.getEmail());
        properties.setProperty(ID_TOKEN, nullToBlank(session.getIdToken()));
        properties.setProperty(REFRESH_TOKEN, nullToBlank(session.getRefreshToken()));
        properties.setProperty(EXPIRES_AT, session.getExpiresAt() == null ? "" : session.getExpiresAt().toString());

        try {
            Files.createDirectories(sessionPath.getParent());
            try (OutputStream outputStream = Files.newOutputStream(sessionPath)) {
                properties.store(outputStream, "PartPlan user session");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to save the current user session.", exception);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static String blankToNull(String value) {
        return normalize(value).isBlank() ? null : value.trim();
    }

    private static String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private static Instant parseInstant(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            return null;
        }

        try {
            return Instant.parse(normalized);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static Path defaultSessionPath() {
        return AppStoragePaths.sessionPath();
    }
}
