package viewmodel;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import model.InspectionLot;
import model.InspectionLotSummary;
import model.InspectionPlan;
import service.repository.LotRepository;
import service.repository.PlanRepository;

import java.util.List;

public class InspectionLotBrowserViewModel {
    private final PlanRepository planRepository;
    private final LotRepository lotRepository;
    private final ObservableList<InspectionPlan> savedPlans = FXCollections.observableArrayList();
    private final ObservableList<InspectionLotSummary> savedLots = FXCollections.observableArrayList();

    public InspectionLotBrowserViewModel(PlanRepository planRepository, LotRepository lotRepository) {
        this.planRepository = planRepository;
        this.lotRepository = lotRepository;
    }

    public ObservableList<InspectionPlan> getSavedPlans() {
        return savedPlans;
    }

    public ObservableList<InspectionLotSummary> getSavedLots() {
        return savedLots;
    }

    public void refresh() {
        applyBrowserData(loadBrowserData());
    }

    public InspectionPlan findLatestUpversionTarget(InspectionLotSummary selectedLot) {
        if (selectedLot == null) {
            return null;
        }

        return savedPlans.stream()
                .filter(plan -> selectedLot.getPlanFamilyId().equals(plan.getFamilyId()))
                .filter(plan -> plan.getVersion() > selectedLot.getPlanVersion())
                .max(java.util.Comparator.comparingInt(InspectionPlan::getVersion))
                .orElse(null);
    }

    public InspectionLot createLot(InspectionPlan selectedPlan, String proposedLotName, int proposedLotSize) {
        InspectionLot createdLot = createLotInRepository(selectedPlan, proposedLotName, proposedLotSize);
        applyBrowserData(loadBrowserData());
        return createdLot;
    }

    public InspectionLot createLotInRepository(InspectionPlan selectedPlan, String proposedLotName, int proposedLotSize) {
        if (selectedPlan == null) {
            return null;
        }

        InspectionPlan fullPlan = planRepository.loadPlan(selectedPlan.getId());
        return lotRepository.createLot(proposedLotName, fullPlan, proposedLotSize);
    }

    public InspectionLot upversionLot(InspectionLotSummary selectedLot) {
        InspectionLot updatedLot = upversionLotInRepository(selectedLot);
        applyBrowserData(loadBrowserData());
        return updatedLot;
    }

    public InspectionLot upversionLotInRepository(InspectionLotSummary selectedLot) {
        if (selectedLot == null) {
            return null;
        }

        InspectionPlan targetPlan = findLatestUpversionTarget(selectedLot);
        if (targetPlan == null) {
            throw new IllegalStateException("No newer completed plan version is available for this inspection lot.");
        }

        InspectionPlan fullTargetPlan = planRepository.loadPlan(targetPlan.getId());
        return lotRepository.upversionLot(selectedLot.getId(), fullTargetPlan);
    }

    public void deleteLot(InspectionLotSummary selectedLot) {
        deleteLotInRepository(selectedLot);
        applyBrowserData(loadBrowserData());
    }

    public void deleteLotInRepository(InspectionLotSummary selectedLot) {
        if (selectedLot == null) {
            return;
        }

        lotRepository.deleteLot(selectedLot.getId());
    }

    public BrowserData loadBrowserData() {
        return new BrowserData(
                List.copyOf(planRepository.loadCompletePlans()),
                List.copyOf(lotRepository.loadLotSummaries())
        );
    }

    public void applyBrowserData(BrowserData browserData) {
        if (browserData == null) {
            savedPlans.clear();
            savedLots.clear();
            return;
        }

        savedPlans.setAll(browserData.savedPlans());
        savedLots.setAll(browserData.savedLots());
    }

    public record BrowserData(List<InspectionPlan> savedPlans, List<InspectionLotSummary> savedLots) {
    }
}
