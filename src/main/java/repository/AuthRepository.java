package repository;

import model.auth.LoginResult;
import service.FirebaseAuthService;

import java.util.Base64;
import java.util.prefs.Preferences;

public class AuthRepository {
    private static final Preferences prefs = Preferences.userRoot().node("partplan/auth");

    public void saveAuthResult(String idToken, String refreshToken, String uid) {
        prefs.put("idToken", idToken);
        prefs.put("refreshToken", refreshToken);
        prefs.put("uid", uid);
        prefs.putLong("tokenExpiry", extractExpiry(idToken));
    }

    private long extractExpiry(String idToken){
        try {
            String[] parts = idToken.split("\\.");
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));

            String expValue = payload.split("\"exp\":")[1].split("[,}]")[0].trim();
            return Long.parseLong(expValue) * 1000L;
        } catch (Exception e){
            return 0L;
        }

    }

    public static String getToken(){
        return prefs.get("idToken", null);
    }

    public static String getUid(){
        return prefs.get("uid", null);
    }

    public static String getRefreshToken(){
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

    public static void clear(){
        prefs.remove("idToken");
        prefs.remove("refreshToken");
        prefs.remove("uid");
    }
}
