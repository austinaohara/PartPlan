package service.config;

public record FirebaseProjectConfig(
        String apiKey,
        String projectId,
        String appId,
        String storageBucket,
        String authDomain
) {
    public FirebaseProjectConfig {
        apiKey = normalize(apiKey);
        projectId = normalize(projectId);
        appId = normalize(appId);
        storageBucket = normalize(storageBucket);
        authDomain = normalize(authDomain);
    }

    public boolean isComplete() {
        return !apiKey.isBlank()
                && !projectId.isBlank()
                && !appId.isBlank()
                && !storageBucket.isBlank();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
