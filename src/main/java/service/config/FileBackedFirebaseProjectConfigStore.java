package service.config;

import app.AppStoragePaths;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

public class FileBackedFirebaseProjectConfigStore implements FirebaseProjectConfigStore {
    private static final String API_KEY = "apiKey";
    private static final String PROJECT_ID = "projectId";
    private static final String APP_ID = "appId";
    private static final String STORAGE_BUCKET = "storageBucket";
    private static final String AUTH_DOMAIN = "authDomain";

    private final Path configPath;

    public FileBackedFirebaseProjectConfigStore() {
        this(defaultConfigPath());
    }

    FileBackedFirebaseProjectConfigStore(Path configPath) {
        this.configPath = configPath;
    }

    @Override
    public Optional<FirebaseProjectConfig> load() {
        if (Files.notExists(configPath)) {
            return Optional.empty();
        }

        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(configPath)) {
            properties.load(inputStream);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load Firebase project configuration.", exception);
        }

        FirebaseProjectConfig config = new FirebaseProjectConfig(
                properties.getProperty(API_KEY),
                properties.getProperty(PROJECT_ID),
                properties.getProperty(APP_ID),
                properties.getProperty(STORAGE_BUCKET),
                properties.getProperty(AUTH_DOMAIN)
        );

        return config.isComplete() ? Optional.of(config) : Optional.empty();
    }

    @Override
    public void save(FirebaseProjectConfig config) {
        if (config == null || !config.isComplete()) {
            throw new IllegalArgumentException("Firebase project configuration is incomplete.");
        }

        Properties properties = new Properties();
        properties.setProperty(API_KEY, config.apiKey());
        properties.setProperty(PROJECT_ID, config.projectId());
        properties.setProperty(APP_ID, config.appId());
        properties.setProperty(STORAGE_BUCKET, config.storageBucket());
        properties.setProperty(AUTH_DOMAIN, config.authDomain());

        try {
            Files.createDirectories(configPath.getParent());
            try (OutputStream outputStream = Files.newOutputStream(configPath)) {
                properties.store(outputStream, "PartPlan Firebase project settings");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to save Firebase project configuration.", exception);
        }
    }

    @Override
    public void clear() {
        try {
            Files.deleteIfExists(configPath);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to clear Firebase project configuration.", exception);
        }
    }

    private static Path defaultConfigPath() {
        return AppStoragePaths.firebaseConfigPath();
    }
}
