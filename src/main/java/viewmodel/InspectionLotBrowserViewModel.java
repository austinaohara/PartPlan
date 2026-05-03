package viewmodel;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import model.InspectionLot;
import model.InspectionLotSummary;
import model.InspectionPlan;
import service.repository.LotRepository;
import service.repository.PlanRepository;

public class InspectionLotBrowserViewModel {
    private final PlanRepository planRepository;
    private final LotRepository lotRepository;
    private final ObservableList<InspectionPlan> savedPlans = FXCollections.observableArrayList();
    private final ObservableList<InspectionLotSummary> savedLots = FXCollections.observableArrayList();

    public InspectionLotBrowserViewModel(PlanRepository planRepository, LotRepository lotRepository) {
        this.planRepository = planRepository;
        this.lotRepository = lotRepository;
        refresh();
    }

    public ObservableList<InspectionPlan> getSavedPlans() {
        return savedPlans;
    }

    public ObservableList<InspectionLotSummary> getSavedLots() {
        return savedLots;
    }

    public void refresh() {
        savedPlans.setAll(planRepository.loadCompletePlans());
        savedLots.setAll(lotRepository.loadLotSummaries());
    }

    public InspectionPlan findLatestUpversionTarget(InspectionLotSummary selectedLot) {
        if (selectedLot == null) {
            return null;
        }

        return planRepository.loadCompletePlans().stream()
                .filter(plan -> selectedLot.getPlanFamilyId().equals(plan.getFamilyId()))
                .filter(plan -> plan.getVersion() > selectedLot.getPlanVersion())
                .max(java.util.Comparator.comparingInt(InspectionPlan::getVersion))
                .orElse(null);
    }

    public InspectionLot createLot(InspectionPlan selectedPlan, String proposedLotName, int proposedLotSize) {
        if (selectedPlan == null) {
            return null;
        }

        InspectionPlan fullPlan = planRepository.loadPlan(selectedPlan.getId());
        InspectionLot createdLot = lotRepository.createLot(proposedLotName, fullPlan, proposedLotSize);
        refresh();
        return createdLot;
    }

    public InspectionLot upversionLot(InspectionLotSummary selectedLot) {
        if (selectedLot == null) {
            return null;
        }

        InspectionPlan targetPlan = findLatestUpversionTarget(selectedLot);
        if (targetPlan == null) {
            throw new IllegalStateException("No newer completed plan version is available for this inspection lot.");
        }

        InspectionPlan fullTargetPlan = planRepository.loadPlan(targetPlan.getId());
        InspectionLot updatedLot = lotRepository.upversionLot(selectedLot.getId(), fullTargetPlan);
        refresh();
        return updatedLot;
    }

    public void deleteLot(InspectionLotSummary selectedLot) {
        if (selectedLot == null) {
            return;
        }

        lotRepository.deleteLot(selectedLot.getId());
        refresh();
    }
}
