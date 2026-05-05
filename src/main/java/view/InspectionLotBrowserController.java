package view;

import app.AppContext;
import app.AppMenuSupport;
import app.BackgroundTaskRunner;
import app.UserFacingErrorMessages;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.util.StringConverter;
import model.InspectionLot;
import model.InspectionLotSummary;
import model.InspectionPlan;
import service.auth.AuthService;
import viewmodel.InspectionLotBrowserViewModel;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

public class InspectionLotBrowserController {
    private static final int MAX_LOT_SIZE = 1000;
    private static final DateTimeFormatter UPDATED_AT_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a");

    private final InspectionLotBrowserViewModel viewModel;
    private final AuthService authService;
    private final BooleanProperty repositoryBusy = new SimpleBooleanProperty(false);

    public InspectionLotBrowserController(AppContext appContext) {
        this.authService = appContext.getAuthService();
        this.viewModel = new InspectionLotBrowserViewModel(
                appContext.getPlanRepository(),
                appContext.getLotRepository()
        );
    }

    @FXML
    private BorderPane root;
    @FXML
    private TableView<InspectionLotSummary> savedLotsTableView;
    @FXML
    private TableColumn<InspectionLotSummary, String> lotNameColumn;
    @FXML
    private TableColumn<InspectionLotSummary, String> lotPlanColumn;
    @FXML
    private TableColumn<InspectionLotSummary, Number> lotSizeColumn;
    @FXML
    private TableColumn<InspectionLotSummary, String> lotUpdatedColumn;
    @FXML
    private Button openLotButton;
    @FXML
    private Button deleteLotButton;
    @FXML
    private Button upversionLotButton;
    @FXML
    private Button createLotButton;
    @FXML
    private Label savedLotCountLabel;

    @FXML
    private void initialize() {
        AppMenuSupport.install(root, AppMenuSupport.MenuContext.LOT_BROWSER, new AppMenuSupport.MenuCallbacks(
                this::signOutFromMenu,
                this::openFirebaseSettingsFromMenu,
                () -> AppMenuSupport.openOpenAiSettingsWindow(root)
        ));
        bindMenuActions();
        root.disableProperty().bind(repositoryBusy);
        configureSavedLotsTable();
        bindViewModel();
        savedLotsTableView.setPlaceholder(new Label("Loading inspection lots..."));
        refreshBrowserDataAsync(null);
    }

    public void selectLot(String lotId) {
        if (lotId == null || lotId.isBlank()) {
            return;
        }

        viewModel.getSavedLots().stream()
                .filter(lot -> lot.getId().equals(lotId))
                .findFirst()
                .ifPresent(lot -> {
                    savedLotsTableView.getSelectionModel().select(lot);
                    savedLotsTableView.scrollTo(lot);
                });
    }

    @FXML
    private void onRefreshData() {
        refreshBrowserDataAsync(getSelectedLotId());
    }

    @FXML
    private void onOpenLot() throws IOException {
        InspectionLotSummary selectedLot = savedLotsTableView.getSelectionModel().getSelectedItem();
        if (selectedLot == null) {
            showInformation("Select an inspection lot first.");
            return;
        }

        openPartEditor(savedLotsTableView, selectedLot.getId());
    }

    @FXML
    private void onDeleteLot() {
        InspectionLotSummary selectedLot = savedLotsTableView.getSelectionModel().getSelectedItem();
        if (selectedLot == null) {
            showInformation("Select an inspection lot first.");
            return;
        }
        deleteLot(selectedLot);
    }

    @FXML
    private void onUpversionLot() {
        InspectionLotSummary selectedLot = savedLotsTableView.getSelectionModel().getSelectedItem();
        if (selectedLot == null) {
            showInformation("Select an inspection lot first.");
            return;
        }
        upversionLot(selectedLot);
    }

    private void upversionLot(InspectionLotSummary selectedLot) {
        InspectionPlan targetPlan = viewModel.findLatestUpversionTarget(selectedLot);
        if (targetPlan == null) {
            showInformation("No newer completed plan version is available for this inspection lot.");
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Upversion Inspection Lot");
        alert.setHeaderText("Move selected inspection lot to a newer plan version?");
        alert.setContentText(buildUpversionMessage(selectedLot, targetPlan));
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return;
        }

        repositoryBusy.set(true);
        BackgroundTaskRunner.run("lot-browser-upversion", () -> {
            InspectionLot updatedLot = viewModel.upversionLotInRepository(selectedLot);
            InspectionLotBrowserViewModel.BrowserData browserData = viewModel.loadBrowserData();
            return new LotBrowserMutationResult(updatedLot, browserData);
        }, resultData -> {
            repositoryBusy.set(false);
            viewModel.applyBrowserData(resultData.browserData());
            updateSavedLotCount();
            InspectionLot updatedLot = resultData.lot();
            if (updatedLot != null) {
                selectLot(updatedLot.getId());
                showInformation("Inspection lot moved to " + formatPlanReference(updatedLot.getPlanName(), updatedLot.getPlanVersion()) + ".");
            }
        }, failure -> {
            repositoryBusy.set(false);
            showFailure(failure, "Unable to upversion the inspection lot.");
        });
    }

    @FXML
    private void onCreateLot() {
        NewLotRequest request = promptForNewLot();
        if (request == null) {
            return;
        }
        repositoryBusy.set(true);
        BackgroundTaskRunner.run("lot-create", () -> {
            InspectionLot createdLot = viewModel.createLotInRepository(
                    request.plan(),
                    request.lotName(),
                    request.lotSize()
            );
            InspectionLotBrowserViewModel.BrowserData browserData = viewModel.loadBrowserData();
            return new LotBrowserMutationResult(createdLot, browserData);
        }, resultData -> {
            repositoryBusy.set(false);
            viewModel.applyBrowserData(resultData.browserData());
            updateSavedLotCount();

            InspectionLot createdLot = resultData.lot();
            if (createdLot == null) {
                return;
            }

            try {
                openPartEditor(createLotButton, createdLot.getId());
            } catch (IOException exception) {
                showInformation("Inspection lot created, but the part editor could not be opened.");
            }
        }, failure -> {
            repositoryBusy.set(false);
            showFailure(failure, "Unable to create the inspection lot.");
        });
    }

    @FXML
    private void onReturnToHub() throws IOException {
        AppNavigator.swapRoot(savedLotsTableView, "/fxml/welcome.fxml", "PartPlan");
    }

    private void configureSavedLotsTable() {
        savedLotsTableView.setItems(viewModel.getSavedLots());
        savedLotsTableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        savedLotsTableView.setPlaceholder(new Label("No inspection lots have been created yet."));
        savedLotsTableView.setOnKeyPressed(this::handleSavedLotsTableKeyPressed);

        lotNameColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getName()));
        lotPlanColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(formatPlanReference(data.getValue().getPlanName(), data.getValue().getPlanVersion())));
        lotSizeColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getLotSize()));
        lotUpdatedColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(formatTimestamp(data.getValue().getUpdatedAt())));

        savedLotsTableView.setRowFactory(tableView -> {
            TableRow<InspectionLotSummary> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    try {
                        openPartEditor(row, row.getItem().getId());
                    } catch (IOException exception) {
                        throw new IllegalStateException("Unable to open inspection lot.", exception);
                    }
                }
            });
            return row;
        });
    }

    private void handleSavedLotsTableKeyPressed(KeyEvent event) {
        if (event.getCode() != KeyCode.DELETE || event.isControlDown() || event.isAltDown() || event.isMetaDown()) {
            return;
        }

        if (savedLotsTableView.getSelectionModel().getSelectedItem() == null) {
            return;
        }

        deleteLot(savedLotsTableView.getSelectionModel().getSelectedItem());
        event.consume();
    }

    private void bindViewModel() {
        openLotButton.disableProperty().bind(savedLotsTableView.getSelectionModel().selectedItemProperty().isNull());
        deleteLotButton.disableProperty().bind(savedLotsTableView.getSelectionModel().selectedItemProperty().isNull());
        createLotButton.disableProperty().bind(Bindings.isEmpty(viewModel.getSavedPlans()));
        viewModel.getSavedLots().addListener((javafx.collections.ListChangeListener<InspectionLotSummary>) change -> updateSavedLotCount());
        savedLotsTableView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> updateUpversionActionState());
        updateUpversionActionState();
    }

    private void updateSavedLotCount() {
        int lotCount = viewModel.getSavedLots().size();
        savedLotCountLabel.setText("%d saved %s".formatted(lotCount, lotCount == 1 ? "lot" : "lots"));
    }

    private String getSelectedLotId() {
        InspectionLotSummary selectedLot = savedLotsTableView.getSelectionModel().getSelectedItem();
        return selectedLot == null ? "" : selectedLot.getId();
    }

    private void openPartEditor(Node source, String lotId) throws IOException {
        AppNavigator.swapRoot(source, "/fxml/part-editor.fxml", "PartPlan - Part Editor", loader -> {
            PartEditorController controller = loader.getController();
            controller.loadLot(lotId);
        });
    }

    private String formatTimestamp(LocalDateTime value) {
        return value == null ? "" : UPDATED_AT_FORMAT.format(value);
    }

    private String displayPlanName(InspectionPlan plan) {
        if (plan == null || plan.getName() == null || plan.getName().isBlank()) {
            return "Untitled Plan";
        }
        String name = plan.getName().trim();
        if (plan.getVersion() <= 0) {
            return name;
        }
        return name + " v" + plan.getVersion();
    }

    private void deleteLot(InspectionLotSummary selectedLot) {
        if (selectedLot == null) {
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Inspection Lot");
        alert.setHeaderText("Delete selected inspection lot?");
        alert.setContentText(selectedLot.getName());
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return;
        }

        repositoryBusy.set(true);
        BackgroundTaskRunner.run("lot-delete", () -> {
            viewModel.deleteLotInRepository(selectedLot);
            return viewModel.loadBrowserData();
        }, browserData -> {
            repositoryBusy.set(false);
            viewModel.applyBrowserData(browserData);
            updateSavedLotCount();
        }, failure -> {
            repositoryBusy.set(false);
            showFailure(failure, "Unable to delete the inspection lot.");
        });
    }

    private String formatPlanReference(String planName, int planVersion) {
        String name = planName == null || planName.isBlank() ? "Untitled Plan" : planName.trim();
        if (planVersion <= 0) {
            return name;
        }
        return name + " v" + planVersion;
    }

    private InspectionLotSummary promptForLotSelection() {
        if (viewModel.getSavedLots().isEmpty()) {
            showInformation("There are no saved inspection lots to open.");
            return null;
        }

        ListView<InspectionLotSummary> lotListView = new ListView<>(viewModel.getSavedLots());
        lotListView.setPrefWidth(460.0);
        lotListView.setPrefHeight(320.0);
        lotListView.setCellFactory(listView -> new ListCell<>() {
            @Override
            protected void updateItem(InspectionLotSummary item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null
                        ? null
                        : item.getName() + " (" + formatPlanReference(item.getPlanName(), item.getPlanVersion()) + ")");
            }
        });

        InspectionLotSummary currentSelection = savedLotsTableView.getSelectionModel().getSelectedItem();
        if (currentSelection != null) {
            lotListView.getSelectionModel().select(currentSelection);
            lotListView.scrollTo(currentSelection);
        }

        Dialog<InspectionLotSummary> dialog = new Dialog<>();
        dialog.setTitle("Open Inspection Lot");
        dialog.setHeaderText("Choose a lot to open");
        dialog.getDialogPane().setContent(lotListView);
        ButtonType openButtonType = new ButtonType("Open", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(openButtonType, ButtonType.CANCEL);
        Node openButton = dialog.getDialogPane().lookupButton(openButtonType);
        openButton.disableProperty().bind(lotListView.getSelectionModel().selectedItemProperty().isNull());

        lotListView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && lotListView.getSelectionModel().getSelectedItem() != null) {
                dialog.setResult(lotListView.getSelectionModel().getSelectedItem());
                dialog.close();
            }
        });

        dialog.setResultConverter(buttonType -> buttonType == openButtonType
                ? lotListView.getSelectionModel().getSelectedItem()
                : null);
        return dialog.showAndWait().orElse(null);
    }

    private NewLotRequest promptForNewLot() {
        if (viewModel.getSavedPlans().isEmpty()) {
            showInformation("There are no completed plans available for a new inspection lot.");
            return null;
        }

        ComboBox<InspectionPlan> planSelector = new ComboBox<>(viewModel.getSavedPlans());
        planSelector.setPrefWidth(320.0);
        planSelector.setConverter(new StringConverter<>() {
            @Override
            public String toString(InspectionPlan plan) {
                return plan == null ? "" : displayPlanName(plan);
            }

            @Override
            public InspectionPlan fromString(String string) {
                return null;
            }
        });
        planSelector.getSelectionModel().selectFirst();

        TextField lotNameInput = new TextField();
        lotNameInput.setPromptText("Inspection lot name");

        Spinner<Integer> lotSizeInput = new Spinner<>(1, MAX_LOT_SIZE, 5);
        lotSizeInput.setEditable(true);

        javafx.scene.layout.VBox content = new javafx.scene.layout.VBox(10.0,
                new Label("Completed Plan"),
                planSelector,
                new Label("Lot Name"),
                lotNameInput,
                new Label("Lot Size"),
                lotSizeInput
        );

        Dialog<NewLotRequest> dialog = new Dialog<>();
        dialog.setTitle("New Inspection Lot");
        dialog.setHeaderText("Choose a completed plan and lot details");
        dialog.getDialogPane().setContent(content);
        ButtonType createButtonType = new ButtonType("Create", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createButtonType, ButtonType.CANCEL);
        Node createButton = dialog.getDialogPane().lookupButton(createButtonType);
        createButton.disableProperty().bind(planSelector.getSelectionModel().selectedItemProperty().isNull());

        dialog.setResultConverter(buttonType -> buttonType == createButtonType
                ? new NewLotRequest(
                planSelector.getSelectionModel().getSelectedItem(),
                lotNameInput.getText(),
                lotSizeInput.getValue()
        )
                : null);
        return dialog.showAndWait().orElse(null);
    }

    private String buildUpversionMessage(InspectionLotSummary lot, InspectionPlan targetPlan) {
        return """
                Lot: %s
                Current plan: %s
                New plan: %s

                Measurements and comments are preserved for matching bubble IDs. New bubbles will start blank, and removed bubbles will be dropped from the lot.
                """.formatted(
                lot.getName(),
                formatPlanReference(lot.getPlanName(), lot.getPlanVersion()),
                displayPlanName(targetPlan)
        );
    }

    private void showInformation(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Inspection Lots");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showFailure(Throwable failure, String fallbackMessage) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Inspection Lots");
        alert.setHeaderText("Action failed");
        alert.setContentText(UserFacingErrorMessages.format(failure, fallbackMessage));
        alert.showAndWait();
    }

    private void updateUpversionActionState() {
        InspectionLotSummary selectedLot = savedLotsTableView.getSelectionModel().getSelectedItem();
        upversionLotButton.setDisable(selectedLot == null || viewModel.findLatestUpversionTarget(selectedLot) == null);
    }

    private void refreshBrowserDataAsync(String selectedLotId) {
        repositoryBusy.set(true);
        BackgroundTaskRunner.run("lot-browser-refresh", viewModel::loadBrowserData, browserData -> {
            repositoryBusy.set(false);
            viewModel.applyBrowserData(browserData);
            updateSavedLotCount();
            savedLotsTableView.setPlaceholder(new Label("No inspection lots have been created yet."));
            if (selectedLotId != null && !selectedLotId.isBlank()) {
                selectLot(selectedLotId);
            }
        }, failure -> {
            repositoryBusy.set(false);
            savedLotsTableView.setPlaceholder(new Label("Unable to load inspection lots."));
            showFailure(failure, "Unable to load inspection lots.");
        });
    }

    private void bindMenuActions() {
        AppMenuSupport.bindAction(root, AppMenuSupport.MenuAction.LOT_CREATE_LOT, this::onCreateLot);
        AppMenuSupport.bindAction(root, AppMenuSupport.MenuAction.LOT_OPEN_SELECTED_LOT, this::onOpenLotFromMenu);
        AppMenuSupport.bindAction(root, AppMenuSupport.MenuAction.LOT_DELETE_LOT, this::onDeleteLotFromMenu);
        AppMenuSupport.bindAction(root, AppMenuSupport.MenuAction.LOT_UPVERSION_LOT, this::onUpversionLotFromMenu);
        AppMenuSupport.bindAction(root, AppMenuSupport.MenuAction.NAV_HOME, this::returnToHubFromMenu);
        AppMenuSupport.bindAction(root, AppMenuSupport.MenuAction.TOOLS_REFRESH_REMOTE_DATA, this::onRefreshData);
    }

    private void onOpenLotFromMenu() {
        try {
            InspectionLotSummary selectedLot = promptForLotSelection();
            if (selectedLot == null) {
                return;
            }
            openPartEditor(savedLotsTableView, selectedLot.getId());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to open the selected inspection lot.", exception);
        }
    }

    private void onDeleteLotFromMenu() {
        InspectionLotSummary selectedLot = promptForLotSelection();
        if (selectedLot == null) {
            return;
        }
        deleteLot(selectedLot);
    }

    private void onUpversionLotFromMenu() {
        InspectionLotSummary selectedLot = promptForLotSelection();
        if (selectedLot == null) {
            return;
        }
        upversionLot(selectedLot);
    }

    private void signOutFromMenu() {
        try {
            authService.signOut();
            AppNavigator.swapRoot(root, "/fxml/login.fxml", "PartPlan - Sign In");
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to sign out.", exception);
        }
    }

    private void returnToHubFromMenu() {
        try {
            AppNavigator.swapRoot(root, "/fxml/welcome.fxml", "PartPlan");
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to return to the hub.", exception);
        }
    }

    private void openFirebaseSettingsFromMenu() {
        try {
            AppNavigator.swapRoot(root, "/fxml/firebase-config.fxml", "PartPlan - Firebase Setup");
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to open the Firebase settings screen.", exception);
        }
    }

    private record LotBrowserMutationResult(InspectionLot lot, InspectionLotBrowserViewModel.BrowserData browserData) {
    }

    private record NewLotRequest(InspectionPlan plan, String lotName, int lotSize) {
    }
}
