package view;

import app.AppContext;
import app.AppMenuSupport;
import app.BackgroundTaskRunner;
import app.UnsavedChangesDialogs;
import app.UserFacingErrorMessages;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
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
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import model.*;
import service.autoballoon.AutoBalloonRequest;
import service.auth.AuthService;
import service.export.ExportFormat;
import service.export.InspectionExportService;
import viewmodel.PlanEditorViewModel;

import java.io.File;
import java.io.IOException;

import javafx.geometry.Point2D;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

public class PlanEditorController {
    private static final Path DEFAULT_IMAGE_DIRECTORY = Path.of("src", "main", "resources", "images");
    private static final String DEFAULT_PLAN_NAME = "New Inspection Plan";
    private static final double DEFAULT_ZOOM = 1.0;
    private static final double ZOOM_STEP = 1.1;
    private static final double MIN_ZOOM = 0.25;
    private static final double MAX_ZOOM = 4.0;
    private static final double PANEL_MIN_WIDTH = 150.0;
    private static final double PANEL_MAX_WIDTH = 600.0;

    private final PlanEditorViewModel viewModel;
    private final AuthService authService;
    private final BooleanProperty repositoryBusy = new SimpleBooleanProperty(false);
    private double zoomLevel = DEFAULT_ZOOM;
    private final InspectionExportService exportService = new InspectionExportService();
    private Window guardedWindow;
    private Scene registeredShortcutScene;
    private boolean allowWindowClose;
    private final EventHandler<WindowEvent> closeRequestHandler = this::handleCloseRequest;
    private final EventHandler<KeyEvent> hotkeyHandler = this::handleHotkeys;

    private void handleCloseRequest(WindowEvent event) {
        if (allowWindowClose) {
            allowWindowClose = false;
            return;
        }
        if (repositoryBusy.get()) {
            event.consume();
            return;
        }
        if (!viewModel.hasUnsavedChanges()) {
            return;
        }
        event.consume();
        requestProceedWithPotentialUnsavedChanges("close the plan editor", false, this::closeWindowAfterSaveOrDiscard);
    }

    public PlanEditorController(AppContext appContext) {
        this.authService = appContext.getAuthService();
        this.viewModel = new PlanEditorViewModel(
                appContext.getPlanRepository(),
                appContext.getLotRepository(),
                appContext.getAssetStore(),
                appContext.getPdfPageRenderingService(),
                appContext.getAutoBalloonDetectionService()
        );
    }

    // Existing fields
    @FXML
    private BorderPane root;
    @FXML
    private Label planTitleLabel;
    @FXML
    private Label planMetadataLabel;
    @FXML
    private Label planUnsavedLabel;
    @FXML
    private Label drawingFileNameLabel;
    @FXML
    private Label drawingPathLabel;
    @FXML
    private Label emptyStateLabel;
    @FXML
    private Label pdfPreviewLabel;
    @FXML
    private ImageView drawingImageView;
    @FXML
    private ScrollPane drawingScrollPane;
    @FXML
    private Pane bubbleOverlayPane;
    @FXML
    private ListView<Bubble> bubbleListView;
    @FXML
    private TextField bubbleSearchField;
    @FXML
    private ComboBox<String> bubbleSortComboBox;
    @FXML
    private ListView<PlanPage> planPagesListView;
    @FXML
    private Button savePlanButton;
    @FXML
    private Button completePlanButton;
    @FXML
    private Button createRevisionButton;
    @FXML
    private Button addPageButton;
    @FXML
    private Button autoBalloonButton;
    @FXML
    private Button autoBalloonSettingsButton;
    @FXML
    private HBox autoBalloonButtonShell;

    // Panel collapse fields
    @FXML
    private VBox leftPanel;
    @FXML
    private VBox leftCollapsedTab;
    @FXML
    private VBox leftResizeHandle;
    @FXML
    private VBox rightPanel;
    @FXML
    private VBox rightCollapsedTab;
    @FXML
    private VBox rightResizeHandle;
    @FXML
    private VBox bubbleEditorPane;
    @FXML
    private Label bubbleModeLabel;
    @FXML
    private Label bubbleHintLabel;
    @FXML
    private CheckBox useDefaultDiameterCheckBox;
    @FXML
    private CheckBox useDefaultColorCheckBox;
    @FXML
    private TextField bubbleDiameterField;
    @FXML
    private TextField bubbleNumberField;
    @FXML
    private TextField bubbleColorField;
    @FXML
    private TextField characteristicField;
    @FXML
    private ComboBox<InspectionType> inspectionTypeComboBox;
    @FXML
    private TextField nominalValueField;
    @FXML
    private TextField lowerToleranceField;
    @FXML
    private TextField upperToleranceField;
    @FXML
    private TextArea bubbleNoteArea;
    @FXML
    private Button saveBubbleButton;
    @FXML
    private Button deleteBubbleButton;
    @FXML
    private Button copyBubbleButton;

    private boolean leftExpanded = true;
    private boolean rightExpanded = true;

    // Resize drag state
    private double dragStartX;
    private double dragStartWidth;
    private Bubble draggingBubble;
    private boolean bubbleDragged;
    private boolean drawingPannableBeforeBubbleDrag = true;
    private boolean syncingBubbleSelection;
    private FilteredList<Bubble> filteredBubbles;
    private SortedList<Bubble> sortedBubbles;
    private boolean syncingPageSelection;
    private boolean suppressNextDrawingClick;
    private double defaultBubbleDiameter = 36.0;
    private String defaultBubbleColor = "#E53935";
    private String defaultCharacteristic = "";
    private InspectionType defaultInspectionType = InspectionType.NUMERIC;
    private String defaultNominalValue = "";
    private String defaultLowerTolerance = "";
    private String defaultUpperTolerance = "";
    private String defaultNote = "";
    private boolean updatingBubbleDefaultsUi;

    private Stage bubbleTableStage;
    private Stage autoBalloonSettingsStage;

    @FXML
    private void initialize() {
        AppMenuSupport.install(root, AppMenuSupport.MenuContext.PLAN_EDITOR, new AppMenuSupport.MenuCallbacks(
                this::signOutFromMenu,
                this::openFirebaseSettingsFromMenu,
                this::onOpenAutoBalloonSettings
        ));
        bindMenuActions();
        root.disableProperty().bind(repositoryBusy);
        refreshPlanHeader();
        drawingFileNameLabel.textProperty().bind(viewModel.drawingFileNameProperty());
        drawingPathLabel.textProperty().bind(viewModel.drawingPathProperty());
        emptyStateLabel.visibleProperty().bind(viewModel.drawingLoadedProperty().not());
        emptyStateLabel.managedProperty().bind(emptyStateLabel.visibleProperty());
        savePlanButton.disableProperty().bind(viewModel.currentPlanEditableProperty().not()
                .or(viewModel.unsavedChangesProperty().not())
                .or(viewModel.saveInProgressProperty())
                .or(repositoryBusy));
        addPageButton.disableProperty().bind(viewModel.currentPlanEditableProperty().not().or(repositoryBusy));
        autoBalloonButton.disableProperty().bind(viewModel.currentPlanEditableProperty().not()
                .or(viewModel.drawingLoadedProperty().not())
                .or(repositoryBusy));
        autoBalloonSettingsButton.disableProperty().bind(repositoryBusy);
        completePlanButton.disableProperty().bind(viewModel.currentPlanEditableProperty().not().or(repositoryBusy));
        createRevisionButton.disableProperty().bind(viewModel.currentPlanCompleteProperty().not().or(repositoryBusy));
        planUnsavedLabel.textProperty().bind(viewModel.saveStateProperty());
        planUnsavedLabel.visibleProperty().bind(viewModel.saveStateProperty().isNotEmpty());
        planUnsavedLabel.managedProperty().bind(planUnsavedLabel.visibleProperty());
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
        root.sceneProperty().addListener((observable, oldScene, newScene) -> {
            registerShortcuts(oldScene, newScene);
            registerWindowCloseGuard(newScene);
        });
        viewModel.planNameProperty().addListener((observable, oldValue, newValue) -> refreshPlanHeader());
        viewModel.planStatusProperty().addListener((observable, oldValue, newValue) -> refreshPlanHeader());
        viewModel.planVersionProperty().addListener((observable, oldValue, newValue) -> refreshPlanHeader());
        drawingScrollPane.addEventFilter(ScrollEvent.SCROLL, this::handleScrollZoom);
        viewModel.getPageBubbles().addListener((ListChangeListener<Bubble>) change -> renderBubbles());
        viewModel.selectedBubbleProperty().addListener((observable, oldBubble, newBubble) -> {
            syncBubbleListSelection(newBubble);
            refreshBubbleEditor(newBubble);
            renderBubbles();
        });
        drawingImageView.imageProperty().addListener((observable, oldImage, newImage) -> renderBubbles());
        inspectionTypeComboBox.getItems().setAll(InspectionType.values());
        useDefaultDiameterCheckBox.selectedProperty().addListener((observable, oldValue, newValue) -> updateDefaultControlLocks());
        useDefaultColorCheckBox.selectedProperty().addListener((observable, oldValue, newValue) -> updateDefaultControlLocks());
        inspectionTypeComboBox.valueProperty().addListener((observable, oldValue, newValue) -> updateInspectionTypeControls());
        viewModel.currentPlanEditableProperty().addListener((observable, oldValue, newValue) -> refreshBubbleEditor(viewModel.getSelectedBubble()));
        refreshBubbleEditor(null);

        planPagesListView.setItems(viewModel.getPlanPages());
        planPagesListView.setCellFactory(listView -> new ListCell<>() {
            protected void updateItem(PlanPage item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    return;
                }
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
        viewModel.selectedPageProperty().addListener((observable, oldPage, newPage) -> {
            bubbleSearchField.clear();
            syncSelectedPage(newPage);
        });
        // Bubble list (feature #51 + #52 + #61 sort)
        filteredBubbles = new FilteredList<>(viewModel.getPageBubbles(), b -> true);
        sortedBubbles = new SortedList<>(filteredBubbles);

        bubbleSortComboBox.getItems().setAll("By Number", "By Type");
        bubbleSortComboBox.setValue("By Number");
        bubbleSortComboBox.valueProperty().addListener((obs, oldVal, newVal) -> applyBubbleSort(newVal));
        applyBubbleSort("By Number");

        bubbleListView.setItems(sortedBubbles);
        bubbleListView.setCellFactory(lv -> new ListCell<>() {
            protected void updateItem(Bubble item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle(null);
                    return;
                }
                String label = item.getLabel() == null || item.getLabel().isBlank()
                        ? "#" + item.getSequenceNumber()
                        : item.getLabel();
                String characteristic = item.getCharacteristic();
                setText(characteristic == null || characteristic.isBlank()
                        ? label
                        : label + " — " + characteristic);

                Circle dot = new Circle(5);
                switch (item.getStatus()) {
                    case OPEN -> {
                        dot.setFill(Color.web("#808080"));
                        setStyle("");
                    }
                    case REVIEW -> {
                        dot.setFill(Color.web("#ffff00"));
                        setStyle("");
                    }
                    case PASS -> {
                        dot.setFill(Color.web("#00ff00"));
                        setStyle("");
                    }
                    case FAIL -> {
                        dot.setFill(Color.web("#ff0000"));
                        setStyle("");
                    }
                }
                setGraphic(dot);
            }
        });
        bubbleListView.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldBubble, newBubble) -> {
                    if (syncingBubbleSelection) return;
                    viewModel.selectBubble(newBubble);
                });
        bubbleSearchField.textProperty().addListener((obs, oldText, newText) -> {
            String query = newText == null ? "" : newText.trim().toLowerCase();
            filteredBubbles.setPredicate(bubble -> {
                if (query.isBlank()) return true;
                String seqStr = String.valueOf(bubble.getSequenceNumber());
                String lbl = bubble.getLabel() == null ? "" : bubble.getLabel().toLowerCase();
                String ch = bubble.getCharacteristic() == null ? "" : bubble.getCharacteristic().toLowerCase();
                return seqStr.contains(query) || lbl.contains(query) || ch.contains(query);
            });
        });

        setupResizeHandle(leftResizeHandle, leftPanel, true);
        setupResizeHandle(rightResizeHandle, rightPanel, false);
    }

    public void loadPlan(String planId) {
        if (planId == null || planId.isBlank()) {
            return;
        }

        repositoryBusy.set(true);
        BackgroundTaskRunner.run("plan-load", () -> viewModel.loadPlanFromRepository(planId), loadedPlan -> {
            repositoryBusy.set(false);
            viewModel.applyLoadedPlan(loadedPlan);
            viewModel.addOrUpdateSavedPlan(loadedPlan);
            refreshLoadedPlanView();
        }, failure -> {
            repositoryBusy.set(false);
            showFailure(failure, "Unable to load the inspection plan.");
        });
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
        requestProceedWithPotentialUnsavedChanges("start a new plan", true, this::applyNewPlanView);
    }

    @FXML
    private void onSavePlan() {
        requestSaveCurrentPlan(null);
    }

    private void requestSaveCurrentPlan(Runnable onSuccessContinuation) {
        if (repositoryBusy.get() || viewModel.saveInProgressProperty().get()) {
            showInformation("Please wait for the current database operation to finish.");
            return;
        }
        if (!viewModel.isCurrentPlanEditable()) {
            showReadOnlyPlanMessage();
            return;
        }
        if (!viewModel.hasUnsavedChanges()) {
            if (onSuccessContinuation != null) {
                onSuccessContinuation.run();
            }
            return;
        }

        saveCurrentPlanAsync(onSuccessContinuation);
    }

    private void saveCurrentPlanAsync(Runnable onSuccessContinuation) {
        InspectionPlan snapshot;
        try {
            snapshot = viewModel.beginSaveSnapshot();
        } catch (IllegalStateException exception) {
            showInformation(exception.getMessage());
            return;
        }

        savePlanButton.setText("Saving...");
        repositoryBusy.set(true);
        BackgroundTaskRunner.run("plan-save", () -> {
            viewModel.persistPlanSnapshot(snapshot);
            return snapshot;
        }, savedPlan -> {
            repositoryBusy.set(false);
            viewModel.finishSaveSuccess(savedPlan);
            savePlanButton.setText("Save Draft");
            if (onSuccessContinuation != null) {
                onSuccessContinuation.run();
            }
        }, failure -> {
            repositoryBusy.set(false);
            viewModel.finishSaveFailure(snapshot.getId());
            savePlanButton.setText("Save Draft");
            showFailure(failure, "Unable to save the plan.");
        });
    }

    @FXML
    private void onCompletePlan() {
        repositoryBusy.set(true);
        BackgroundTaskRunner.run("plan-complete", viewModel::completeCurrentPlanInRepository, completedPlan -> {
            repositoryBusy.set(false);
            viewModel.applyLoadedPlan(completedPlan);
            viewModel.addOrUpdateSavedPlan(completedPlan);
            refreshLoadedPlanView();
            showInformation("Plan marked complete as " + viewModel.planVersionProperty().get() + ".");
        }, failure -> {
            repositoryBusy.set(false);
            showFailure(failure, "Unable to complete the plan.");
        });
    }

    @FXML
    private void onCreateRevision() {
        repositoryBusy.set(true);
        BackgroundTaskRunner.run("plan-revision", viewModel::createRevisionFromCurrentPlanInRepository, revision -> {
            repositoryBusy.set(false);
            viewModel.applyLoadedPlan(revision);
            viewModel.addOrUpdateSavedPlan(revision);
            refreshLoadedPlanView();
            showInformation("Pending revision opened.");
        }, failure -> {
            repositoryBusy.set(false);
            showFailure(failure, "Unable to create a revision.");
        });
    }

    @FXML
    private void onAutoBalloonPage() {
        if (!viewModel.isCurrentPlanEditable()) {
            showInformation("Complete plans are read-only. Create a revision to make changes.");
            return;
        }
        if (drawingImageView.getImage() == null || viewModel.getSelectedPage() == null) {
            showInformation("Select a drawing page before running auto-balloon.");
            return;
        }

        int imageWidth = (int) Math.round(drawingImageView.getImage().getWidth());
        int imageHeight = (int) Math.round(drawingImageView.getImage().getHeight());
        AutoBalloonRequest request;
        try {
            request = viewModel.createAutoBalloonRequest(imageWidth, imageHeight);
        } catch (RuntimeException exception) {
            showInformation(exception.getMessage());
            return;
        }

        repositoryBusy.set(true);
        autoBalloonButton.setText("Detecting...");
        BackgroundTaskRunner.run("plan-auto-balloon",
                () -> viewModel.detectAutoBalloonCandidates(request),
                candidates -> {
                    repositoryBusy.set(false);
                    autoBalloonButton.setText("Auto-Balloon Page");
                    int addedCount = viewModel.applyAutoBalloonCandidates(candidates, imageWidth, imageHeight);
                    renderBubbles();
                    syncBubbleListSelection(viewModel.getSelectedBubble());
                    if (addedCount == 0) {
                        showInformation("No callouts were detected on this page.");
                        return;
                    }
                    showInformation(addedCount + " AI-generated balloon candidate" + (addedCount == 1 ? " was" : "s were")
                            + " added to the current page. Review all balloons for accuracy before saving.");
                },
                failure -> {
                    repositoryBusy.set(false);
                    autoBalloonButton.setText("Auto-Balloon Page");
                    showFailure(failure, "Unable to auto-balloon the selected page.");
                });
    }

    @FXML
    private void onOpenAutoBalloonSettings() {
        try {
            if (autoBalloonSettingsStage == null || !autoBalloonSettingsStage.isShowing()) {
                FXMLLoader loader = AppNavigator.createLoader("/fxml/auto-balloon-config.fxml");
                Parent settingsRoot = loader.load();
                autoBalloonSettingsStage = new Stage();
                autoBalloonSettingsStage.setTitle("PartPlan - OpenAI Settings");
                autoBalloonSettingsStage.setScene(new Scene(settingsRoot));
                if (root.getScene() != null) {
                    autoBalloonSettingsStage.initOwner(root.getScene().getWindow());
                }
                autoBalloonSettingsStage.show();
            } else {
                autoBalloonSettingsStage.toFront();
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to open the OpenAI settings window.", exception);
        }
    }

    @FXML
    private void onOpenPlan() {
        returnToPlanBrowserFromMenu();
    }

    @FXML
    private void onDeletePlan() {
        showInformation("Delete plans from the Inspection Plans browser.");
    }

    private void requestDeletePlan(InspectionPlan selectedPlan) {
        InspectionPlan currentPlan = viewModel.getCurrentPlan();
        if (currentPlan != null
                && selectedPlan.getId().equals(currentPlan.getId())
                && viewModel.hasUnsavedChanges()) {
            requestProceedWithPotentialUnsavedChanges("delete the current plan", true, () -> proceedDeletePlan(selectedPlan));
            return;
        }

        proceedDeletePlan(selectedPlan);
    }

    private void proceedDeletePlan(InspectionPlan selectedPlan) {
        repositoryBusy.set(true);
        BackgroundTaskRunner.run("plan-delete-prepare",
                () -> new DeletePlanPreparation(selectedPlan, viewModel.loadAffectedLotsForPlan(selectedPlan.getId())),
                preparation -> {
                    repositoryBusy.set(false);
                    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                    alert.setTitle("Delete Plan");
                    alert.setHeaderText("Delete selected plan?");
                    alert.setContentText(buildDeletePlanMessage(preparation.plan(), preparation.affectedLots()));
                    Optional<ButtonType> result = alert.showAndWait();
                    if (result.isEmpty() || result.get() != ButtonType.OK) {
                        return;
                    }

                    repositoryBusy.set(true);
                    BackgroundTaskRunner.run("plan-delete", () -> {
                        viewModel.deletePlanInRepository(preparation.plan().getId());
                        return preparation.plan().getId();
                    }, deletedPlanId -> {
                        repositoryBusy.set(false);
                        viewModel.applyDeletedPlan(deletedPlanId);
                        refreshLoadedPlanView();
                    }, failure -> {
                        repositoryBusy.set(false);
                        showFailure(failure, "Unable to delete the selected plan.");
                    });
                },
                failure -> {
                    repositoryBusy.set(false);
                    showFailure(failure, "Unable to load the affected inspection lots.");
                });
    }

    @FXML
    private void onReturnToPlans(ActionEvent event) throws IOException {
        requestProceedWithPotentialUnsavedChanges("return to inspection plans", true, () -> {
            try {
                openPlanBrowser((Node) event.getSource());
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to return to inspection plans.", exception);
            }
        });
    }

    @FXML
    private void onReturnToHub(ActionEvent event) throws IOException {
        requestProceedWithPotentialUnsavedChanges("return to the hub", true, () -> {
            try {
                AppNavigator.swapRoot((Node) event.getSource(), "/fxml/welcome.fxml", "PartPlan");
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to return to the hub.", exception);
            }
        });
    }

    @FXML
    private void onImportDrawing() {
        if (!viewModel.isCurrentPlanEditable()) {
            showInformation("Complete plans are read-only. Create a revision to make changes.");
            return;
        }
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Drawing Page");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Drawing Files", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp", "*.pdf"),
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"),
                new FileChooser.ExtensionFilter("PDF Files", "*.pdf")
        );
        configureInitialDirectory(fileChooser);
        Window window = root.getScene().getWindow();
        File selectedFile = fileChooser.showOpenDialog(window);
        if (selectedFile == null) return;
        viewModel.importDrawing(selectedFile);
        selectCurrentPageIfPresent();
        loadDrawingPreview(viewModel.getDrawingPath());
        resetViewport();
    }

    @FXML
    private void onSaveBubble() {
        if (!viewModel.isCurrentPlanEditable()) {
            showInformation("Complete plans are read-only. Create a revision to make changes.");
            return;
        }
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

            // #62 — Validate required fields before saving a bubble
            String bubbleNumText = bubbleNumberField.getText();
            if (bubbleNumText == null || bubbleNumText.isBlank()) {
                showValidationError("Bubble Number is required. Please enter a number before saving.");
                bubbleNumberField.requestFocus();
                return;
            }

            InspectionType selectedType = inspectionTypeComboBox.getValue();
            if (selectedType == InspectionType.NUMERIC) {
                String nomText = nominalValueField.getText();
                if (nomText == null || nomText.isBlank()) {
                    showValidationError("Nominal Value is required for Numeric inspection type.");
                    nominalValueField.requestFocus();
                    return;
                }
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
    private void onCopyBubble() {
        if (!viewModel.isCurrentPlanEditable()) {
            showInformation("Complete plans are read-only. Create a revision to make changes.");
            return;
        }
        if (viewModel.getSelectedBubble() == null) {
            showInformation("Select a bubble first to copy it.");
            return;
        }
        viewModel.copySelectedBubble();
    }

    @FXML
    private void onDeleteBubble() {
        if (!viewModel.isCurrentPlanEditable()) {
            showInformation("Complete plans are read-only. Create a revision to make changes.");
            return;
        }
        if (viewModel.getSelectedBubble() == null) {
            return;
        }

        viewModel.deleteSelectedBubble();
    }

    @FXML
    private void onOpenBubbleTable() {
        try {
            if (bubbleTableStage == null || !bubbleTableStage.isShowing()) {
                FXMLLoader fxmlLoader = new FXMLLoader();
                fxmlLoader.setLocation(getClass().getResource("/fxml/data-editor.fxml"));
                fxmlLoader.setController(new DataEditorController(this.viewModel));
                Parent bubbleTableRoot = fxmlLoader.load();
                bubbleTableRoot.disableProperty().bind(repositoryBusy.or(viewModel.saveInProgressProperty()));

                bubbleTableStage = new Stage();
                bubbleTableStage.setTitle("PartPlan - Bubble Table");
                bubbleTableStage.setScene(new Scene(bubbleTableRoot));
                if (root.getScene() != null) {
                    bubbleTableStage.initOwner(root.getScene().getWindow());
                }
                bubbleTableStage.show();
            } else {
                bubbleTableStage.toFront();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @FXML
    private void onExportCsv() throws Exception {
        InspectionPlan currentPlan = viewModel.getCurrentPlan();
        if (isExportablePlan(currentPlan)) {
            File file = showSaveDialog("CSV Files", "*.csv");
            if (file == null) return;
            exportService.export(currentPlan, ExportFormat.CSV, file.toPath());
            showInformation("CSV exported successfully.");
        } else {
            showExportAlert();
        }
    }

    @FXML
    private void onExportPdf() throws Exception {
        InspectionPlan currentPlan = viewModel.getCurrentPlan();
        if (isExportablePlan(currentPlan)) {
            File file = showSaveDialog("PDF Files", "*.pdf");
            if (file == null) return;
            exportService.export(currentPlan, ExportFormat.PDF, file.toPath());
            showInformation("PDF exported successfully.");
        } else {
            showExportAlert();
        }
    }

    private boolean isExportablePlan(InspectionPlan currentPlan) {
        return currentPlan != null
                && currentPlan.getName() != null
                && !currentPlan.getPages().isEmpty();
    }

    private File showSaveDialog(String desc, String extension) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Inspection Report");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(desc, extension));
        File downloadsDir = new File(System.getProperty("user.home"), "Downloads");
        if (downloadsDir.exists() && downloadsDir.isDirectory()) {
            fileChooser.setInitialDirectory(downloadsDir);
        }
        return fileChooser.showSaveDialog(root.getScene().getWindow());
    }

    private void showExportAlert() {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Nothing to Export");
        alert.setContentText("Add a page or open an inspection plan before exporting.");
        alert.showAndWait();
    }

    private void configureInitialDirectory(FileChooser fileChooser) {
        Path imageDirectory = DEFAULT_IMAGE_DIRECTORY.toAbsolutePath().normalize();
        if (Files.isDirectory(imageDirectory)) fileChooser.setInitialDirectory(imageDirectory.toFile());
    }

    private void registerShortcuts(Scene oldScene, Scene newScene) {
        if (oldScene != null && oldScene != newScene) {
            oldScene.removeEventFilter(KeyEvent.KEY_PRESSED, hotkeyHandler);
            if (registeredShortcutScene == oldScene) {
                registeredShortcutScene = null;
            }
        }
        if (newScene == null || newScene == registeredShortcutScene) {
            return;
        }
        newScene.addEventFilter(KeyEvent.KEY_PRESSED, hotkeyHandler);
        registeredShortcutScene = newScene;
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

        if (isUndoShortcut(event)) {
            if (!isTextInputFocusOwner()) {
                onUndoFromMenu();
                event.consume();
            }
            return;
        }

        if (isRedoShortcut(event)) {
            if (!isTextInputFocusOwner()) {
                onRedoFromMenu();
                event.consume();
            }
            return;
        }

        if (!event.isControlDown()) return;
        if (event.getCode() == KeyCode.S) {
            requestSaveCurrentPlan(null);
            event.consume();
            return;
        }
        if (event.getCode() == KeyCode.C && viewModel.getSelectedBubble() != null) {
            onCopyBubble();
            event.consume();
            return;
        }
        if (!viewModel.hasDrawing()) return;
        if (event.getCode() == KeyCode.EQUALS || event.getCode() == KeyCode.PLUS) {
            zoomIn();
            event.consume();
            return;
        }
        if (event.getCode() == KeyCode.MINUS) {
            zoomOut();
            event.consume();
            return;
        }
        if (event.getCode() == KeyCode.DIGIT0 || event.getCode() == KeyCode.NUMPAD0) {
            resetViewport();
            event.consume();
            return;
        }
        if (event.getCode() == KeyCode.F) {
            fitImageToViewport();
            event.consume();
        }
    }

    private void handleScrollZoom(ScrollEvent event) {
        if (!event.isControlDown() || !viewModel.hasDrawing()) return;
        double previousZoom = zoomLevel;
        if (event.getDeltaY() > 0) zoomIn();
        else if (event.getDeltaY() < 0) zoomOut();
        if (zoomLevel != previousZoom) event.consume();
    }

    private void zoomIn() {
        applyZoom(Math.min(zoomLevel * ZOOM_STEP, MAX_ZOOM));
    }

    private void zoomOut() {
        applyZoom(Math.max(zoomLevel / ZOOM_STEP, MIN_ZOOM));
    }

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
        Platform.runLater(() -> {
            drawingScrollPane.setHvalue(0.0);
            drawingScrollPane.setVvalue(0.0);
        });
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

    private void showReadOnlyPlanMessage() {
        showInformation("Complete plans are read-only. Create a revision to make changes.");
    }

    private void showFailure(Throwable failure, String fallbackMessage) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("PartPlan");
        alert.setHeaderText("Action failed");
        alert.setContentText(UserFacingErrorMessages.format(failure, fallbackMessage));
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

    private boolean isUndoShortcut(KeyEvent event) {
        return event.isControlDown()
                && !event.isAltDown()
                && !event.isMetaDown()
                && !event.isShiftDown()
                && event.getCode() == KeyCode.Z;
    }

    private boolean isRedoShortcut(KeyEvent event) {
        if (!event.isControlDown() || event.isAltDown() || event.isMetaDown()) {
            return false;
        }
        return event.getCode() == KeyCode.Y
                || (event.isShiftDown() && event.getCode() == KeyCode.Z);
    }

    private boolean isTextInputFocusOwner() {
        Scene scene = root.getScene();
        if (scene == null) {
            return false;
        }
        return scene.getFocusOwner() instanceof TextInputControl;
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

        if (suppressNextDrawingClick) {
            suppressNextDrawingClick = false;
            event.consume();
            return;
        }

        if (event.isShiftDown() && !viewModel.isCurrentPlanEditable()) {
            event.consume();
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
            boolean isSelected = viewModel.getSelectedBubble() != null
                    && bubble.getId().equals(viewModel.getSelectedBubble().getId());
            Color baseColor = toFxColor(bubble.getColor());
            Circle circle = new Circle(
                    bubble.getX() * scale,
                    bubble.getY() * scale,
                    bubble.getRadius() * scale
            );
            double bubbleStrokeWidth = isSelected ? 3.0 : 2.0;

            Circle selectionOutline = null;
            if (isSelected) {
                double selectionOutlineStrokeWidth = 2.0;
                Circle outline = new Circle(
                        circle.getCenterX(),
                        circle.getCenterY(),
                        circle.getRadius() + (bubbleStrokeWidth + selectionOutlineStrokeWidth) / 2.0
                );
                outline.setFill(Color.TRANSPARENT);
                outline.setStroke(Color.web("#183247"));
                outline.setStrokeWidth(selectionOutlineStrokeWidth);
                outline.setMouseTransparent(true);
                selectionOutline = outline;
            }

            Circle selectionOutlineNode = selectionOutline;

            circle.setFill(Color.WHITE);
            circle.setStroke(baseColor);
            circle.setStrokeWidth(bubbleStrokeWidth);

            Text text = new Text(circle.getCenterX(), circle.getCenterY(), bubble.getLabel());
            text.setFill(baseColor);
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
                viewModel.selectBubble(bubble);
                if (!viewModel.isCurrentPlanEditable()) {
                    mouseEvent.consume();
                    return;
                }
                draggingBubble = bubble;
                bubbleDragged = false;
                drawingPannableBeforeBubbleDrag = drawingScrollPane.isPannable();
                drawingScrollPane.setPannable(false);
                if (selectionOutlineNode != null) {
                    selectionOutlineNode.toFront();
                }
                circle.toFront();
                text.toFront();
                mouseEvent.consume();
            });
            circle.setOnMouseDragged(mouseEvent -> {
                if (!viewModel.isCurrentPlanEditable()) {
                    return;
                }
                if (draggingBubble == null || !draggingBubble.getId().equals(bubble.getId())) {
                    return;
                }
                handleActiveBubbleDrag(mouseEvent.getSceneX(), mouseEvent.getSceneY());
                mouseEvent.consume();
            });
            circle.setOnMouseReleased(mouseEvent -> {
                if (!viewModel.isCurrentPlanEditable()) {
                    return;
                }
                if (draggingBubble == null || !draggingBubble.getId().equals(bubble.getId())) {
                    return;
                }
                finishActiveBubbleDrag(mouseEvent.getSceneX(), mouseEvent.getSceneY());
                mouseEvent.consume();
            });

            if (selectionOutlineNode != null) {
                bubbleOverlayPane.getChildren().add(selectionOutlineNode);
            }
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
        boolean editable = viewModel.isCurrentPlanEditable();
        if (selectedBubble == null) {
            bubbleModeLabel.setText("Default Bubble Settings");
            bubbleHintLabel.setText("Shift + Click to place a bubble. Ctrl + C to copy.");
            saveBubbleButton.setText("Save Defaults");
            deleteBubbleButton.setDisable(true);
            copyBubbleButton.setDisable(true); // when no bubble selected
            updatingBubbleDefaultsUi = true;
            useDefaultDiameterCheckBox.setSelected(true);
            useDefaultColorCheckBox.setSelected(true);
            bubbleDiameterField.setText(formatNumber(defaultBubbleDiameter));
            bubbleNumberField.clear();
            bubbleNumberField.setDisable(true);
            bubbleNumberField.setEditable(false);
            bubbleColorField.setText(defaultBubbleColor);
            updatingBubbleDefaultsUi = false;
            characteristicField.setText(defaultCharacteristic);
            inspectionTypeComboBox.setValue(defaultInspectionType);
            nominalValueField.setText(defaultNominalValue);
            lowerToleranceField.setText(defaultLowerTolerance);
            upperToleranceField.setText(defaultUpperTolerance);
            bubbleNoteArea.setText(defaultNote);
            setBubbleEditorEditable(editable);
            updateDefaultControlLocks();
            updateInspectionTypeControls();
            return;
        }

        bubbleModeLabel.setText("Selected Bubble");
        bubbleHintLabel.setText("Bubble " + selectedBubble.getLabel() + String.format(" at %.1f, %.1f", selectedBubble.getX(), selectedBubble.getY()));
        saveBubbleButton.setText("Save Bubble");
        deleteBubbleButton.setDisable(!editable);
        copyBubbleButton.setDisable(!editable); // when a bubble is selected
        updatingBubbleDefaultsUi = true;
        useDefaultDiameterCheckBox.setSelected(selectedBubble.isUseDefaultDiameter());
        useDefaultColorCheckBox.setSelected(selectedBubble.isUseDefaultColor());
        bubbleDiameterField.setText(useDefaultDiameterCheckBox.isSelected()
                ? formatNumber(defaultBubbleDiameter)
                : formatNumber(selectedBubble.getRadius() * 2.0));
        bubbleNumberField.setText(String.valueOf(selectedBubble.getSequenceNumber()));
        bubbleNumberField.setDisable(!editable);
        bubbleNumberField.setEditable(editable);
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
        setBubbleEditorEditable(editable);
        updateDefaultControlLocks();
        updateInspectionTypeControls();
    }

    private void applyBubbleSort(String sortOption) {
        if ("By Type".equals(sortOption)) {
            sortedBubbles.setComparator(
                    Comparator.comparing((Bubble b) -> b.getInspectionType() == null ? "" : b.getInspectionType().name())
                            .thenComparingInt(Bubble::getSequenceNumber)
            );
        } else {
            // Default: By Number
            sortedBubbles.setComparator(Comparator.comparingInt(Bubble::getSequenceNumber));
        }
    }

    private void showValidationError(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Incomplete Bubble");
        alert.setHeaderText("Required field missing");
        alert.setContentText(message);
        alert.showAndWait();
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
        boolean editable = viewModel.isCurrentPlanEditable();

        if (passFail && editable) {
            nominalValueField.clear();
            lowerToleranceField.clear();
            upperToleranceField.clear();
        }

        nominalValueField.setDisable(!editable || passFail);
        lowerToleranceField.setDisable(!editable || passFail);
        upperToleranceField.setDisable(!editable || passFail);
    }

    private void updateDefaultControlLocks() {
        if (updatingBubbleDefaultsUi) {
            return;
        }

        Bubble selectedBubble = viewModel.getSelectedBubble();
        boolean editable = viewModel.isCurrentPlanEditable();
        useDefaultDiameterCheckBox.setDisable(!editable);
        useDefaultColorCheckBox.setDisable(!editable);
        inspectionTypeComboBox.setDisable(!editable);

        if (selectedBubble == null) {
            bubbleDiameterField.setDisable(!editable);
            bubbleColorField.setDisable(!editable);
            return;
        }

        if (shouldUseDefaultDiameter()) {
            bubbleDiameterField.setText(formatNumber(defaultBubbleDiameter));
            bubbleDiameterField.setDisable(true);
        } else {
            bubbleDiameterField.setDisable(!editable);
            if (selectedBubble.isUseDefaultDiameter()) {
                bubbleDiameterField.setText(formatNumber(selectedBubble.getRadius() * 2.0));
            }
        }

        if (shouldUseDefaultColor()) {
            bubbleColorField.setText(defaultBubbleColor);
            bubbleColorField.setDisable(true);
        } else {
            bubbleColorField.setDisable(!editable);
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

    private String currentPlanDisplayName() {
        String planName = viewModel.getPlanName();
        if (planName == null || planName.isBlank()) {
            return DEFAULT_PLAN_NAME;
        }
        return planName.trim();
    }

    private void refreshPlanHeader() {
        planTitleLabel.setText(currentPlanDisplayName());
        String versionText = valueOrEmpty(viewModel.planVersionProperty().get());
        String statusText = valueOrEmpty(viewModel.planStatusProperty().get());
        String metadata = versionText.isBlank()
                ? statusText
                : statusText.isBlank() ? versionText : versionText + " · " + statusText;
        planMetadataLabel.setText(metadata.isBlank() ? "Draft · Pending" : metadata);
    }

    private double bubbleLabelFontSize(Bubble bubble, double scale) {
        double diameter = bubble.getRadius() * 2.0 * scale;
        double labelLength = Math.max(1, bubble.getLabel() == null ? 1 : bubble.getLabel().length());
        double estimatedSize = (diameter * 0.95) / labelLength;
        return Math.max(10.0, Math.min(diameter * 0.7, estimatedSize));
    }

    private void syncBubbleListSelection(Bubble bubble) {
        syncingBubbleSelection = true;
        try {
            if (bubble == null) {
                bubbleListView.getSelectionModel().clearSelection();
                return;
            }
            for (Bubble b : sortedBubbles) {
                if (b.getId().equals(bubble.getId())) {
                    bubbleListView.getSelectionModel().select(b);
                    bubbleListView.scrollTo(b);
                    return;
                }
            }
            bubbleListView.getSelectionModel().clearSelection();
        } finally {
            syncingBubbleSelection = false;
        }
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
        if (!viewModel.isCurrentPlanEditable()) {
            return;
        }
        if (draggingBubble == null) {
            return;
        }

        Point2D overlayPoint = bubbleOverlayPane.sceneToLocal(sceneX, sceneY);
        updateBubblePosition(draggingBubble, overlayPoint.getX(), overlayPoint.getY());
        bubbleDragged = true;
    }

    private void finishActiveBubbleDrag(double sceneX, double sceneY) {
        if (!viewModel.isCurrentPlanEditable()) {
            draggingBubble = null;
            bubbleDragged = false;
            return;
        }
        if (draggingBubble == null) {
            return;
        }

        Bubble releasedBubble = draggingBubble;
        if (bubbleDragged) {
            Point2D overlayPoint = bubbleOverlayPane.sceneToLocal(sceneX, sceneY);
            updateBubblePosition(releasedBubble, overlayPoint.getX(), overlayPoint.getY());
            viewModel.persistBubbleLayout();
            suppressNextDrawingClick = true;
        }

        viewModel.selectBubble(releasedBubble);
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
        renderBubbles();
    }

    private String formatPlanListEntry(InspectionPlan plan) {
        String name = plan.getName() == null || plan.getName().isBlank() ? "Untitled Plan" : plan.getName().trim();
        String versionText = plan.getVersion() <= 0 ? "Draft" : "v" + plan.getVersion();
        String statusText = plan.isComplete() ? "Complete" : "Pending";
        return name + " (" + statusText + ", " + versionText + ")";
    }

    private InspectionPlan promptForPlanSelection() {
        if (viewModel.getSavedPlans().isEmpty()) {
            showInformation("There are no saved plans to open.");
            return null;
        }

        ListView<InspectionPlan> planListView = new ListView<>(viewModel.getSavedPlans());
        planListView.setPrefWidth(420.0);
        planListView.setPrefHeight(320.0);
        planListView.setCellFactory(listView -> new ListCell<>() {
            @Override
            protected void updateItem(InspectionPlan item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : formatPlanListEntry(item));
            }
        });

        Dialog<InspectionPlan> dialog = new Dialog<>();
        dialog.setTitle("Open Plan");
        dialog.setHeaderText("Choose a plan to open");
        dialog.getDialogPane().setContent(planListView);
        ButtonType openButtonType = new ButtonType("Open", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(openButtonType, ButtonType.CANCEL);
        Node openButton = dialog.getDialogPane().lookupButton(openButtonType);
        openButton.disableProperty().bind(planListView.getSelectionModel().selectedItemProperty().isNull());

        planListView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && planListView.getSelectionModel().getSelectedItem() != null) {
                dialog.setResult(planListView.getSelectionModel().getSelectedItem());
                dialog.close();
            }
        });

        dialog.setResultConverter(buttonType -> buttonType == openButtonType
                ? planListView.getSelectionModel().getSelectedItem()
                : null);
        return dialog.showAndWait().orElse(null);
    }

    private void refreshLoadedPlanView() {
        refreshPlanHeader();
        selectCurrentPageIfPresent();
        loadDrawingPreview(viewModel.getDrawingPath());
        resetViewport();
    }

    private void applyNewPlanView() {
        viewModel.createNewPlan();
        refreshPlanHeader();
        planPagesListView.getSelectionModel().clearSelection();
        clearDrawingPreview();
        resetViewport();
    }

    private void requestProceedWithPotentialUnsavedChanges(String actionLabel, boolean showBusyMessage, Runnable continuation) {
        if (repositoryBusy.get()) {
            if (showBusyMessage) {
                showInformation("Please wait for the current database operation to finish.");
            }
            return;
        }
        if (!viewModel.hasUnsavedChanges()) {
            continuation.run();
            return;
        }

        switch (UnsavedChangesDialogs.promptToSaveDiscardOrCancel("plan", actionLabel)) {
            case SAVE -> requestSaveCurrentPlan(continuation);
            case DISCARD -> continuation.run();
            case CANCEL -> {
            }
        }
    }

    private void bindMenuActions() {
        AppMenuSupport.bindAction(root, AppMenuSupport.MenuAction.FILE_RENAME_PLAN, this::onRenameCurrentPlanFromMenu);
        AppMenuSupport.bindAction(root, AppMenuSupport.MenuAction.FILE_SAVE, this::onSavePlan);
        AppMenuSupport.bindAction(root, AppMenuSupport.MenuAction.FILE_SAVE_AS_REVISION, this::onCreateRevision);
        AppMenuSupport.bindAction(root, AppMenuSupport.MenuAction.FILE_IMPORT_DRAWING_PAGE, this::onImportDrawing);
        AppMenuSupport.bindAction(root, AppMenuSupport.MenuAction.FILE_EXPORT_CSV, this::onExportCsvFromMenu);
        AppMenuSupport.bindAction(root, AppMenuSupport.MenuAction.FILE_EXPORT_PDF, this::onExportPdfFromMenu);
        AppMenuSupport.bindAction(root, AppMenuSupport.MenuAction.EDIT_UNDO, this::onUndoFromMenu);
        AppMenuSupport.bindAction(root, AppMenuSupport.MenuAction.EDIT_REDO, this::onRedoFromMenu);
        AppMenuSupport.bindAction(root, AppMenuSupport.MenuAction.NAV_PLANS, this::returnToPlanBrowserFromMenu);
        AppMenuSupport.bindAction(root, AppMenuSupport.MenuAction.NAV_INSPECTION_LOTS, this::returnToLotBrowserFromMenu);
        AppMenuSupport.bindAction(root, AppMenuSupport.MenuAction.NAV_HOME, this::returnToHubFromMenu);

        AppMenuSupport.bindAction(root, AppMenuSupport.MenuAction.VIEW_SAVED_PLANS_PANEL, this::onToggleLeftPanel);
        AppMenuSupport.bindAction(root, AppMenuSupport.MenuAction.VIEW_BUBBLE_DATA_PANEL, this::onToggleRightPanel);
        AppMenuSupport.bindAction(root, AppMenuSupport.MenuAction.VIEW_ZOOM_IN, this::zoomIn);
        AppMenuSupport.bindAction(root, AppMenuSupport.MenuAction.VIEW_ZOOM_OUT, this::zoomOut);
        AppMenuSupport.bindAction(root, AppMenuSupport.MenuAction.VIEW_RESET_ZOOM, this::resetViewport);
        AppMenuSupport.bindAction(root, AppMenuSupport.MenuAction.VIEW_FIT_TO_PAGE, this::fitImageToViewport);

        AppMenuSupport.bindAction(root, AppMenuSupport.MenuAction.PLAN_COMPLETE_PLAN, this::onCompletePlan);
        AppMenuSupport.bindAction(root, AppMenuSupport.MenuAction.PLAN_CREATE_REVISION, this::onCreateRevision);
        AppMenuSupport.bindAction(root, AppMenuSupport.MenuAction.PLAN_OPEN_DATA_EDITOR, this::onOpenBubbleTable);
        AppMenuSupport.bindAction(root, AppMenuSupport.MenuAction.PLAN_AUTO_BALLOON_PAGE, this::onAutoBalloonPage);

        AppMenuSupport.bindDisable(root, AppMenuSupport.MenuAction.FILE_SAVE,
                viewModel.currentPlanEditableProperty().not()
                        .or(viewModel.unsavedChangesProperty().not())
                        .or(viewModel.saveInProgressProperty())
                        .or(repositoryBusy));
        AppMenuSupport.bindDisable(root, AppMenuSupport.MenuAction.EDIT_UNDO,
                viewModel.canUndoProperty().not().or(repositoryBusy));
        AppMenuSupport.bindDisable(root, AppMenuSupport.MenuAction.EDIT_REDO,
                viewModel.canRedoProperty().not().or(repositoryBusy));
        AppMenuSupport.bindDisable(root, AppMenuSupport.MenuAction.FILE_RENAME_PLAN,
                viewModel.currentPlanEditableProperty().not().or(repositoryBusy));
        AppMenuSupport.bindDisable(root, AppMenuSupport.MenuAction.FILE_IMPORT_DRAWING_PAGE,
                viewModel.currentPlanEditableProperty().not().or(repositoryBusy));
        AppMenuSupport.bindDisable(root, AppMenuSupport.MenuAction.PLAN_COMPLETE_PLAN,
                viewModel.currentPlanEditableProperty().not().or(repositoryBusy));
        AppMenuSupport.bindDisable(root, AppMenuSupport.MenuAction.PLAN_CREATE_REVISION,
                viewModel.currentPlanCompleteProperty().not().or(repositoryBusy));
        AppMenuSupport.bindDisable(root, AppMenuSupport.MenuAction.PLAN_AUTO_BALLOON_PAGE,
                viewModel.currentPlanEditableProperty().not()
                        .or(viewModel.drawingLoadedProperty().not())
                        .or(repositoryBusy));
    }

    private void onUndoFromMenu() {
        performPlanUndo();
    }

    private void onRedoFromMenu() {
        performPlanRedo();
    }

    private void performPlanUndo() {
        if (repositoryBusy.get() || !viewModel.canUndo()) {
            return;
        }
        try {
            viewModel.undo();
        } catch (IllegalStateException exception) {
            showInformation(exception.getMessage());
        }
    }

    private void performPlanRedo() {
        if (repositoryBusy.get() || !viewModel.canRedo()) {
            return;
        }
        try {
            viewModel.redo();
        } catch (IllegalStateException exception) {
            showInformation(exception.getMessage());
        }
    }

    private void onRenameCurrentPlanFromMenu() {
        if (!viewModel.isCurrentPlanEditable()) {
            showInformation("Complete plans are read-only. Create a revision to make changes.");
            return;
        }

        TextInputDialog dialog = new TextInputDialog(currentPlanDisplayName());
        dialog.setTitle("Rename Inspection Plan");
        dialog.setHeaderText("Rename current inspection plan");
        dialog.setContentText("Plan Name:");
        Optional<String> result = dialog.showAndWait();
        if (result.isEmpty()) {
            return;
        }

        viewModel.renamePlan(result.get());
        refreshPlanHeader();
    }

    private void returnToPlanBrowserFromMenu() {
        requestProceedWithPotentialUnsavedChanges("return to inspection plans", true, () -> {
            try {
                openPlanBrowser(root);
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to return to inspection plans.", exception);
            }
        });
    }

    private void returnToLotBrowserFromMenu() {
        requestProceedWithPotentialUnsavedChanges("return to inspection lots", true, () -> {
            try {
                openLotBrowser(root);
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to open inspection lots.", exception);
            }
        });
    }

    private void onExportCsvFromMenu() {
        try {
            onExportCsv();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to export CSV.", exception);
        }
    }

    private void onExportPdfFromMenu() {
        try {
            onExportPdf();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to export PDF.", exception);
        }
    }

    private void onOpenPlanFromMenu() {
        InspectionPlan selectedPlan = promptForPlanSelection();
        if (selectedPlan == null) {
            return;
        }
        requestProceedWithPotentialUnsavedChanges("open another plan", true, () -> {
            repositoryBusy.set(true);
            BackgroundTaskRunner.run("plan-open", () -> viewModel.loadPlanFromRepository(selectedPlan.getId()), loadedPlan -> {
                repositoryBusy.set(false);
                viewModel.applyLoadedPlan(loadedPlan);
                viewModel.addOrUpdateSavedPlan(loadedPlan);
                refreshLoadedPlanView();
            }, failure -> {
                repositoryBusy.set(false);
                showFailure(failure, "Unable to open the selected plan.");
            });
        });
    }

    private void onDeletePlanFromMenu() {
        InspectionPlan selectedPlan = promptForPlanSelection();
        if (selectedPlan == null) {
            return;
        }
        requestDeletePlan(selectedPlan);
    }

    private void returnToHubFromMenu() {
        requestProceedWithPotentialUnsavedChanges("return to the hub", true, () -> {
            try {
                AppNavigator.swapRoot(root, "/fxml/welcome.fxml", "PartPlan");
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to return to the hub.", exception);
            }
        });
    }

    private void signOutFromMenu() {
        requestProceedWithPotentialUnsavedChanges("sign out", true, () -> {
            try {
                authService.signOut();
                AppNavigator.swapRoot(root, "/fxml/login.fxml", "PartPlan - Sign In");
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to sign out.", exception);
            }
        });
    }

    private void openFirebaseSettingsFromMenu() {
        requestProceedWithPotentialUnsavedChanges("open Firebase settings", true, () -> {
            try {
                AppNavigator.swapRoot(root, "/fxml/firebase-config.fxml", "PartPlan - Firebase Setup");
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to open the Firebase settings screen.", exception);
            }
        });
    }

    private void registerWindowCloseGuard(Scene scene) {
        if (guardedWindow != null) {
            guardedWindow.removeEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, closeRequestHandler);
            guardedWindow = null;
        }
        if (scene == null) {
            return;
        }

        Consumer<Window> installer = window -> {
            if (window == null || window == guardedWindow) {
                return;
            }
            guardedWindow = window;
            guardedWindow.addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, closeRequestHandler);
        };

        installer.accept(scene.getWindow());
        scene.windowProperty().addListener((observable, oldWindow, newWindow) -> {
            if (oldWindow != null && oldWindow != guardedWindow) {
                oldWindow.removeEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, closeRequestHandler);
            }
            if (oldWindow != null && oldWindow == guardedWindow) {
                oldWindow.removeEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, closeRequestHandler);
                guardedWindow = null;
            }
            installer.accept(newWindow);
        });
    }

    private void closeWindowAfterSaveOrDiscard() {
        if (guardedWindow == null) {
            return;
        }
        allowWindowClose = true;
        if (guardedWindow instanceof Stage stage) {
            stage.close();
            return;
        }
        guardedWindow.hide();
    }

    private void openPlanBrowser(Node source) throws IOException {
        String currentPlanId = viewModel.getCurrentPlan() == null ? null : viewModel.getCurrentPlan().getId();
        AppNavigator.swapRoot(source, "/fxml/plan-browser.fxml", "PartPlan - Inspection Plans", loader -> {
            PlanBrowserController controller = loader.getController();
            controller.selectPlan(currentPlanId);
        });
    }

    private void openLotBrowser(Node source) throws IOException {
        AppNavigator.swapRoot(source, "/fxml/inspection-lot-browser.fxml", "PartPlan - Inspection Lots");
    }

    private String buildDeletePlanMessage(InspectionPlan plan, List<InspectionLotSummary> affectedLots) {
        String name = plan.getName() == null || plan.getName().isBlank() ? "Untitled Plan" : plan.getName().trim();
        if (affectedLots.isEmpty()) {
            return name;
        }

        List<String> lotNames = affectedLots.stream()
                .limit(5)
                .map(InspectionLotSummary::getName)
                .toList();
        String lotList = String.join("\n- ", lotNames);
        String suffix = affectedLots.size() > lotNames.size()
                ? "\n- and %d more".formatted(affectedLots.size() - lotNames.size())
                : "";

        return """
                %s
                
                This will also delete %d inspection %s for this exact plan version:
                - %s%s
                """.formatted(
                name,
                affectedLots.size(),
                affectedLots.size() == 1 ? "lot" : "lots",
                lotList,
                suffix
        );
    }

    private void setBubbleEditorEditable(boolean editable) {
        saveBubbleButton.setDisable(!editable);
        characteristicField.setEditable(editable);
        bubbleNoteArea.setEditable(editable);
        bubbleDiameterField.setEditable(editable);
        bubbleNumberField.setEditable(editable);
        bubbleColorField.setEditable(editable);
        nominalValueField.setEditable(editable);
        lowerToleranceField.setEditable(editable);
        upperToleranceField.setEditable(editable);
    }

    private record DeletePlanPreparation(InspectionPlan plan, List<InspectionLotSummary> affectedLots) {
    }
}
