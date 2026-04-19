package model.auth;

public class LoginResult {
    private final String idToken;
    private final String refreshToken;
    private final String uid;
    private final String errorMessage;
    private final boolean success;

    public LoginResult(String idToken, String refreshToken, String uid, String errorMessage, boolean success) {
        this.idToken = idToken;
        this.refreshToken = refreshToken;
        this.uid = uid;
        this.errorMessage = errorMessage;
        this.success = success;
    }

    public static LoginResult success(String idToken, String refreshToken, String uid) {
        return new LoginResult(idToken, refreshToken, uid, null, true);
    }

    public static LoginResult failure(String errorMessage) {
        return new LoginResult(null, null, null, errorMessage, false);
    }

    public String getIdToken() { return idToken; }
    public String getRefreshToken() { return refreshToken; }
    public String getUid() { return uid; }
    public String getErrorMessage() { return errorMessage; }
    public boolean isSuccess() { return success; }
}
