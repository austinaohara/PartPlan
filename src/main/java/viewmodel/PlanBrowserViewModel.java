package viewmodel;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import model.InspectionLotSummary;
import model.InspectionPlan;
import service.repository.LotRepository;
import service.repository.PlanRepository;

import java.util.Comparator;
import java.util.List;

public class PlanBrowserViewModel {
    private final PlanRepository planRepository;
    private final LotRepository lotRepository;
    private final ObservableList<InspectionPlan> savedPlans = FXCollections.observableArrayList();

    public PlanBrowserViewModel(PlanRepository planRepository, LotRepository lotRepository) {
        this.planRepository = planRepository;
        this.lotRepository = lotRepository;
    }

    public ObservableList<InspectionPlan> getSavedPlans() {
        return savedPlans;
    }

    public List<InspectionPlan> loadPlans() {
        return List.copyOf(planRepository.loadPlans());
    }

    public void applyPlans(List<InspectionPlan> plans) {
        savedPlans.setAll(plans == null
                ? List.of()
                : plans.stream()
                .sorted(Comparator.comparing(InspectionPlan::getUpdatedAt).reversed())
                .toList());
    }

    public InspectionPlan loadPlan(String planId) {
        return planRepository.loadPlan(planId);
    }

    public List<InspectionLotSummary> loadAffectedLots(String planId) {
        if (planId == null || planId.isBlank()) {
            return List.of();
        }
        return lotRepository.loadLotSummariesForPlan(planId);
    }

    public void deletePlan(String planId) {
        if (planId == null || planId.isBlank()) {
            return;
        }
        lotRepository.deleteLotsForPlan(planId);
        planRepository.deletePlan(planId);
    }

    public void renamePlan(String planId, String proposedName) {
        if (planId == null || planId.isBlank()) {
            return;
        }

        InspectionPlan plan = planRepository.loadPlan(planId);
        if (plan == null) {
            return;
        }

        String normalizedName = normalizePlanName(proposedName, plan.getName());
        plan.rename(normalizedName);
        planRepository.savePlan(plan);
    }

    private String normalizePlanName(String proposedName, String fallback) {
        if (proposedName == null || proposedName.isBlank()) {
            return fallback == null || fallback.isBlank() ? "New Inspection Plan" : fallback.trim();
        }
        return proposedName.trim();
    }
}
