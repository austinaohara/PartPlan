package app;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

import java.util.Optional;

public final class UnsavedChangesDialogs {
    private UnsavedChangesDialogs() {
    }

    public static boolean confirmDiscard(String itemLabel, String actionLabel) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Unsaved Changes");
        alert.setHeaderText("Discard unsaved changes?");
        alert.setContentText("""
                You have unsaved changes to this %s.

                Continue and %s without saving?
                """.formatted(itemLabel, actionLabel));
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }
}
