package repository;

import java.util.prefs.Preferences;

public class AuthRepository {
    private static final Preferences prefs = Preferences.userRoot().node("com/partplan/auth");

    public void saveToken(String token){
        prefs.put("idToken", token);
    }

    public String getToken(){
        return prefs.get("idToken", null);
    }

    public void clearToken(){
        prefs.remove("idToken");
    }
}
