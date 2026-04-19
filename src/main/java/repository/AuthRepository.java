package repository;

import java.util.prefs.Preferences;

public class AuthRepository {
    private static final Preferences prefs = Preferences.userRoot().node("partplan/auth");

    public void saveAuthResult(String idToken, String refreshToken, String uid) {
        prefs.put("idToken", idToken);
        prefs.put("refreshToken", refreshToken);
        prefs.put("uid", uid);
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

    public void clear(){
        prefs.remove("idToken");
        prefs.remove("refreshToken");
        prefs.remove("uid");
    }
}
