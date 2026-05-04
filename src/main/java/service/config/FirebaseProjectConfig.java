package service.config;

public record FirebaseProjectConfig(
        String apiKey,
        String projectId,
        String appId,
        String storageBucket,
        String authDomain,
        String databaseId
) {
    public static final String DEFAULT_DATABASE_ID = "(default)";

    public FirebaseProjectConfig {
        apiKey = normalize(apiKey);
        projectId = normalize(projectId);
        appId = normalize(appId);
        storageBucket = normalize(storageBucket);
        authDomain = normalize(authDomain);
        databaseId = normalize(databaseId);
    }

    public boolean isComplete() {
        return !apiKey.isBlank()
                && !projectId.isBlank()
                && !appId.isBlank()
                && !storageBucket.isBlank();
    }

    public String resolvedDatabaseId() {
        return databaseId.isBlank() ? DEFAULT_DATABASE_ID : databaseId;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
