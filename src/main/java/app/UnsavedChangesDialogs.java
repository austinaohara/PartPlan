package app;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;

import java.util.Optional;

public final class UnsavedChangesDialogs {
    private UnsavedChangesDialogs() {
    }

    public static SaveDecision promptToSaveDiscardOrCancel(String itemLabel, String actionLabel) {
        ButtonType saveButton = new ButtonType("Save");
        ButtonType discardButton = new ButtonType("Discard");
        ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Unsaved Changes");
        alert.setHeaderText("Save changes before continuing?");
        alert.setContentText("""
                You have unsaved changes to this %s.

                Do you want to save before you %s?
                """.formatted(itemLabel, actionLabel));
        alert.getButtonTypes().setAll(saveButton, discardButton, cancelButton);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isEmpty() || result.get() == cancelButton) {
            return SaveDecision.CANCEL;
        }
        if (result.get() == saveButton) {
            return SaveDecision.SAVE;
        }
        return SaveDecision.DISCARD;
    }

    public enum SaveDecision {
        SAVE,
        DISCARD,
        CANCEL
    }
}
