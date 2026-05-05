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

    public InspectionLot createLotInRepository(InspectionPlan selectedPlan, String proposedLotName, int proposedLotSize) {
        if (selectedPlan == null) {
            return null;
        }

        InspectionPlan fullPlan = planRepository.loadPlan(selectedPlan.getId());
        return lotRepository.createLot(proposedLotName, fullPlan, proposedLotSize);
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

    public void deleteLotInRepository(InspectionLotSummary selectedLot) {
        if (selectedLot == null) {
            return;
        }

        lotRepository.deleteLot(selectedLot.getId());
    }

    public void renameLotInRepository(InspectionLotSummary selectedLot, String proposedName) {
        if (selectedLot == null) {
            return;
        }

        String normalizedName = normalizeLotName(proposedName, selectedLot.getName());
        lotRepository.saveLotName(selectedLot.getId(), normalizedName);
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

    private String normalizeLotName(String proposedName, String fallback) {
        if (proposedName == null || proposedName.isBlank()) {
            return fallback == null || fallback.isBlank() ? "Inspection Lot" : fallback.trim();
        }
        return proposedName.trim();
    }
}
