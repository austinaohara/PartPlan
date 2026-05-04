package service.auth;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import service.config.FirebaseProjectConfig;
import service.config.FirebaseProjectConfigStore;
import service.session.SessionManager;
import service.session.UserSession;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public class FirebaseAuthService implements AuthService {
    private static final String IDENTITY_TOOLKIT_BASE = "https://identitytoolkit.googleapis.com/v1/accounts:";
    private static final String SECURE_TOKEN_BASE = "https://securetoken.googleapis.com/v1/token";

    private final SessionManager sessionManager;
    private final FirebaseProjectConfigStore configStore;
    private final HttpClient httpClient;
    private final Gson gson;

    public FirebaseAuthService(SessionManager sessionManager, FirebaseProjectConfigStore configStore) {
        this(sessionManager, configStore, HttpClient.newHttpClient(), new Gson());
    }

    FirebaseAuthService(
            SessionManager sessionManager,
            FirebaseProjectConfigStore configStore,
            HttpClient httpClient,
            Gson gson
    ) {
        this.sessionManager = sessionManager;
        this.configStore = configStore;
        this.httpClient = httpClient;
        this.gson = gson;
    }

    @Override
    public boolean isConfigured() {
        try {
            return configStore.load().isPresent();
        } catch (RuntimeException exception) {
            return false;
        }
    }

    @Override
    public UserSession signIn(String email, String password) {
        AuthResponse response = sendJsonAuthRequest("signInWithPassword", email, password);
        return persistSession(response.localId, response.email, response.idToken, response.refreshToken, response.expiresIn);
    }

    @Override
    public UserSession signUp(String email, String password) {
        AuthResponse response = sendJsonAuthRequest("signUp", email, password);
        return persistSession(response.localId, response.email, response.idToken, response.refreshToken, response.expiresIn);
    }

    @Override
    public UserSession refreshSession(UserSession session) {
        if (session == null || session.getRefreshToken() == null || session.getRefreshToken().isBlank()) {
            throw new AuthenticationException("The saved session cannot be refreshed.");
        }

        FirebaseProjectConfig config = requireConfig();
        String encodedBody = "grant_type=refresh_token&refresh_token="
                + URLEncoder.encode(session.getRefreshToken(), StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder(URI.create(SECURE_TOKEN_BASE + "?key=" + config.apiKey()))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(encodedBody))
                .build();

        RefreshResponse response = sendRequest(request, RefreshResponse.class);
        String email = session.getEmail();
        return persistSession(response.userId, email, response.idToken, response.refreshToken, response.expiresIn);
    }

    @Override
    public void signOut() {
        sessionManager.clearCurrentSession();
    }

    private AuthResponse sendJsonAuthRequest(String endpoint, String email, String password) {
        FirebaseProjectConfig config = requireConfig();
        validateCredentials(email, password);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("email", email.trim());
        payload.put("password", password);
        payload.put("returnSecureToken", true);

        HttpRequest request = HttpRequest.newBuilder(URI.create(IDENTITY_TOOLKIT_BASE + endpoint + "?key=" + config.apiKey()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload)))
                .build();

        return sendRequest(request, AuthResponse.class);
    }

    private <T> T sendRequest(HttpRequest request, Class<T> responseType) {
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException exception) {
            throw new AuthenticationException("Unable to reach Firebase. Check your network connection.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AuthenticationException("Firebase authentication was interrupted.", exception);
        }

        if (response.statusCode() >= 400) {
            throw new AuthenticationException(parseFirebaseError(response.body()));
        }

        try {
            return gson.fromJson(response.body(), responseType);
        } catch (RuntimeException exception) {
            throw new AuthenticationException("Firebase returned an unreadable response.", exception);
        }
    }

    private UserSession persistSession(
            String uid,
            String email,
            String idToken,
            String refreshToken,
            String expiresInSeconds
    ) {
        long ttlSeconds = parseExpiry(expiresInSeconds);
        UserSession session = new UserSession(
                uid,
                email,
                idToken,
                refreshToken,
                Instant.now().plusSeconds(ttlSeconds)
        );
        sessionManager.setCurrentSession(session);
        return session;
    }

    private FirebaseProjectConfig requireConfig() {
        return configStore.load()
                .filter(FirebaseProjectConfig::isComplete)
                .orElseThrow(() -> new AuthenticationException("Firebase project settings are missing or incomplete."));
    }

    private void validateCredentials(String email, String password) {
        if (email == null || email.isBlank()) {
            throw new AuthenticationException("Email is required.");
        }
        if (password == null || password.isBlank()) {
            throw new AuthenticationException("Password is required.");
        }
    }

    private long parseExpiry(String expiresInSeconds) {
        try {
            return Long.parseLong(expiresInSeconds);
        } catch (RuntimeException exception) {
            throw new AuthenticationException("Firebase did not provide a valid session expiry.", exception);
        }
    }

    private String parseFirebaseError(String responseBody) {
        try {
            ErrorEnvelope errorEnvelope = gson.fromJson(responseBody, ErrorEnvelope.class);
            if (errorEnvelope != null && errorEnvelope.error != null && errorEnvelope.error.message != null) {
                return switch (errorEnvelope.error.message) {
                    case "EMAIL_EXISTS" -> "That email address already has an account.";
                    case "EMAIL_NOT_FOUND" -> "No account exists for that email address.";
                    case "INVALID_PASSWORD", "INVALID_LOGIN_CREDENTIALS" -> "The email or password is incorrect.";
                    case "USER_DISABLED" -> "This Firebase account has been disabled.";
                    case "INVALID_REFRESH_TOKEN", "TOKEN_EXPIRED" -> "The saved session has expired. Sign in again.";
                    case "CONFIGURATION_NOT_FOUND" -> "Firebase Authentication is not configured for this project.";
                    default -> "Firebase authentication failed: " + errorEnvelope.error.message;
                };
            }
        } catch (RuntimeException ignored) {
            // Fall through to generic message below.
        }
        return "Firebase authentication failed.";
    }

    private static class AuthResponse {
        String localId;
        String email;
        String idToken;
        String refreshToken;
        String expiresIn;
    }

    private static class RefreshResponse {
        @SerializedName("user_id")
        String userId;
        @SerializedName("id_token")
        String idToken;
        @SerializedName("refresh_token")
        String refreshToken;
        @SerializedName("expires_in")
        String expiresIn;
    }

    private static class ErrorEnvelope {
        ApiError error;
    }

    private static class ApiError {
        String message;
    }
}
