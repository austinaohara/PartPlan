package viewmodel;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import model.InspectionLot;
import model.InspectionLotSummary;
import model.InspectionPlan;
import service.InspectionLotDatabaseService;
import service.PlanStorageService;

public class InspectionLotBrowserViewModel {
    private final PlanStorageService planStorageService = new PlanStorageService();
    private final InspectionLotDatabaseService lotDatabaseService = new InspectionLotDatabaseService();
    private final ObservableList<InspectionPlan> savedPlans = FXCollections.observableArrayList();
    private final ObservableList<InspectionLotSummary> savedLots = FXCollections.observableArrayList();

    public InspectionLotBrowserViewModel() {
        refresh();
    }

    public ObservableList<InspectionPlan> getSavedPlans() {
        return savedPlans;
    }

    public ObservableList<InspectionLotSummary> getSavedLots() {
        return savedLots;
    }

    public void refresh() {
        savedPlans.setAll(planStorageService.loadPlans());
        savedLots.setAll(lotDatabaseService.loadLotSummaries());
    }

    public InspectionLot createLot(InspectionPlan selectedPlan, String proposedLotName, int proposedLotSize) {
        if (selectedPlan == null) {
            return null;
        }

        InspectionPlan fullPlan = planStorageService.loadPlan(selectedPlan.getId());
        InspectionLot createdLot = lotDatabaseService.createLot(proposedLotName, fullPlan, proposedLotSize);
        refresh();
        return createdLot;
    }
}
