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
import java.util.EnumMap;
import java.util.Map;

public final class AppMenuSupport {
    private static final String MENU_INSTALLED_KEY = "app.menu.installed";
    private static final String MENU_ITEMS_KEY = "app.menu.items";

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

    public static void bindAction(BorderPane rootPane, MenuAction action, Runnable handler) {
        if (rootPane == null || action == null) {
            return;
        }
        MenuItem menuItem = getRegisteredMenuItems(rootPane).get(action);
        if (menuItem == null) {
            return;
        }
        if (handler == null) {
            menuItem.setDisable(true);
            menuItem.setOnAction(null);
            return;
        }
        menuItem.setDisable(false);
        menuItem.setOnAction(event -> handler.run());
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
        Map<MenuAction, MenuItem> menuItems = new EnumMap<>(MenuAction.class);
        MenuBar menuBar = new MenuBar();
        menuBar.getProperties().put(MENU_ITEMS_KEY, menuItems);
        menuBar.getMenus().add(buildFileMenu(callbacks, menuItems));
        menuBar.getMenus().add(buildEditMenu(menuItems));
        if (menuContext.includesViewMenu()) {
            menuBar.getMenus().add(buildViewMenu(menuItems));
        }
        if (menuContext.includesPlanMenu()) {
            menuBar.getMenus().add(buildPlanMenu(menuItems));
        }
        if (menuContext.includesLotMenu()) {
            menuBar.getMenus().add(buildLotMenu(menuItems));
        }
        menuBar.getMenus().add(buildToolsMenu(callbacks, menuItems));
        menuBar.getMenus().add(buildHelpMenu());
        return menuBar;
    }

    private static Menu buildFileMenu(MenuCallbacks callbacks, Map<MenuAction, MenuItem> menuItems) {
        Menu fileMenu = new Menu("File");
        fileMenu.getItems().addAll(
                disabledItem(MenuAction.FILE_NEW_PLAN, "New Plan", menuItems),
                disabledItem(MenuAction.FILE_OPEN_PLAN, "Open Plan...", menuItems),
                disabledItem(MenuAction.FILE_OPEN_INSPECTION_LOTS, "Open Inspection Lots", menuItems),
                new SeparatorMenuItem(),
                disabledItem(MenuAction.FILE_SAVE, "Save", menuItems),
                disabledItem(MenuAction.FILE_SAVE_AS_REVISION, "Save As Revision", menuItems),
                disabledItem(MenuAction.FILE_IMPORT_DRAWING_PAGE, "Import Drawing/Page...", menuItems),
                exportMenu(menuItems),
                new SeparatorMenuItem(),
                actionItem(MenuAction.FILE_SIGN_OUT, "Sign Out", callbacks.onSignOut(), menuItems),
                actionItem("Exit", Platform::exit)
        );
        return fileMenu;
    }

    private static Menu buildEditMenu(Map<MenuAction, MenuItem> menuItems) {
        Menu editMenu = new Menu("Edit");
        editMenu.getItems().addAll(
                disabledItem(MenuAction.EDIT_UNDO, "Undo", menuItems),
                disabledItem(MenuAction.EDIT_REDO, "Redo", menuItems),
                new SeparatorMenuItem(),
                disabledItem(MenuAction.EDIT_COPY, "Copy", menuItems),
                disabledItem(MenuAction.EDIT_DELETE, "Delete", menuItems),
                disabledItem(MenuAction.EDIT_FIND, "Find...", menuItems),
                disabledItem(MenuAction.EDIT_CLEAR_SELECTION, "Clear Selection", menuItems)
        );
        return editMenu;
    }

    private static Menu buildViewMenu(Map<MenuAction, MenuItem> menuItems) {
        Menu viewMenu = new Menu("View");
        viewMenu.getItems().addAll(
                disabledItem(MenuAction.VIEW_SAVED_PLANS_PANEL, "Saved Plans Panel", menuItems),
                disabledItem(MenuAction.VIEW_BUBBLE_DATA_PANEL, "Bubble Data Panel", menuItems),
                new SeparatorMenuItem(),
                disabledItem(MenuAction.VIEW_ZOOM_IN, "Zoom In", menuItems),
                disabledItem(MenuAction.VIEW_ZOOM_OUT, "Zoom Out", menuItems),
                disabledItem(MenuAction.VIEW_RESET_ZOOM, "Reset Zoom", menuItems),
                disabledItem(MenuAction.VIEW_FIT_TO_PAGE, "Fit to Page", menuItems)
        );
        return viewMenu;
    }

    private static Menu buildPlanMenu(Map<MenuAction, MenuItem> menuItems) {
        Menu planMenu = new Menu("Plan");
        planMenu.getItems().addAll(
                disabledItem(MenuAction.PLAN_SAVE_DRAFT, "Save Draft", menuItems),
                disabledItem(MenuAction.PLAN_OPEN_PLAN, "Open Plan...", menuItems),
                disabledItem(MenuAction.PLAN_DELETE_PLAN, "Delete Plan", menuItems),
                disabledItem(MenuAction.PLAN_COMPLETE_PLAN, "Complete Plan", menuItems),
                disabledItem(MenuAction.PLAN_CREATE_REVISION, "Create Revision", menuItems),
                new SeparatorMenuItem(),
                disabledItem(MenuAction.PLAN_OPEN_DATA_EDITOR, "Open in Data Editor", menuItems),
                disabledItem(MenuAction.PLAN_AUTO_BALLOON_PAGE, "Auto-Balloon Page", menuItems),
                disabledItem(MenuAction.PLAN_NEXT_PAGE, "Next Page", menuItems),
                disabledItem(MenuAction.PLAN_PREVIOUS_PAGE, "Previous Page", menuItems)
        );
        return planMenu;
    }

    private static Menu buildLotMenu(Map<MenuAction, MenuItem> menuItems) {
        Menu lotMenu = new Menu("Lot");
        lotMenu.getItems().addAll(
                disabledItem(MenuAction.LOT_CREATE_LOT, "Create Lot", menuItems),
                disabledItem(MenuAction.LOT_OPEN_SELECTED_LOT, "Open Selected Lot", menuItems),
                disabledItem(MenuAction.LOT_SAVE_LOT, "Save Lot", menuItems),
                disabledItem(MenuAction.LOT_DELETE_LOT, "Delete Lot", menuItems),
                disabledItem(MenuAction.LOT_UPVERSION_LOT, "Upversion Lot", menuItems),
                new SeparatorMenuItem(),
                disabledItem(MenuAction.LOT_PREVIOUS_PART, "Previous Part", menuItems),
                disabledItem(MenuAction.LOT_NEXT_PART, "Next Part", menuItems),
                disabledItem(MenuAction.LOT_GO_TO_PART, "Go To Part...", menuItems)
        );
        return lotMenu;
    }

    private static Menu buildToolsMenu(MenuCallbacks callbacks, Map<MenuAction, MenuItem> menuItems) {
        Menu toolsMenu = new Menu("Tools");
        toolsMenu.getItems().addAll(
                actionItem(MenuAction.TOOLS_FIREBASE_SETTINGS, "Firebase Settings...", callbacks.onOpenFirebaseSettings(), menuItems),
                actionItem(MenuAction.TOOLS_OPENAI_SETTINGS, "OpenAI Settings...", callbacks.onOpenOpenAiSettings(), menuItems),
                new SeparatorMenuItem(),
                disabledItem(MenuAction.TOOLS_REFRESH_REMOTE_DATA, "Refresh Remote Data", menuItems),
                disabledItem(MenuAction.TOOLS_REAUTHENTICATE, "Reauthenticate", menuItems)
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

    private static Menu exportMenu(Map<MenuAction, MenuItem> menuItems) {
        Menu exportMenu = new Menu("Export");
        exportMenu.getItems().addAll(
                disabledItem(MenuAction.FILE_EXPORT_CSV, "CSV", menuItems),
                disabledItem(MenuAction.FILE_EXPORT_PDF, "PDF", menuItems)
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

    private static MenuItem actionItem(MenuAction action, String text, Runnable runnable, Map<MenuAction, MenuItem> menuItems) {
        MenuItem item = actionItem(text, runnable);
        registerMenuItem(action, item, menuItems);
        return item;
    }

    private static MenuItem disabledItem(MenuAction action, String text, Map<MenuAction, MenuItem> menuItems) {
        MenuItem item = new MenuItem(text);
        item.setDisable(true);
        registerMenuItem(action, item, menuItems);
        return item;
    }

    @SuppressWarnings("unchecked")
    private static Map<MenuAction, MenuItem> getRegisteredMenuItems(BorderPane rootPane) {
        Object top = rootPane.getTop();
        if (top instanceof MenuBar menuBar) {
            Object menuItems = menuBar.getProperties().get(MENU_ITEMS_KEY);
            if (menuItems instanceof Map<?, ?> map) {
                return (Map<MenuAction, MenuItem>) map;
            }
        }
        if (top instanceof VBox topContainer && !topContainer.getChildren().isEmpty() && topContainer.getChildren().getFirst() instanceof MenuBar menuBar) {
            Object menuItems = menuBar.getProperties().get(MENU_ITEMS_KEY);
            if (menuItems instanceof Map<?, ?> map) {
                return (Map<MenuAction, MenuItem>) map;
            }
        }
        return Map.of();
    }

    private static void registerMenuItem(MenuAction action, MenuItem menuItem, Map<MenuAction, MenuItem> menuItems) {
        if (action != null && menuItems != null) {
            menuItems.put(action, menuItem);
        }
    }

    private static void showAboutDialog() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("About PartPlan");
        alert.setHeaderText("PartPlan");
        alert.setContentText("""
                PartPlan manages inspection plans, auto-ballooning, and lot measurement data.

                Shared menu wiring is being rolled out in phases. Some screen-specific actions may still be disabled.
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

    public enum MenuAction {
        FILE_NEW_PLAN,
        FILE_OPEN_PLAN,
        FILE_OPEN_INSPECTION_LOTS,
        FILE_SAVE,
        FILE_SAVE_AS_REVISION,
        FILE_IMPORT_DRAWING_PAGE,
        FILE_EXPORT_CSV,
        FILE_EXPORT_PDF,
        FILE_SIGN_OUT,
        EDIT_UNDO,
        EDIT_REDO,
        EDIT_COPY,
        EDIT_DELETE,
        EDIT_FIND,
        EDIT_CLEAR_SELECTION,
        VIEW_SAVED_PLANS_PANEL,
        VIEW_BUBBLE_DATA_PANEL,
        VIEW_ZOOM_IN,
        VIEW_ZOOM_OUT,
        VIEW_RESET_ZOOM,
        VIEW_FIT_TO_PAGE,
        PLAN_SAVE_DRAFT,
        PLAN_OPEN_PLAN,
        PLAN_DELETE_PLAN,
        PLAN_COMPLETE_PLAN,
        PLAN_CREATE_REVISION,
        PLAN_OPEN_DATA_EDITOR,
        PLAN_AUTO_BALLOON_PAGE,
        PLAN_NEXT_PAGE,
        PLAN_PREVIOUS_PAGE,
        LOT_CREATE_LOT,
        LOT_OPEN_SELECTED_LOT,
        LOT_SAVE_LOT,
        LOT_DELETE_LOT,
        LOT_UPVERSION_LOT,
        LOT_PREVIOUS_PART,
        LOT_NEXT_PART,
        LOT_GO_TO_PART,
        TOOLS_FIREBASE_SETTINGS,
        TOOLS_OPENAI_SETTINGS,
        TOOLS_REFRESH_REMOTE_DATA,
        TOOLS_REAUTHENTICATE
    }
}
