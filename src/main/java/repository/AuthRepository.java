package repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import service.FirebaseAuthService;

import java.io.IOException;
import java.util.Base64;
import java.util.prefs.Preferences;

public class AuthRepository {
    private static final Preferences prefs = Preferences.userRoot().node("partplan/auth");
    private final FirebaseAuthService authService;

    private final ObjectMapper mapper = new ObjectMapper();

    public AuthRepository() throws IOException {
        this.authService = new FirebaseAuthService();
    }

    public void saveAuthResult(String idToken, String refreshToken, String uid, String email) {
        prefs.put("idToken", idToken);
        prefs.put("refreshToken", refreshToken);
        prefs.put("uid", uid);
        prefs.put("email", email);
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

    public static boolean isTokenExpired() {
        long expiry = prefs.getLong("tokenExpiry", 0L);
        return System.currentTimeMillis() > expiry - 60_000L; // 60s buffer
    }

    public boolean isSessionValid() {
        String token = getToken();
        if (token == null) return false;
        if (isTokenExpired()) return false;
        return authService.isTokenValid(token);
    }

    public void clear(){
        prefs.remove("idToken");
        prefs.remove("refreshToken");
        prefs.remove("uid");
    }

    public String getToken(){
        return prefs.get("idToken", null);
    }

    public String getRefreshToken(){
        return prefs.get("refreshToken", null);
    }

    public String getUid(){
        return prefs.get("uid", null);
    }

    public String getEmail() {
        return prefs.get("email", null);
    }

    public String getUsername() {
        String email = getEmail();
        if (email == null) return null;
        return email.substring(0, email.indexOf("@"));
    }


}
