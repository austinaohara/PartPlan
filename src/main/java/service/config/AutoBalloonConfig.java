package service.config;

public record AutoBalloonConfig(
        String apiKey,
        String model
) {
    public static final String DEFAULT_MODEL = "gpt-5.4-mini";

    public AutoBalloonConfig {
        apiKey = normalize(apiKey);
        model = normalize(model);
    }

    public boolean isComplete() {
        return !apiKey.isBlank();
    }

    public String resolvedModel() {
        return model.isBlank() ? DEFAULT_MODEL : model;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
