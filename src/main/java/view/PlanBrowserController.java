package view;

import app.AppContext;
import app.AppMenuSupport;
import app.BackgroundTaskRunner;
import app.UserFacingErrorMessages;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import model.InspectionLotSummary;
import model.InspectionPlan;
import service.auth.AuthService;
import viewmodel.PlanBrowserViewModel;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

public class PlanBrowserController {
    private static final DateTimeFormatter UPDATED_AT_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a");

    private final PlanBrowserViewModel viewModel;
    private final AuthService authService;
    private final BooleanProperty repositoryBusy = new SimpleBooleanProperty(false);
    private String pendingPlanSelectionId;

    public PlanBrowserController(AppContext appContext) {
        this.authService = appContext.getAuthService();
        this.viewModel = new PlanBrowserViewModel(
                appContext.getPlanRepository(),
                appContext.getLotRepository()
        );
    }

    @FXML
    private BorderPane root;
    @FXML
    private TableView<InspectionPlan> savedPlansTableView;
    @FXML
    private TableColumn<InspectionPlan, String> planNameColumn;
    @FXML
    private TableColumn<InspectionPlan, String> planStatusColumn;
    @FXML
    private TableColumn<InspectionPlan, String> planVersionColumn;
    @FXML
    private TableColumn<InspectionPlan, String> planUpdatedColumn;
    @FXML
    private Button openPlanButton;
    @FXML
    private Button renamePlanButton;
    @FXML
    private Button deletePlanButton;
    @FXML
    private Button createPlanButton;
    @FXML
    private Label savedPlanCountLabel;

    @FXML
    private void initialize() {
        AppMenuSupport.install(root, AppMenuSupport.MenuContext.PLAN_BROWSER, new AppMenuSupport.MenuCallbacks(
                this::signOutFromMenu,
                this::openFirebaseSettingsFromMenu,
                () -> AppMenuSupport.openOpenAiSettingsWindow(root)
        ));
        bindMenuActions();
        root.disableProperty().bind(repositoryBusy);
        configureSavedPlansTable();
        bindViewModel();
        savedPlansTableView.setPlaceholder(new Label("Loading inspection plans..."));
        refreshPlansAsync(null);
    }

    public void selectPlan(String planId) {
        if (planId == null || planId.isBlank()) {
            return;
        }
        pendingPlanSelectionId = planId;

        viewModel.getSavedPlans().stream()
                .filter(plan -> planId.equals(plan.getId()))
                .findFirst()
                .ifPresent(plan -> {
                    savedPlansTableView.getSelectionModel().select(plan);
                    savedPlansTableView.scrollTo(plan);
                });
    }

    @FXML
    private void onRefreshData() {
        refreshPlansAsync(getSelectedPlanId());
    }

    @FXML
    private void onOpenPlan() throws IOException {
        InspectionPlan selectedPlan = savedPlansTableView.getSelectionModel().getSelectedItem();
        if (selectedPlan == null) {
            showInformation("Select an inspection plan first.");
            return;
        }
        openPlanEditor(savedPlansTableView, selectedPlan.getId());
    }

    @FXML
    private void onCreatePlan() throws IOException {
        openNewPlanEditor(createPlanButton);
    }

    @FXML
    private void onDeletePlan() {
        InspectionPlan selectedPlan = savedPlansTableView.getSelectionModel().getSelectedItem();
        if (selectedPlan == null) {
            showInformation("Select an inspection plan first.");
            return;
        }
        deletePlan(selectedPlan);
    }

    @FXML
    private void onRenamePlan() {
        InspectionPlan selectedPlan = savedPlansTableView.getSelectionModel().getSelectedItem();
        if (selectedPlan == null) {
            showInformation("Select an inspection plan first.");
            return;
        }
        renamePlan(selectedPlan);
    }

    @FXML
    private void onReturnToHub() throws IOException {
        AppNavigator.swapRoot(savedPlansTableView, "/fxml/welcome.fxml", "PartPlan");
    }

    private void configureSavedPlansTable() {
        savedPlansTableView.setItems(viewModel.getSavedPlans());
        savedPlansTableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        savedPlansTableView.setPlaceholder(new Label("No inspection plans have been created yet."));
        savedPlansTableView.setOnKeyPressed(this::handleSavedPlansTableKeyPressed);

        planNameColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(displayPlanName(data.getValue())));
        planStatusColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().isComplete() ? "Complete" : "Pending"));
        planVersionColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(displayPlanVersion(data.getValue())));
        planUpdatedColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(formatTimestamp(data.getValue().getUpdatedAt())));

        savedPlansTableView.setRowFactory(tableView -> {
            TableRow<InspectionPlan> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    try {
                        openPlanEditor(row, row.getItem().getId());
                    } catch (IOException exception) {
                        throw new IllegalStateException("Unable to open inspection plan.", exception);
                    }
                }
            });
            return row;
        });
    }

    private void handleSavedPlansTableKeyPressed(KeyEvent event) {
        if (event.getCode() != KeyCode.DELETE || event.isControlDown() || event.isAltDown() || event.isMetaDown()) {
            return;
        }
        InspectionPlan selectedPlan = savedPlansTableView.getSelectionModel().getSelectedItem();
        if (selectedPlan == null) {
            return;
        }
        deletePlan(selectedPlan);
        event.consume();
    }

    private void bindViewModel() {
        openPlanButton.disableProperty().bind(savedPlansTableView.getSelectionModel().selectedItemProperty().isNull());
        renamePlanButton.disableProperty().bind(savedPlansTableView.getSelectionModel().selectedItemProperty().isNull());
        deletePlanButton.disableProperty().bind(savedPlansTableView.getSelectionModel().selectedItemProperty().isNull());
        viewModel.getSavedPlans().addListener((javafx.collections.ListChangeListener<InspectionPlan>) change -> updateSavedPlanCount());
        updateSavedPlanCount();
    }

    private void updateSavedPlanCount() {
        int planCount = viewModel.getSavedPlans().size();
        savedPlanCountLabel.setText("%d saved %s".formatted(planCount, planCount == 1 ? "plan" : "plans"));
    }

    private String getSelectedPlanId() {
        InspectionPlan selectedPlan = savedPlansTableView.getSelectionModel().getSelectedItem();
        return selectedPlan == null ? "" : selectedPlan.getId();
    }

    private void openPlanEditor(Node source, String planId) throws IOException {
        AppNavigator.swapRoot(source, "/fxml/plan-editor.fxml", "PartPlan - Plan Editor", loader -> {
            PlanEditorController controller = loader.getController();
            controller.loadPlan(planId);
        });
    }

    private void openNewPlanEditor(Node source) throws IOException {
        AppNavigator.swapRoot(source, "/fxml/plan-editor.fxml", "PartPlan - Plan Editor");
    }

    private void deletePlan(InspectionPlan selectedPlan) {
        if (selectedPlan == null) {
            return;
        }

        repositoryBusy.set(true);
        BackgroundTaskRunner.run("plan-delete-prep", () -> new DeletePlanPreparation(
                selectedPlan,
                viewModel.loadAffectedLots(selectedPlan.getId())
        ), preparation -> {
            repositoryBusy.set(false);
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Delete Inspection Plan");
            alert.setHeaderText("Delete selected inspection plan?");
            alert.setContentText(buildDeletePlanMessage(preparation.plan(), preparation.affectedLots()));
            Optional<ButtonType> result = alert.showAndWait();
            if (result.isEmpty() || result.get() != ButtonType.OK) {
                return;
            }

            repositoryBusy.set(true);
            BackgroundTaskRunner.run("plan-delete", () -> {
                viewModel.deletePlan(preparation.plan().getId());
                return viewModel.loadPlans();
            }, plans -> {
                repositoryBusy.set(false);
                viewModel.applyPlans(plans);
                updateSavedPlanCount();
            }, failure -> {
                repositoryBusy.set(false);
                showFailure(failure, "Unable to delete the inspection plan.");
            });
        }, failure -> {
            repositoryBusy.set(false);
            showFailure(failure, "Unable to prepare plan deletion.");
        });
    }

    private void renamePlan(InspectionPlan selectedPlan) {
        if (selectedPlan == null) {
            return;
        }

        TextInputDialog dialog = new TextInputDialog(displayPlanName(selectedPlan));
        dialog.setTitle("Rename Inspection Plan");
        dialog.setHeaderText("Rename selected inspection plan");
        dialog.setContentText("Plan Name:");
        Optional<String> result = dialog.showAndWait();
        if (result.isEmpty()) {
            return;
        }

        repositoryBusy.set(true);
        String renamedPlanId = selectedPlan.getId();
        String proposedName = result.get();
        BackgroundTaskRunner.run("plan-rename", () -> {
            viewModel.renamePlan(renamedPlanId, proposedName);
            return viewModel.loadPlans();
        }, plans -> {
            repositoryBusy.set(false);
            viewModel.applyPlans(plans);
            updateSavedPlanCount();
            selectPlan(renamedPlanId);
        }, failure -> {
            repositoryBusy.set(false);
            showFailure(failure, "Unable to rename the inspection plan.");
        });
    }

    private String buildDeletePlanMessage(InspectionPlan plan, List<InspectionLotSummary> affectedLots) {
        String name = displayPlanName(plan);
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

    private InspectionPlan promptForPlanSelection() {
        if (viewModel.getSavedPlans().isEmpty()) {
            showInformation("There are no saved inspection plans to open.");
            return null;
        }

        ListView<InspectionPlan> planListView = new ListView<>(viewModel.getSavedPlans());
        planListView.setPrefWidth(460.0);
        planListView.setPrefHeight(320.0);
        planListView.setCellFactory(listView -> new ListCell<>() {
            @Override
            protected void updateItem(InspectionPlan item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null
                        ? null
                        : displayPlanName(item) + " (" + (item.isComplete() ? "Complete" : "Pending") + ", " + displayPlanVersion(item) + ")");
            }
        });

        InspectionPlan currentSelection = savedPlansTableView.getSelectionModel().getSelectedItem();
        if (currentSelection != null) {
            planListView.getSelectionModel().select(currentSelection);
            planListView.scrollTo(currentSelection);
        }

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

    private void refreshPlansAsync(String selectedPlanId) {
        repositoryBusy.set(true);
        BackgroundTaskRunner.run("plan-browser-refresh", viewModel::loadPlans, plans -> {
            repositoryBusy.set(false);
            viewModel.applyPlans(plans);
            updateSavedPlanCount();
            savedPlansTableView.setPlaceholder(new Label("No inspection plans have been created yet."));
            String targetPlanId = selectedPlanId;
            if ((targetPlanId == null || targetPlanId.isBlank()) && pendingPlanSelectionId != null && !pendingPlanSelectionId.isBlank()) {
                targetPlanId = pendingPlanSelectionId;
            }
            if (targetPlanId != null && !targetPlanId.isBlank()) {
                selectPlan(targetPlanId);
            }
        }, failure -> {
            repositoryBusy.set(false);
            savedPlansTableView.setPlaceholder(new Label("Unable to load inspection plans."));
            showFailure(failure, "Unable to load inspection plans.");
        });
    }

    private void bindMenuActions() {
        AppMenuSupport.bindAction(root, AppMenuSupport.MenuAction.FILE_NEW_PLAN, this::onCreatePlanFromMenu);
        AppMenuSupport.bindAction(root, AppMenuSupport.MenuAction.FILE_OPEN_PLAN, this::onOpenPlanFromMenu);
        AppMenuSupport.bindAction(root, AppMenuSupport.MenuAction.FILE_RENAME_PLAN, this::onRenamePlanFromMenu);
        AppMenuSupport.bindAction(root, AppMenuSupport.MenuAction.PLAN_DELETE_PLAN, this::onDeletePlanFromMenu);
        AppMenuSupport.bindAction(root, AppMenuSupport.MenuAction.NAV_HOME, this::returnToHubFromMenu);
        AppMenuSupport.bindAction(root, AppMenuSupport.MenuAction.TOOLS_REFRESH_REMOTE_DATA, this::onRefreshData);
    }

    private void onCreatePlanFromMenu() {
        try {
            openNewPlanEditor(root);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to start a new inspection plan.", exception);
        }
    }

    private void onOpenPlanFromMenu() {
        InspectionPlan selectedPlan = promptForPlanSelection();
        if (selectedPlan == null) {
            return;
        }
        try {
            openPlanEditor(root, selectedPlan.getId());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to open the selected inspection plan.", exception);
        }
    }

    private void onDeletePlanFromMenu() {
        InspectionPlan selectedPlan = promptForPlanSelection();
        if (selectedPlan == null) {
            return;
        }
        deletePlan(selectedPlan);
    }

    private void onRenamePlanFromMenu() {
        InspectionPlan selectedPlan = promptForPlanSelection();
        if (selectedPlan == null) {
            return;
        }
        renamePlan(selectedPlan);
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

    private String displayPlanName(InspectionPlan plan) {
        if (plan == null || plan.getName() == null || plan.getName().isBlank()) {
            return "Untitled Plan";
        }
        return plan.getName().trim();
    }

    private String displayPlanVersion(InspectionPlan plan) {
        if (plan == null || plan.getVersion() <= 0) {
            return "Draft";
        }
        return "v" + plan.getVersion();
    }

    private String formatTimestamp(LocalDateTime value) {
        return value == null ? "" : UPDATED_AT_FORMAT.format(value);
    }

    private void showInformation(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Inspection Plans");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showFailure(Throwable failure, String fallbackMessage) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Inspection Plans");
        alert.setHeaderText("Action failed");
        alert.setContentText(UserFacingErrorMessages.format(failure, fallbackMessage));
        alert.showAndWait();
    }

    private record DeletePlanPreparation(InspectionPlan plan, List<InspectionLotSummary> affectedLots) {
    }
}
