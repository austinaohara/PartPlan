package repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import model.auth.LoginResult;
import service.FirebaseAuthService;

import java.util.Base64;
import java.util.prefs.Preferences;

public class AuthRepository {
    private static final Preferences prefs = Preferences.userRoot().node("partplan/auth");

    private final ObjectMapper mapper = new ObjectMapper();

    public void saveAuthResult(String idToken, String refreshToken, String uid) {
        prefs.put("idToken", idToken);
        prefs.put("refreshToken", refreshToken);
        prefs.put("uid", uid);
        prefs.putLong("tokenExpiry", extractExpiry(idToken));
    }

    private long extractExpiry(String idToken) {
        try {
            String payload = new String(Base64.getUrlDecoder().decode(idToken.split("\\.")[1]));
            JsonNode node = mapper.readTree(payload);
            return node.get("exp").asLong() * 1000L;
        } catch (Exception e) {
            return 0L;
        }
    }

    public String getToken(){
        return prefs.get("idToken", null);
    }

    public String getUid(){
        return prefs.get("uid", null);
    }

    public String getRefreshToken(){
        return prefs.get("refreshToken", null);
    }

    public String getValidToken(FirebaseAuthService authService) throws Exception {
        if (!isTokenExpired()) {
            return getToken();
        }

        String storedRefreshToken = getRefreshToken();
        if (storedRefreshToken == null) throw new IllegalStateException("Not logged in");

        LoginResult result = authService.refreshToken(storedRefreshToken);
        if (!result.isSuccess()) throw new IllegalStateException("Session expired, please log in again");

        saveAuthResult(result.getIdToken(), result.getRefreshToken(), result.getUid());
        return result.getIdToken();
    }

    public static boolean isTokenExpired() {
        long expiry = prefs.getLong("tokenExpiry", 0L);
        return System.currentTimeMillis() > expiry - 60_000L; // 60s buffer
    }

    public boolean isSessionValid(FirebaseAuthService authService) {
        String token = getToken();
        if (token == null) return false;
        if (isTokenExpired()) return false;
        return authService.isTokenValid(token);
    }

    public static void clear(){
        prefs.remove("idToken");
        prefs.remove("refreshToken");
        prefs.remove("uid");
    }
}
