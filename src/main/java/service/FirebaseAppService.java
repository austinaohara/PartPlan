package service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;

public final class FirebaseAppService {
    private static final Path CONFIG_PATH = Path.of(".env", "firebaseConfig.json");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private FirebaseAppService() {}

    public static FirebaseConfig loadConfig() {
        try {
            JsonNode config = MAPPER.readTree(CONFIG_PATH.toFile());
            return new FirebaseConfig(
                    trimTrailingSlash(config.path("databaseURL").asText()),
                    config.path("storageBucket").asText()
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read Firebase configuration.", exception);
        }
    }

    private static String trimTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    public record FirebaseConfig(String databaseUrl, String storageBucket) {
    }
}
