package view;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import model.InspectionPlan;
import model.Bubble;
import model.InspectionType;
import model.PlanPage;
import viewmodel.PlanEditorViewModel;

import java.io.File;
import javafx.geometry.Point2D;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class PlanEditorController {
    private static final Path DEFAULT_IMAGE_DIRECTORY = Path.of("src", "main", "resources", "images");
    private static final String DEFAULT_PLAN_NAME = "New Inspection Plan";
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
    @FXML private Label pdfPreviewLabel;
    @FXML private ImageView drawingImageView;
    @FXML private ScrollPane drawingScrollPane;
    @FXML private Pane bubbleOverlayPane;
    @FXML private ListView<InspectionPlan> savedPlansListView;
    @FXML private ListView<PlanPage> planPagesListView;

    // Panel collapse fields
    @FXML private VBox leftPanel;
    @FXML private VBox leftCollapsedTab;
    @FXML private VBox leftResizeHandle;
    @FXML private VBox rightPanel;
    @FXML private VBox rightCollapsedTab;
    @FXML private VBox rightResizeHandle;
    @FXML private Label bubbleModeLabel;
    @FXML private Label bubbleHintLabel;
    @FXML private CheckBox useDefaultDiameterCheckBox;
    @FXML private CheckBox useDefaultColorCheckBox;
    @FXML private TextField bubbleDiameterField;
    @FXML private TextField bubbleNumberField;
    @FXML private TextField bubbleColorField;
    @FXML private TextField characteristicField;
    @FXML private ComboBox<InspectionType> inspectionTypeComboBox;
    @FXML private TextField nominalValueField;
    @FXML private TextField lowerToleranceField;
    @FXML private TextField upperToleranceField;
    @FXML private TextArea bubbleNoteArea;
    @FXML private Button saveBubbleButton;
    @FXML private Button deleteBubbleButton;

    private boolean leftExpanded = true;
    private boolean rightExpanded = true;

    // Resize drag state
    private double dragStartX;
    private double dragStartWidth;
    private Bubble draggingBubble;
    private boolean bubbleDragged;
    private boolean drawingPannableBeforeBubbleDrag = true;
    private boolean syncingPageSelection;
    private double defaultBubbleDiameter = 36.0;
    private String defaultBubbleColor = "#E53935";
    private String defaultCharacteristic = "";
    private InspectionType defaultInspectionType = InspectionType.NUMERIC;
    private String defaultNominalValue = "";
    private String defaultLowerTolerance = "";
    private String defaultUpperTolerance = "";
    private String defaultNote = "";
    private boolean updatingBubbleDefaultsUi;

    private Stage dataEditorStage;

    @FXML
    private void initialize() {
        planNameField.setText(displayPlanName(viewModel.getPlanName()));
        drawingFileNameLabel.textProperty().bind(viewModel.drawingFileNameProperty());
        drawingPathLabel.textProperty().bind(viewModel.drawingPathProperty());
        emptyStateLabel.visibleProperty().bind(viewModel.drawingLoadedProperty().not());
        emptyStateLabel.managedProperty().bind(emptyStateLabel.visibleProperty());
        drawingScrollPane.setVisible(false);
        drawingScrollPane.setManaged(false);
        pdfPreviewLabel.setVisible(false);
        pdfPreviewLabel.setManaged(false);
        drawingScrollPane.setPannable(true);
        drawingImageView.setPreserveRatio(true);
        bubbleOverlayPane.prefWidthProperty().bind(drawingImageView.fitWidthProperty());
        bubbleOverlayPane.prefHeightProperty().bind(drawingImageView.fitHeightProperty());
        bubbleOverlayPane.setOnMouseClicked(this::handleDrawingClick);
        bubbleOverlayPane.setOnMouseDragged(this::handleBubbleOverlayDrag);
        bubbleOverlayPane.setOnMouseReleased(this::handleBubbleOverlayRelease);
        root.sceneProperty().addListener((observable, oldScene, newScene) -> registerShortcuts(newScene));
        drawingScrollPane.addEventFilter(ScrollEvent.SCROLL, this::handleScrollZoom);
        viewModel.getPageBubbles().addListener((ListChangeListener<Bubble>) change -> renderBubbles());
        viewModel.selectedBubbleProperty().addListener((observable, oldBubble, newBubble) -> {
            refreshBubbleEditor(newBubble);
            renderBubbles();
        });
        drawingImageView.imageProperty().addListener((observable, oldImage, newImage) -> renderBubbles());
        inspectionTypeComboBox.getItems().setAll(InspectionType.values());
        useDefaultDiameterCheckBox.selectedProperty().addListener((observable, oldValue, newValue) -> updateDefaultControlLocks());
        useDefaultColorCheckBox.selectedProperty().addListener((observable, oldValue, newValue) -> updateDefaultControlLocks());
        inspectionTypeComboBox.valueProperty().addListener((observable, oldValue, newValue) -> updateInspectionTypeControls());
        refreshBubbleEditor(null);

        savedPlansListView.setItems(viewModel.getSavedPlans());
        savedPlansListView.setCellFactory(listView -> new ListCell<>() {
            protected void updateItem(InspectionPlan item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); return; }
                setText(item.getName());
            }
        });
        planPagesListView.setItems(viewModel.getPlanPages());
        planPagesListView.setCellFactory(listView -> new ListCell<>() {
            protected void updateItem(PlanPage item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); return; }
                String fileName = item.getDrawing() == null ? "No file" : item.getDrawing().getFileName();
                setText(item.getName() + " - " + fileName);
            }
        });
        planPagesListView.getSelectionModel().selectedItemProperty().addListener((observable, oldPage, newPage) -> {
            if (syncingPageSelection) {
                return;
            }
            if (newPage == null) {
                if (viewModel.getSelectedPage() != null) {
                    Platform.runLater(this::selectCurrentPageIfPresent);
                }
                return;
            }
            viewModel.selectPage(newPage);
            loadDrawingPreview(viewModel.getDrawingPath());
            resetViewport();
        });
        viewModel.selectedPageProperty().addListener((observable, oldPage, newPage) -> syncSelectedPage(newPage));
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
        planNameField.setText(displayPlanName(viewModel.getPlanName()));
        planPagesListView.getSelectionModel().clearSelection();
        clearDrawingPreview();
        resetViewport();
        savedPlansListView.getSelectionModel().clearSelection();
    }

    @FXML
    private void onSavePlan() {
        onPlanNameChanged();
        viewModel.saveCurrentPlan();
        planNameField.setText(displayPlanName(viewModel.getPlanName()));
        loadDrawingPreview(viewModel.getDrawingPath());
        selectCurrentPageIfPresent();
        selectCurrentPlanIfPresent();
        showInformation("Plan saved locally as JSON.");
    }

    @FXML
    private void onOpenPlan() {
        InspectionPlan selectedPlan = savedPlansListView.getSelectionModel().getSelectedItem();
        if (selectedPlan == null) { showInformation("Select a saved plan first."); return; }
        viewModel.openPlan(selectedPlan);
        planNameField.setText(displayPlanName(viewModel.getPlanName()));
        selectCurrentPageIfPresent();
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
        planNameField.setText(displayPlanName(viewModel.getPlanName()));
        selectCurrentPageIfPresent();
        loadDrawingPreview(viewModel.getDrawingPath());
        resetViewport();
    }

    @FXML
    private void onPlanNameChanged() {
        viewModel.renamePlan(planNameField.getText());
        String displayName = displayPlanName(viewModel.getPlanName());
        if (!planNameField.getText().equals(displayName)) {
            planNameField.setText(displayName);
            planNameField.positionCaret(planNameField.getText().length());
        }
    }

    @FXML
    private void onImportDrawing() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Drawing Page");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Drawing Files", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp", "*.pdf"),
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"),
                new FileChooser.ExtensionFilter("PDF Files", "*.pdf")
        );
        configureInitialDirectory(fileChooser);
        Window window = planNameField.getScene().getWindow();
        File selectedFile = fileChooser.showOpenDialog(window);
        if (selectedFile == null) return;
        viewModel.importDrawing(selectedFile);
        selectCurrentPageIfPresent();
        loadDrawingPreview(viewModel.getDrawingPath());
        resetViewport();
    }

    @FXML
    private void onSaveBubble() {
        try {
            double radius = parseBubbleRadius();
            String color = normalizeBubbleColor();
            parseNullableDouble(nominalValueField.getText());
            parseNullableDouble(lowerToleranceField.getText());
            parseNullableDouble(upperToleranceField.getText());

            Bubble selectedBubble = viewModel.getSelectedBubble();
            if (selectedBubble == null) {
                saveDefaultBubbleSettings(color);
                return;
            }

            viewModel.saveSelectedBubble(
                    parseBubbleSequenceNumber(),
                    radius,
                    shouldUseDefaultDiameter(),
                    color,
                    shouldUseDefaultColor(),
                    characteristicField.getText(),
                    inspectionTypeComboBox.getValue(),
                    nominalValueField.getText(),
                    lowerToleranceField.getText(),
                    upperToleranceField.getText(),
                    bubbleNoteArea.getText()
            );
        } catch (NumberFormatException exception) {
            showInformation("Diameter, nominal value, and tolerances must be valid numbers.");
        }
    }

    @FXML
    private void onDeleteBubble() {
        if (viewModel.getSelectedBubble() == null) {
            return;
        }

        viewModel.deleteSelectedBubble();
    }

    @FXML
    private void OnOpenDataEditor() {
        try {
            if (dataEditorStage == null || !dataEditorStage.isShowing()) { // prevents multiple dataEditor windows
                FXMLLoader fxmlLoader = new FXMLLoader();
                fxmlLoader.setLocation(getClass().getResource("/fxml/data-editor.fxml"));
                fxmlLoader.setController(new DataEditorController(this.viewModel));
                Parent root = fxmlLoader.load();

                dataEditorStage = new Stage();
                dataEditorStage.setScene(new Scene(root));
                dataEditorStage.show();
            } else {
                dataEditorStage.toFront();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
        if (isArrowNavigationEvent(event)) {
            navigateBubbleSelection(event);
            return;
        }

        if (isDeleteBubbleEvent(event)) {
            onDeleteBubble();
            event.consume();
            return;
        }

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
        if (image == null) {
            drawingImageView.setFitWidth(0);
            drawingImageView.setFitHeight(0);
            renderBubbles();
            return;
        }
        drawingImageView.setFitWidth(image.getWidth() * zoomLevel);
        drawingImageView.setFitHeight(image.getHeight() * zoomLevel);
        renderBubbles();
    }

    private void resetViewport() {
        applyZoom(DEFAULT_ZOOM);
        Platform.runLater(() -> { drawingScrollPane.setHvalue(0.0); drawingScrollPane.setVvalue(0.0); });
    }

    private void loadDrawingPreview(String drawingPath) {
        if (drawingPath == null || drawingPath.isBlank()) {
            clearDrawingPreview();
            return;
        }
        File drawingFile = new File(drawingPath);
        if (!drawingFile.isFile()) {
            clearDrawingPreview();
            return;
        }
        if (isPdf(drawingFile)) {
            drawingImageView.setImage(null);
            bubbleOverlayPane.getChildren().clear();
            drawingScrollPane.setVisible(false);
            drawingScrollPane.setManaged(false);
            pdfPreviewLabel.setVisible(true);
            pdfPreviewLabel.setManaged(true);
            return;
        }
        pdfPreviewLabel.setVisible(false);
        pdfPreviewLabel.setManaged(false);
        drawingScrollPane.setVisible(true);
        drawingScrollPane.setManaged(true);
        drawingImageView.setImage(new Image(drawingFile.toURI().toString()));
        renderBubbles();
    }

    private void clearDrawingPreview() {
        drawingImageView.setImage(null);
        bubbleOverlayPane.getChildren().clear();
        drawingScrollPane.setVisible(false);
        drawingScrollPane.setManaged(false);
        pdfPreviewLabel.setVisible(false);
        pdfPreviewLabel.setManaged(false);
    }

    private void restoreDrawing() {
        if (!viewModel.isDrawingLoaded()) {
            drawingImageView.setImage(null);
            return;
        }

        File file = new File(viewModel.getDrawingFileName());

        if (file.exists()) {
            drawingImageView.setImage(new Image(file.toURI().toString()));
            resetViewport();
        } else {
            drawingImageView.setImage(null);
        }
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

    private void selectCurrentPageIfPresent() {
        PlanPage currentPage = viewModel.getSelectedPage();
        if (currentPage == null) {
            syncSelectedPage(null);
            return;
        }

        for (PlanPage page : viewModel.getPlanPages()) {
            if (page.getId().equals(currentPage.getId())) {
                syncSelectedPage(page);
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
        if (drawingImageView.getImage() == null) {
            return;
        }

        double scrollbarSize = 50;
        double paneWidth = drawingScrollPane.getWidth() - scrollbarSize;
        double paneHeight = drawingScrollPane.getHeight() - scrollbarSize;
        double imageWidth = drawingImageView.getImage().getWidth();
        double imageHeight = drawingImageView.getImage().getHeight();
        applyZoom(Math.min(paneWidth / imageWidth, paneHeight / imageHeight));
    }

    private boolean isPdf(File file) {
        return file.getName().toLowerCase().endsWith(".pdf");
    }

    private boolean isArrowNavigationEvent(KeyEvent event) {
        if (event.isControlDown() || event.isAltDown() || event.isMetaDown()) {
            return false;
        }

        if (event.getCode() != KeyCode.LEFT
                && event.getCode() != KeyCode.RIGHT
                && event.getCode() != KeyCode.UP
                && event.getCode() != KeyCode.DOWN) {
            return false;
        }

        Scene scene = root.getScene();
        if (scene == null) {
            return false;
        }

        Node focusOwner = scene.getFocusOwner();
        return !(focusOwner instanceof TextInputControl)
                && !(focusOwner instanceof ComboBoxBase<?>)
                && !(focusOwner instanceof ListView<?>)
                && !(focusOwner instanceof TableView<?>);
    }

    private boolean isDeleteBubbleEvent(KeyEvent event) {
        if (event.isControlDown() || event.isAltDown() || event.isMetaDown()) {
            return false;
        }

        if (event.getCode() != KeyCode.DELETE && event.getCode() != KeyCode.BACK_SPACE) {
            return false;
        }

        if (viewModel.getSelectedBubble() == null) {
            return false;
        }

        Scene scene = root.getScene();
        if (scene == null) {
            return false;
        }

        Node focusOwner = scene.getFocusOwner();
        return !(focusOwner instanceof TextInputControl)
                && !(focusOwner instanceof ComboBoxBase<?>)
                && !(focusOwner instanceof ListView<?>)
                && !(focusOwner instanceof TableView<?>);
    }

    private void navigateBubbleSelection(KeyEvent event) {
        List<Bubble> bubbles = viewModel.getPageBubbles().stream()
                .sorted(Comparator.comparingInt(Bubble::getSequenceNumber))
                .toList();
        if (bubbles.isEmpty()) {
            event.consume();
            return;
        }

        int direction = (event.getCode() == KeyCode.LEFT || event.getCode() == KeyCode.UP) ? -1 : 1;
        Bubble currentBubble = viewModel.getSelectedBubble();
        int currentIndex = currentBubble == null ? -1 : indexOfBubble(bubbles, currentBubble.getId());
        int nextIndex;

        if (currentIndex < 0) {
            nextIndex = direction > 0 ? 0 : bubbles.size() - 1;
        } else {
            nextIndex = (currentIndex + direction + bubbles.size()) % bubbles.size();
        }

        viewModel.selectBubble(bubbles.get(nextIndex));
        event.consume();
    }

    private int indexOfBubble(List<Bubble> bubbles, String bubbleId) {
        for (int index = 0; index < bubbles.size(); index++) {
            if (bubbles.get(index).getId().equals(bubbleId)) {
                return index;
            }
        }
        return -1;
    }

    private void handleDrawingClick(MouseEvent event) {
        if (drawingImageView.getImage() == null || viewModel.getSelectedPage() == null) {
            return;
        }

        if (!event.isShiftDown()) {
            viewModel.selectBubble(null);
            event.consume();
            return;
        }

        double scale = getDisplayScale();
        try {
            viewModel.placeBubble(
                    event.getX() / scale,
                    event.getY() / scale,
                    defaultBubbleDiameter / 2.0,
                    true,
                    defaultBubbleColor,
                    true,
                    defaultCharacteristic,
                    defaultInspectionType,
                    parseNullableDouble(defaultNominalValue),
                    parseNullableDouble(defaultLowerTolerance),
                    parseNullableDouble(defaultUpperTolerance),
                    defaultNote
            );
            viewModel.persistBubbleLayout();
        } catch (NumberFormatException exception) {
            showInformation("Diameter, nominal value, and tolerances must be valid numbers.");
        }
        event.consume();
    }

    private void renderBubbles() {
        bubbleOverlayPane.getChildren().clear();
        Image image = drawingImageView.getImage();
        if (image == null) {
            return;
        }

        double scale = getDisplayScale();
        for (Bubble bubble : viewModel.getPageBubbles()) {
            Color bubbleColor = toFxColor(bubble.getColor());
            Circle circle = new Circle(
                    bubble.getX() * scale,
                    bubble.getY() * scale,
                    bubble.getRadius() * scale
            );
            circle.setFill(viewModel.getSelectedBubble() != null && bubble.getId().equals(viewModel.getSelectedBubble().getId())
                    ? Color.color(bubbleColor.getRed(), bubbleColor.getGreen(), bubbleColor.getBlue(), 0.20)
                    : Color.TRANSPARENT);
            circle.setStroke(bubbleColor);
            circle.setStrokeWidth(viewModel.getSelectedBubble() != null && bubble.getId().equals(viewModel.getSelectedBubble().getId()) ? 3.0 : 2.0);

            Text text = new Text(circle.getCenterX(), circle.getCenterY(), bubble.getLabel());
            text.setFill(bubbleColor);
            text.setFont(Font.font("Segoe UI", FontWeight.BOLD, bubbleLabelFontSize(bubble, scale)));
            text.setMouseTransparent(true);
            text.applyCss();
            text.setX(circle.getCenterX() - text.getLayoutBounds().getWidth() / 2.0);
            text.setY(circle.getCenterY() + text.getLayoutBounds().getHeight() / 4.0);

            circle.setOnMouseClicked(mouseEvent -> {
                if (mouseEvent.isShiftDown()) {
                    return;
                }
                viewModel.selectBubble(bubble);
                mouseEvent.consume();
            });
            circle.setOnMousePressed(mouseEvent -> {
                if (mouseEvent.isShiftDown()) {
                    return;
                }
                draggingBubble = bubble;
                bubbleDragged = false;
                drawingPannableBeforeBubbleDrag = drawingScrollPane.isPannable();
                drawingScrollPane.setPannable(false);
                viewModel.selectBubble(bubble);
                circle.toFront();
                mouseEvent.consume();
            });
            circle.setOnMouseDragged(mouseEvent -> {
                if (draggingBubble == null || !draggingBubble.getId().equals(bubble.getId())) {
                    return;
                }
                handleActiveBubbleDrag(mouseEvent.getSceneX(), mouseEvent.getSceneY());
                mouseEvent.consume();
            });
            circle.setOnMouseReleased(mouseEvent -> {
                if (draggingBubble == null || !draggingBubble.getId().equals(bubble.getId())) {
                    return;
                }
                finishActiveBubbleDrag(mouseEvent.getSceneX(), mouseEvent.getSceneY());
                mouseEvent.consume();
            });

            bubbleOverlayPane.getChildren().addAll(circle, text);
        }
    }

    private double getDisplayScale() {
        Image image = drawingImageView.getImage();
        if (image == null || image.getWidth() == 0.0) {
            return 1.0;
        }

        return drawingImageView.getFitWidth() / image.getWidth();
    }

    private double parseBubbleRadius() {
        if (shouldUseDefaultDiameter()) {
            return defaultBubbleDiameter / 2.0;
        }

        String diameterText = bubbleDiameterField.getText();
        if (diameterText == null || diameterText.isBlank()) {
            return 18.0;
        }

        double diameter = Double.parseDouble(diameterText.trim());
        if (diameter <= 0.0) {
            throw new NumberFormatException("Bubble diameter must be positive.");
        }
        return diameter / 2.0;
    }

    private int parseBubbleSequenceNumber() {
        Bubble selectedBubble = viewModel.getSelectedBubble();
        if (selectedBubble == null) {
            return 1;
        }

        String sequenceText = bubbleNumberField.getText();
        if (sequenceText == null || sequenceText.isBlank()) {
            return selectedBubble.getSequenceNumber();
        }

        int sequenceNumber = Integer.parseInt(sequenceText.trim());
        if (sequenceNumber <= 0) {
            throw new NumberFormatException("Bubble number must be positive.");
        }
        return sequenceNumber;
    }

    private Double parseNullableDouble(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Double.parseDouble(value.trim());
    }

    private String normalizeBubbleColor() {
        if (shouldUseDefaultColor()) {
            return defaultBubbleColor;
        }

        String colorText = bubbleColorField.getText();
        if (colorText == null || colorText.isBlank()) {
            return "#E53935";
        }

        Color.web(colorText.trim());
        return colorText.trim();
    }

    private Color toFxColor(String colorText) {
        try {
            return Color.web(colorText == null || colorText.isBlank() ? "#E53935" : colorText.trim());
        } catch (IllegalArgumentException exception) {
            return Color.web("#E53935");
        }
    }

    private void refreshBubbleEditor(Bubble selectedBubble) {
        if (selectedBubble == null) {
            bubbleModeLabel.setText("Default Bubble Settings");
            bubbleHintLabel.setText("Shift + Click to place a bubble.");
            saveBubbleButton.setText("Save Defaults");
            deleteBubbleButton.setDisable(true);
            updatingBubbleDefaultsUi = true;
            useDefaultDiameterCheckBox.setSelected(true);
            useDefaultColorCheckBox.setSelected(true);
            bubbleDiameterField.setText(formatNumber(defaultBubbleDiameter));
            bubbleNumberField.clear();
            bubbleNumberField.setDisable(true);
            bubbleColorField.setText(defaultBubbleColor);
            updatingBubbleDefaultsUi = false;
            characteristicField.setText(defaultCharacteristic);
            inspectionTypeComboBox.setValue(defaultInspectionType);
            nominalValueField.setText(defaultNominalValue);
            lowerToleranceField.setText(defaultLowerTolerance);
            upperToleranceField.setText(defaultUpperTolerance);
            bubbleNoteArea.setText(defaultNote);
            updateDefaultControlLocks();
            updateInspectionTypeControls();
            return;
        }

        bubbleModeLabel.setText("Selected Bubble");
        bubbleHintLabel.setText("Bubble " + selectedBubble.getLabel() + String.format(" at %.1f, %.1f", selectedBubble.getX(), selectedBubble.getY()));
        saveBubbleButton.setText("Save Bubble");
        deleteBubbleButton.setDisable(false);
        updatingBubbleDefaultsUi = true;
        useDefaultDiameterCheckBox.setSelected(selectedBubble.isUseDefaultDiameter());
        useDefaultColorCheckBox.setSelected(selectedBubble.isUseDefaultColor());
        bubbleDiameterField.setText(useDefaultDiameterCheckBox.isSelected()
                ? formatNumber(defaultBubbleDiameter)
                : formatNumber(selectedBubble.getRadius() * 2.0));
        bubbleNumberField.setText(String.valueOf(selectedBubble.getSequenceNumber()));
        bubbleNumberField.setDisable(false);
        bubbleColorField.setText(useDefaultColorCheckBox.isSelected()
                ? defaultBubbleColor
                : selectedBubble.getColor());
        updatingBubbleDefaultsUi = false;
        characteristicField.setText(selectedBubble.getCharacteristic());
        inspectionTypeComboBox.setValue(selectedBubble.getInspectionType());
        nominalValueField.setText(formatNullableNumber(selectedBubble.getNominalValue()));
        lowerToleranceField.setText(formatNullableNumber(selectedBubble.getLowerTolerance()));
        upperToleranceField.setText(formatNullableNumber(selectedBubble.getUpperTolerance()));
        bubbleNoteArea.setText(selectedBubble.getNote());
        updateDefaultControlLocks();
        updateInspectionTypeControls();
    }

    private void saveDefaultBubbleSettings(String normalizedColor) {
        defaultBubbleDiameter = Double.parseDouble(bubbleDiameterField.getText().trim());
        defaultBubbleColor = normalizedColor;
        defaultCharacteristic = valueOrEmpty(characteristicField.getText());
        defaultInspectionType = inspectionTypeComboBox.getValue() == null ? InspectionType.NUMERIC : inspectionTypeComboBox.getValue();
        defaultNominalValue = valueOrEmpty(nominalValueField.getText());
        defaultLowerTolerance = valueOrEmpty(lowerToleranceField.getText());
        defaultUpperTolerance = valueOrEmpty(upperToleranceField.getText());
        defaultNote = valueOrEmpty(bubbleNoteArea.getText());
        viewModel.applyBubbleDefaults(defaultBubbleDiameter, defaultBubbleColor);
        refreshBubbleEditor(null);
    }

    private void updateInspectionTypeControls() {
        InspectionType inspectionType = inspectionTypeComboBox.getValue();
        boolean passFail = inspectionType == InspectionType.PASS_FAIL;

        if (passFail) {
            nominalValueField.clear();
            lowerToleranceField.clear();
            upperToleranceField.clear();
        }

        nominalValueField.setDisable(passFail);
        lowerToleranceField.setDisable(passFail);
        upperToleranceField.setDisable(passFail);
    }

    private void updateDefaultControlLocks() {
        if (updatingBubbleDefaultsUi) {
            return;
        }

        Bubble selectedBubble = viewModel.getSelectedBubble();

        if (selectedBubble == null) {
            bubbleDiameterField.setDisable(false);
            bubbleColorField.setDisable(false);
            return;
        }

        if (shouldUseDefaultDiameter()) {
            bubbleDiameterField.setText(formatNumber(defaultBubbleDiameter));
            bubbleDiameterField.setDisable(true);
        } else {
            bubbleDiameterField.setDisable(false);
            if (selectedBubble.isUseDefaultDiameter()) {
                bubbleDiameterField.setText(formatNumber(selectedBubble.getRadius() * 2.0));
            }
        }

        if (shouldUseDefaultColor()) {
            bubbleColorField.setText(defaultBubbleColor);
            bubbleColorField.setDisable(true);
        } else {
            bubbleColorField.setDisable(false);
            if (selectedBubble.isUseDefaultColor()) {
                bubbleColorField.setText(selectedBubble.getColor());
            }
        }
    }

    private boolean shouldUseDefaultDiameter() {
        return useDefaultDiameterCheckBox.isSelected();
    }

    private boolean shouldUseDefaultColor() {
        return useDefaultColorCheckBox.isSelected();
    }

    private String formatNullableNumber(Double value) {
        return value == null ? "" : value.toString();
    }

    private String formatNumber(double value) {
        return value == Math.rint(value) ? String.valueOf((int) value) : String.valueOf(value);
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String displayPlanName(String planName) {
        return DEFAULT_PLAN_NAME.equals(planName) ? "" : planName;
    }

    private double bubbleLabelFontSize(Bubble bubble, double scale) {
        double diameter = bubble.getRadius() * 2.0 * scale;
        double labelLength = Math.max(1, bubble.getLabel() == null ? 1 : bubble.getLabel().length());
        double estimatedSize = (diameter * 0.95) / labelLength;
        return Math.max(10.0, Math.min(diameter * 0.7, estimatedSize));
    }

    private void syncSelectedPage(PlanPage page) {
        syncingPageSelection = true;
        try {
            if (page == null) {
                planPagesListView.getSelectionModel().clearSelection();
                clearDrawingPreview();
                return;
            }

            if (planPagesListView.getSelectionModel().getSelectedItem() == null
                    || !page.getId().equals(planPagesListView.getSelectionModel().getSelectedItem().getId())) {
                planPagesListView.getSelectionModel().select(page);
            }
            loadDrawingPreview(viewModel.getDrawingPath());
        } finally {
            syncingPageSelection = false;
        }
    }

    private void handleBubbleOverlayDrag(MouseEvent event) {
        if (draggingBubble == null) {
            return;
        }

        handleActiveBubbleDrag(event.getSceneX(), event.getSceneY());
        event.consume();
    }

    private void handleBubbleOverlayRelease(MouseEvent event) {
        if (draggingBubble == null) {
            return;
        }

        finishActiveBubbleDrag(event.getSceneX(), event.getSceneY());
        event.consume();
    }

    private void handleActiveBubbleDrag(double sceneX, double sceneY) {
        if (draggingBubble == null) {
            return;
        }

        Point2D overlayPoint = bubbleOverlayPane.sceneToLocal(sceneX, sceneY);
        updateBubblePosition(draggingBubble, overlayPoint.getX(), overlayPoint.getY());
        bubbleDragged = true;
    }

    private void finishActiveBubbleDrag(double sceneX, double sceneY) {
        if (draggingBubble == null) {
            return;
        }

        if (bubbleDragged) {
            Point2D overlayPoint = bubbleOverlayPane.sceneToLocal(sceneX, sceneY);
            updateBubblePosition(draggingBubble, overlayPoint.getX(), overlayPoint.getY());
            viewModel.persistBubbleLayout();
        }

        drawingScrollPane.setPannable(drawingPannableBeforeBubbleDrag);
        draggingBubble = null;
        bubbleDragged = false;
    }

    private void updateBubblePosition(Bubble bubble, double overlayX, double overlayY) {
        double scale = getDisplayScale();
        double imageWidth = drawingImageView.getFitWidth();
        double imageHeight = drawingImageView.getFitHeight();
        double radius = bubble.getRadius() * scale;
        double clampedX = Math.max(radius, Math.min(overlayX, imageWidth - radius));
        double clampedY = Math.max(radius, Math.min(overlayY, imageHeight - radius));

        viewModel.moveBubble(bubble, clampedX / scale, clampedY / scale);
    }
}
