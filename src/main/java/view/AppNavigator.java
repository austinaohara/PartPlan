package view;

import app.AppContext;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;

public final class AppNavigator {
    private static AppContext appContext;

    private AppNavigator() {
    }

    public static void initialize(AppContext context) {
        appContext = Objects.requireNonNull(context, "context must not be null");
    }

    public static FXMLLoader createLoader(String fxmlPath) {
        if (appContext == null) {
            throw new IllegalStateException("AppNavigator has not been initialized.");
        }

        FXMLLoader loader = new FXMLLoader(AppNavigator.class.getResource(fxmlPath));
        loader.setControllerFactory(appContext::createController);
        return loader;
    }

    public static void swapRoot(Node source, String fxmlPath, String title) throws IOException {
        swapRoot(source, fxmlPath, title, null);
    }

    public static void swapRoot(Node source, String fxmlPath, String title, Consumer<FXMLLoader> onLoaded) throws IOException {
        FXMLLoader loader = createLoader(fxmlPath);
        Parent root = loader.load();
        if (onLoaded != null) {
            onLoaded.accept(loader);
        }

        Stage stage = (Stage) source.getScene().getWindow();
        stage.setTitle(title);
        source.getScene().setRoot(root);
    }
}
