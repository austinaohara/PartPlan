package service.config;

import app.AppStoragePaths;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

public class FileBackedAutoBalloonConfigStore implements AutoBalloonConfigStore {
    private static final String API_KEY = "apiKey";
    private static final String MODEL = "model";

    private final Path configPath;

    public FileBackedAutoBalloonConfigStore() {
        this(AppStoragePaths.autoBalloonConfigPath());
    }

    FileBackedAutoBalloonConfigStore(Path configPath) {
        this.configPath = configPath;
    }

    @Override
    public Optional<AutoBalloonConfig> load() {
        if (Files.notExists(configPath)) {
            return Optional.empty();
        }

        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(configPath)) {
            properties.load(inputStream);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load auto-balloon settings.", exception);
        }

        AutoBalloonConfig config = new AutoBalloonConfig(
                properties.getProperty(API_KEY),
                properties.getProperty(MODEL)
        );
        return config.isComplete() ? Optional.of(config) : Optional.empty();
    }

    @Override
    public void save(AutoBalloonConfig config) {
        if (config == null || !config.isComplete()) {
            throw new IllegalArgumentException("Auto-balloon settings are incomplete.");
        }

        Properties properties = new Properties();
        properties.setProperty(API_KEY, config.apiKey());
        properties.setProperty(MODEL, config.resolvedModel());

        try {
            Files.createDirectories(configPath.getParent());
            try (OutputStream outputStream = Files.newOutputStream(configPath)) {
                properties.store(outputStream, "PartPlan OpenAI auto-balloon settings");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to save auto-balloon settings.", exception);
        }
    }

    @Override
    public void clear() {
        try {
            Files.deleteIfExists(configPath);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to clear auto-balloon settings.", exception);
        }
    }
}
