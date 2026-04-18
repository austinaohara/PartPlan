package model.auth;

public class LoginResult {
    private final String idToken;
    private final String errorMessage;
    private final boolean success;


    public LoginResult(String idToken, String errorMessage, boolean success) {
        this.idToken = idToken;
        this.errorMessage = errorMessage;
        this.success = success;
    }

    public static LoginResult success(String idToken){
        return new LoginResult(idToken, null, true);
    }

    public static LoginResult failure(String errorMessage){
        return new LoginResult(null, errorMessage, false);
    }

    public String getIdToken() {
        return idToken;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public boolean isSuccess() {
        return success;
    }
}
