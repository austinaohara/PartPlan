package service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import model.auth.LoginResult;
import okhttp3.*;

import java.io.File;
import java.io.IOException;

public class FirebaseAuthService {
    private static final String AUTH_URL = "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=";
    private static final String REFRESH_URL = "https://securetoken.googleapis.com/v1/token?key=";
    private static final MediaType JSON_MEDIA = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String apiKey;

    public FirebaseAuthService() throws IOException {
        File configFile = new File(".env/firebaseConfig.json");
        if (!configFile.exists()) {
            throw new IOException("Firebase config file missing at: " + configFile.getAbsolutePath());
        }

        JsonNode root = mapper.readTree(configFile);
        this.apiKey = root.get("apiKey").asText();
    }

    public LoginResult signIn(String email, String password) {
        try {
            ObjectNode obj = mapper.createObjectNode();
            obj.put("email", email);
            obj.put("password", password);
            obj.put("returnSecureToken", true);

            Request request = new Request.Builder()
                    .url(AUTH_URL + apiKey)
                    .post(RequestBody.create(obj.toString(), JSON_MEDIA))
                    .build();

            return executeAuthRequest(request, json -> parseSignInResponse(json));
        } catch (Exception e) {
            return LoginResult.failure("Network error: " + e.getMessage());
        }
    }

    private LoginResult parseSignInResponse(JsonNode json) {
        return new LoginResult.Builder()
                .success(true)
                .idToken(json.get("idToken").asText())
                .refreshToken(json.get("refreshToken").asText())
                .uid(json.get("localId").asText())
                .build();
    }

    private LoginResult parseRefreshResponse(JsonNode json) {
        return new LoginResult.Builder()
                .success(true)
                .idToken(json.get("id_token").asText())
                .refreshToken(json.get("refresh_token").asText())
                .uid(json.get("user_id").asText())
                .build();
    }

    public LoginResult refreshToken(String refreshToken) {
        try {
            RequestBody body = new FormBody.Builder()
                    .add("grant_type", "refresh_token")
                    .add("refresh_token", refreshToken)
                    .build();

            Request request = new Request.Builder()
                    .url(REFRESH_URL + apiKey)
                    .post(body)
                    .build();

            return executeAuthRequest(request, json -> parseRefreshResponse(json));
        } catch (Exception e) {
            return LoginResult.failure("Refresh error: " + e.getMessage());
        }
    }

    @FunctionalInterface
    private interface ResponseParser {
        LoginResult parse(JsonNode json);
    }

    private LoginResult executeAuthRequest(Request request, ResponseParser parser) throws IOException {
        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            JsonNode json = mapper.readTree(responseBody);
            if (!response.isSuccessful()) {
                String message = json.path("error").path("message").asText("Authentication failed");
                return new LoginResult.Builder().success(false).errorMessage(message).build();
            }
            return parser.parse(json);
        }
    }

    public boolean isTokenValid(String idToken) {
        try {
            ObjectNode obj = mapper.createObjectNode();
            obj.put("idToken", idToken);

            Request request = new Request.Builder()
                    .url("https://identitytoolkit.googleapis.com/v1/accounts:lookup?key=" + apiKey)
                    .post(RequestBody.create(obj.toString(), JSON_MEDIA))
                    .build();

            try (Response response = client.newCall(request).execute()) {
                String body = response.body().string();
                JsonNode json = mapper.readTree(body);

                if (!response.isSuccessful()) return false;

                JsonNode user = json.path("users").get(0);
                return !user.path("disabled").asBoolean(false);
            }
        } catch (Exception e) {
            return false;
        }
    }
}
