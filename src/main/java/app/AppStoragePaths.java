package app;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class AppStoragePaths {
    private AppStoragePaths() {
    }

    public static Path appDataDirectory() {
        return Paths.get(System.getProperty("user.dir"), ".partplan").toAbsolutePath().normalize();
    }

    public static Path firebaseConfigPath() {
        return appDataDirectory().resolve("firebase.properties");
    }

    public static Path sessionPath() {
        return appDataDirectory().resolve("session.properties");
    }
}
