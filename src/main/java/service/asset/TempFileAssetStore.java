package service.asset;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class TempFileAssetStore implements AssetStore {
    private final Path rootDirectory;

    public TempFileAssetStore() {
        try {
            rootDirectory = Files.createTempDirectory("partplan-assets-");
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to create temporary asset directory.", exception);
        }
    }

    @Override
    public Path createImportDirectory(String planId, String sourceName) {
        String normalizedPlanId = sanitize(planId);
        String normalizedSourceName = sanitize(sourceName);
        Path directory = rootDirectory.resolve(normalizedPlanId)
                .resolve(normalizedSourceName + "-" + System.nanoTime());
        try {
            Files.createDirectories(directory);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to create temporary import directory.", exception);
        }
        return directory;
    }

    private String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "default";
        }
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
