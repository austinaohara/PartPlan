package app;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import view.AppNavigator;

import java.io.IOException;

public final class AppMenuSupport {
    private static final String MENU_INSTALLED_KEY = "app.menu.installed";

    private AppMenuSupport() {
    }

    public static void install(BorderPane rootPane, MenuContext menuContext, MenuCallbacks callbacks) {
        if (rootPane == null || rootPane.getProperties().containsKey(MENU_INSTALLED_KEY)) {
            return;
        }

        MenuBar menuBar = buildMenuBar(
                menuContext == null ? MenuContext.GENERAL : menuContext,
                callbacks == null ? MenuCallbacks.empty() : callbacks
        );
        Object existingTop = rootPane.getTop();
        if (existingTop == null) {
            rootPane.setTop(menuBar);
        } else if (existingTop instanceof Node topNode) {
            VBox topContainer = new VBox(menuBar, topNode);
            rootPane.setTop(topContainer);
        }
        rootPane.getProperties().put(MENU_INSTALLED_KEY, Boolean.TRUE);
    }

    public static void openOpenAiSettingsWindow(Node ownerNode) {
        if (ownerNode == null || ownerNode.getScene() == null) {
            return;
        }
        openOpenAiSettingsWindow(ownerNode.getScene().getWindow());
    }

    public static void openOpenAiSettingsWindow(Window ownerWindow) {
        try {
            FXMLLoader loader = AppNavigator.createLoader("/fxml/auto-balloon-config.fxml");
            Parent settingsRoot = loader.load();
            Stage stage = new Stage();
            stage.setTitle("PartPlan - OpenAI Settings");
            stage.setScene(new Scene(settingsRoot));
            stage.initModality(Modality.NONE);
            if (ownerWindow != null) {
                stage.initOwner(ownerWindow);
            }
            stage.setResizable(false);
            stage.show();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to open the OpenAI settings window.", exception);
        }
    }

    private static MenuBar buildMenuBar(MenuContext menuContext, MenuCallbacks callbacks) {
        MenuBar menuBar = new MenuBar();
        menuBar.getMenus().add(buildFileMenu(callbacks));
        menuBar.getMenus().add(buildEditMenu());
        if (menuContext.includesViewMenu()) {
            menuBar.getMenus().add(buildViewMenu());
        }
        if (menuContext.includesPlanMenu()) {
            menuBar.getMenus().add(buildPlanMenu());
        }
        if (menuContext.includesLotMenu()) {
            menuBar.getMenus().add(buildLotMenu());
        }
        menuBar.getMenus().add(buildToolsMenu(callbacks));
        menuBar.getMenus().add(buildHelpMenu());
        return menuBar;
    }

    private static Menu buildFileMenu(MenuCallbacks callbacks) {
        Menu fileMenu = new Menu("File");
        fileMenu.getItems().addAll(
                disabledItem("New Plan"),
                disabledItem("Open Plan..."),
                disabledItem("Open Inspection Lots"),
                new SeparatorMenuItem(),
                disabledItem("Save"),
                disabledItem("Save As Revision"),
                disabledItem("Import Drawing/Page..."),
                exportMenu(),
                new SeparatorMenuItem(),
                actionItem("Sign Out", callbacks.onSignOut()),
                actionItem("Exit", Platform::exit)
        );
        return fileMenu;
    }

    private static Menu buildEditMenu() {
        Menu editMenu = new Menu("Edit");
        editMenu.getItems().addAll(
                disabledItem("Undo"),
                disabledItem("Redo"),
                new SeparatorMenuItem(),
                disabledItem("Copy"),
                disabledItem("Delete"),
                disabledItem("Find..."),
                disabledItem("Clear Selection")
        );
        return editMenu;
    }

    private static Menu buildViewMenu() {
        Menu viewMenu = new Menu("View");
        viewMenu.getItems().addAll(
                disabledItem("Saved Plans Panel"),
                disabledItem("Bubble Data Panel"),
                new SeparatorMenuItem(),
                disabledItem("Zoom In"),
                disabledItem("Zoom Out"),
                disabledItem("Reset Zoom"),
                disabledItem("Fit to Page")
        );
        return viewMenu;
    }

    private static Menu buildPlanMenu() {
        Menu planMenu = new Menu("Plan");
        planMenu.getItems().addAll(
                disabledItem("Save Draft"),
                disabledItem("Open Plan..."),
                disabledItem("Delete Plan"),
                disabledItem("Complete Plan"),
                disabledItem("Create Revision"),
                new SeparatorMenuItem(),
                disabledItem("Open in Data Editor"),
                disabledItem("Auto-Balloon Page"),
                disabledItem("Next Page"),
                disabledItem("Previous Page")
        );
        return planMenu;
    }

    private static Menu buildLotMenu() {
        Menu lotMenu = new Menu("Lot");
        lotMenu.getItems().addAll(
                disabledItem("Create Lot"),
                disabledItem("Open Selected Lot"),
                disabledItem("Save Lot"),
                disabledItem("Delete Lot"),
                disabledItem("Upversion Lot"),
                new SeparatorMenuItem(),
                disabledItem("Previous Part"),
                disabledItem("Next Part"),
                disabledItem("Go To Part...")
        );
        return lotMenu;
    }

    private static Menu buildToolsMenu(MenuCallbacks callbacks) {
        Menu toolsMenu = new Menu("Tools");
        toolsMenu.getItems().addAll(
                actionItem("Firebase Settings...", callbacks.onOpenFirebaseSettings()),
                actionItem("OpenAI Settings...", callbacks.onOpenOpenAiSettings()),
                new SeparatorMenuItem(),
                disabledItem("Refresh Remote Data"),
                disabledItem("Reauthenticate")
        );
        return toolsMenu;
    }

    private static Menu buildHelpMenu() {
        Menu helpMenu = new Menu("Help");
        helpMenu.getItems().addAll(
                actionItem("Keyboard Shortcuts", () -> showPlaceholderDialog(
                        "Keyboard Shortcuts",
                        "Keyboard shortcuts are not wired into the shared menu yet."
                )),
                actionItem("Troubleshooting", () -> showPlaceholderDialog(
                        "Troubleshooting",
                        "Troubleshooting content is not written yet."
                )),
                new SeparatorMenuItem(),
                actionItem("About PartPlan", AppMenuSupport::showAboutDialog)
        );
        return helpMenu;
    }

    private static Menu exportMenu() {
        Menu exportMenu = new Menu("Export");
        exportMenu.getItems().addAll(
                disabledItem("CSV"),
                disabledItem("PDF")
        );
        return exportMenu;
    }

    private static MenuItem actionItem(String text, Runnable action) {
        MenuItem item = new MenuItem(text);
        if (action == null) {
            item.setDisable(true);
            return item;
        }
        item.setOnAction(event -> action.run());
        return item;
    }

    private static MenuItem disabledItem(String text) {
        MenuItem item = new MenuItem(text);
        item.setDisable(true);
        return item;
    }

    private static void showAboutDialog() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("About PartPlan");
        alert.setHeaderText("PartPlan");
        alert.setContentText("""
                PartPlan manages inspection plans, auto-ballooning, and lot measurement data.

                This shared menu is being rolled out in phases. Plan, lot, edit, and view actions will be wired next.
                """);
        alert.showAndWait();
    }

    private static void showPlaceholderDialog(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public record MenuCallbacks(
            Runnable onSignOut,
            Runnable onOpenFirebaseSettings,
            Runnable onOpenOpenAiSettings
    ) {
        public static MenuCallbacks empty() {
            return new MenuCallbacks(null, null, null);
        }
    }

    public enum MenuContext {
        GENERAL(false, false, false),
        PLAN_EDITOR(true, true, false),
        LOT_BROWSER(false, false, true),
        LOT_EDITOR(false, false, true);

        private final boolean includesViewMenu;
        private final boolean includesPlanMenu;
        private final boolean includesLotMenu;

        MenuContext(boolean includesViewMenu, boolean includesPlanMenu, boolean includesLotMenu) {
            this.includesViewMenu = includesViewMenu;
            this.includesPlanMenu = includesPlanMenu;
            this.includesLotMenu = includesLotMenu;
        }

        public boolean includesViewMenu() {
            return includesViewMenu;
        }

        public boolean includesPlanMenu() {
            return includesPlanMenu;
        }

        public boolean includesLotMenu() {
            return includesLotMenu;
        }
    }
}
