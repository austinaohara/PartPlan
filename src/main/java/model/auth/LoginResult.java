package model.auth;

public class LoginResult {
    private final String idToken;
    private final String refreshToken;
    private final String uid;
    private final String errorMessage;
    private final boolean success;

    private LoginResult(Builder builder) {
        this.idToken = builder.idToken;
        this.refreshToken = builder.refreshToken;
        this.uid = builder.uid;
        this.errorMessage = builder.errorMessage;
        this.success = builder.success;
    }

    public static LoginResult success(String idToken, String refreshToken, String uid) {
        return new Builder()
                .success(true)
                .idToken(idToken)
                .refreshToken(refreshToken)
                .uid(uid)
                .build();
    }

    public static LoginResult failure(String errorMessage) {
        return new Builder()
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }

    public String getIdToken() { return idToken; }
    public String getRefreshToken() { return refreshToken; }
    public String getUid() { return uid; }
    public String getErrorMessage() { return errorMessage; }
    public boolean isSuccess() { return success; }

    public static class Builder {
        private String idToken;
        private String refreshToken;
        private String uid;
        private String errorMessage;
        private boolean success;

        public Builder idToken(String idToken){
            this.idToken = idToken;
            return this;
        }

        public Builder refreshToken(String refreshToken){
            this.refreshToken = refreshToken;
            return this;
        }

        public Builder uid(String uid){
            this.uid = uid;
            return this;
        }

        public Builder errorMessage(String errorMessage){
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder success(boolean success){
            this.success = success;
            return this;
        }

        public LoginResult build() {
            return new LoginResult(this);
        }
    }
}
