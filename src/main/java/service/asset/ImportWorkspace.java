package service.asset;

import java.nio.file.Path;

public interface ImportWorkspace {
    Path createImportDirectory(String planId, String sourceName);
}
