package view;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import viewmodel.PlanEditorViewModel;
import javafx.collections.ListChangeListener;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import model.InspectionPlan;
import java.util.Optional;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

public class PlanEditorController {
    private static final Path DEFAULT_IMAGE_DIRECTORY = Path.of("src", "main", "resources", "images");
    private static final double DEFAULT_ZOOM = 1.0;
    private static final double ZOOM_STEP = 1.1;
    private static final double MIN_ZOOM = 0.25;
    private static final double MAX_ZOOM = 4.0;
    private static final double PANEL_MIN_WIDTH = 150.0;
    private static final double PANEL_MAX_WIDTH = 600.0;

    private final PlanEditorViewModel viewModel = new PlanEditorViewModel();
    private double zoomLevel = DEFAULT_ZOOM;

    // Existing fields
    @FXML private Parent root;
    @FXML private TextField planNameField;
    @FXML private Label drawingFileNameLabel;
    @FXML private Label drawingPathLabel;
    @FXML private Label emptyStateLabel;
    @FXML private ImageView drawingImageView;
    @FXML private ScrollPane drawingScrollPane;
    @FXML private ListView<InspectionPlan> savedPlansListView;

    // Panel collapse fields
    @FXML private VBox leftPanel;
    @FXML private VBox leftCollapsedTab;
    @FXML private VBox leftResizeHandle;
    @FXML private VBox rightPanel;
    @FXML private VBox rightCollapsedTab;
    @FXML private VBox rightResizeHandle;

    private boolean leftExpanded = true;
    private boolean rightExpanded = true;

    // Resize drag state
    private double dragStartX;
    private double dragStartWidth;

    @FXML
    private void initialize() {
        planNameField.setText(viewModel.getPlanName());
        drawingFileNameLabel.textProperty().bind(viewModel.drawingFileNameProperty());
        drawingPathLabel.textProperty().bind(viewModel.drawingPathProperty());
        emptyStateLabel.visibleProperty().bind(viewModel.drawingLoadedProperty().not());
        emptyStateLabel.managedProperty().bind(emptyStateLabel.visibleProperty());
        drawingScrollPane.visibleProperty().bind(viewModel.drawingLoadedProperty());
        drawingScrollPane.managedProperty().bind(drawingScrollPane.visibleProperty());
        drawingScrollPane.setPannable(true);
        drawingImageView.setPreserveRatio(true);
        root.sceneProperty().addListener((observable, oldScene, newScene) -> registerShortcuts(newScene));
        drawingScrollPane.addEventFilter(ScrollEvent.SCROLL, this::handleScrollZoom);

        savedPlansListView.setItems(viewModel.getSavedPlans());
        savedPlansListView.setCellFactory(listView -> new ListCell<>() {
            protected void updateItem(InspectionPlan item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); return; }
                setText(item.getName());
            }
        });
        viewModel.getSavedPlans().addListener(
                (ListChangeListener<InspectionPlan>) change -> selectCurrentPlanIfPresent());

        setupResizeHandle(leftResizeHandle, leftPanel, true);
        setupResizeHandle(rightResizeHandle, rightPanel, false);
    }

    // ── Resize ───────────────────────────────────────────────────────────────

    private void setupResizeHandle(VBox handle, VBox panel, boolean isLeft) {
        handle.setOnMousePressed((MouseEvent e) -> {
            dragStartX = e.getScreenX();
            dragStartWidth = panel.getPrefWidth();
            e.consume();
        });

        handle.setOnMouseDragged((MouseEvent e) -> {
            double delta = e.getScreenX() - dragStartX;
            // Left panel grows rightward (+delta); right panel grows leftward (-delta)
            double newWidth = isLeft
                    ? dragStartWidth + delta
                    : dragStartWidth - delta;
            newWidth = Math.max(PANEL_MIN_WIDTH, Math.min(PANEL_MAX_WIDTH, newWidth));
            panel.setPrefWidth(newWidth);
            e.consume();
        });
    }

    // ── Panel toggle ──────────────────────────────────────────────────────────

    @FXML
    private void onToggleLeftPanel() {
        leftExpanded = !leftExpanded;
        leftPanel.setVisible(leftExpanded);
        leftPanel.setManaged(leftExpanded);
        leftResizeHandle.setVisible(leftExpanded);
        leftResizeHandle.setManaged(leftExpanded);
        leftCollapsedTab.setVisible(!leftExpanded);
        leftCollapsedTab.setManaged(!leftExpanded);
    }

    @FXML
    private void onToggleRightPanel() {
        rightExpanded = !rightExpanded;
        rightPanel.setVisible(rightExpanded);
        rightPanel.setManaged(rightExpanded);
        rightResizeHandle.setVisible(rightExpanded);
        rightResizeHandle.setManaged(rightExpanded);
        rightCollapsedTab.setVisible(!rightExpanded);
        rightCollapsedTab.setManaged(!rightExpanded);
    }

    // ── Existing handlers (unchanged) ─────────────────────────────────────────

    @FXML
    private void onNewPlan() {
        viewModel.createNewPlan();
        planNameField.setText(viewModel.getPlanName());
        drawingImageView.setImage(null);
        resetViewport();
        savedPlansListView.getSelectionModel().clearSelection();
    }

    @FXML
    private void onSavePlan() {
        onPlanNameChanged();
        viewModel.saveCurrentPlan();
        planNameField.setText(viewModel.getPlanName());
        loadDrawingPreview(viewModel.getDrawingPath());
        selectCurrentPlanIfPresent();
        showInformation("Plan saved locally as JSON.");
    }

    @FXML
    private void onOpenPlan() {
        InspectionPlan selectedPlan = savedPlansListView.getSelectionModel().getSelectedItem();
        if (selectedPlan == null) { showInformation("Select a saved plan first."); return; }
        viewModel.openPlan(selectedPlan);
        planNameField.setText(viewModel.getPlanName());
        loadDrawingPreview(viewModel.getDrawingPath());
        resetViewport();
        selectCurrentPlanIfPresent();
    }

    @FXML
    private void onDeletePlan() {
        InspectionPlan selectedPlan = savedPlansListView.getSelectionModel().getSelectedItem();
        if (selectedPlan == null) { showInformation("Select a saved plan first."); return; }
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Plan");
        alert.setHeaderText("Delete selected plan?");
        alert.setContentText(selectedPlan.getName());
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) return;
        viewModel.deletePlan(selectedPlan);
        planNameField.setText(viewModel.getPlanName());
        loadDrawingPreview(viewModel.getDrawingPath());
        resetViewport();
    }

    @FXML
    private void onPlanNameChanged() {
        viewModel.renamePlan(planNameField.getText());
        if (!planNameField.getText().equals(viewModel.getPlanName())) {
            planNameField.setText(viewModel.getPlanName());
            planNameField.positionCaret(planNameField.getText().length());
        }
    }

    @FXML
    private void onImportDrawing() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Drawing Image");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp")
        );
        configureInitialDirectory(fileChooser);
        Window window = planNameField.getScene().getWindow();
        File selectedFile = fileChooser.showOpenDialog(window);
        if (selectedFile == null) return;
        viewModel.importDrawing(selectedFile);
        loadDrawingPreview(selectedFile.getAbsolutePath());
        resetViewport();
    }

    private void configureInitialDirectory(FileChooser fileChooser) {
        Path imageDirectory = DEFAULT_IMAGE_DIRECTORY.toAbsolutePath().normalize();
        if (Files.isDirectory(imageDirectory)) fileChooser.setInitialDirectory(imageDirectory.toFile());
    }

    private void registerShortcuts(Scene scene) {
        if (scene == null) return;
        scene.addEventFilter(KeyEvent.KEY_PRESSED, this::handleHotkeys);
    }

    private void handleHotkeys(KeyEvent event) {
        if (!event.isControlDown()) return;
        if (event.getCode() == KeyCode.S) { onSavePlan(); event.consume(); return; }
        if (!viewModel.hasDrawing()) return;
        if (event.getCode() == KeyCode.EQUALS || event.getCode() == KeyCode.PLUS) { zoomIn(); event.consume(); return; }
        if (event.getCode() == KeyCode.MINUS) { zoomOut(); event.consume(); return; }
        if (event.getCode() == KeyCode.DIGIT0 || event.getCode() == KeyCode.NUMPAD0) { resetViewport(); event.consume(); return; }
        if (event.getCode() == KeyCode.F) { fitImageToViewport(); event.consume(); }
    }

    private void handleScrollZoom(ScrollEvent event) {
        if (!event.isControlDown() || !viewModel.hasDrawing()) return;
        double previousZoom = zoomLevel;
        if (event.getDeltaY() > 0) zoomIn(); else if (event.getDeltaY() < 0) zoomOut();
        if (zoomLevel != previousZoom) event.consume();
    }

    private void zoomIn() { applyZoom(Math.min(zoomLevel * ZOOM_STEP, MAX_ZOOM)); }
    private void zoomOut() { applyZoom(Math.max(zoomLevel / ZOOM_STEP, MIN_ZOOM)); }

    private void applyZoom(double newZoomLevel) {
        zoomLevel = newZoomLevel;
        Image image = drawingImageView.getImage();
        if (image == null) { drawingImageView.setFitWidth(0); drawingImageView.setFitHeight(0); return; }
        drawingImageView.setFitWidth(image.getWidth() * zoomLevel);
        drawingImageView.setFitHeight(image.getHeight() * zoomLevel);
    }

    private void resetViewport() {
        applyZoom(DEFAULT_ZOOM);
        Platform.runLater(() -> { drawingScrollPane.setHvalue(0.0); drawingScrollPane.setVvalue(0.0); });
    }

    private void loadDrawingPreview(String drawingPath) {
        if (drawingPath == null || drawingPath.isBlank()) { drawingImageView.setImage(null); return; }
        File drawingFile = new File(drawingPath);
        if (!drawingFile.isFile()) { drawingImageView.setImage(null); return; }
        drawingImageView.setImage(new Image(drawingFile.toURI().toString()));
    }

    private void selectCurrentPlanIfPresent() {
        InspectionPlan currentPlan = viewModel.getCurrentPlan();
        if (currentPlan == null) return;
        for (InspectionPlan savedPlan : viewModel.getSavedPlans()) {
            if (savedPlan.getId().equals(currentPlan.getId())) {
                savedPlansListView.getSelectionModel().select(savedPlan);
                return;
            }
        }
    }

    private void showInformation(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("PartPlan");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void fitImageToViewport() {
        double scrollbarSize = 50;
        double paneWidth = drawingScrollPane.getWidth() - scrollbarSize;
        double paneHeight = drawingScrollPane.getHeight() - scrollbarSize;
        double imageWidth = drawingImageView.getImage().getWidth();
        double imageHeight = drawingImageView.getImage().getHeight();
        applyZoom(Math.min(paneWidth / imageWidth, paneHeight / imageHeight));
    }
}