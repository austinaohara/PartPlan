package service.asset;

import java.nio.file.Path;

public interface AssetStore {
    Path createImportDirectory(String planId, String sourceName);
}
