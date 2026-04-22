package view;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.function.Consumer;

public final class AppNavigator {
    private AppNavigator() {
    }

    public static void swapRoot(Node source, String fxmlPath, String title) throws IOException {
        swapRoot(source, fxmlPath, title, null);
    }

    public static void swapRoot(Node source, String fxmlPath, String title, Consumer<FXMLLoader> onLoaded) throws IOException {
        FXMLLoader loader = new FXMLLoader(AppNavigator.class.getResource(fxmlPath));
        Parent root = loader.load();
        if (onLoaded != null) {
            onLoaded.accept(loader);
        }

        Stage stage = (Stage) source.getScene().getWindow();
        stage.setTitle(title);
        source.getScene().setRoot(root);
    }
}
