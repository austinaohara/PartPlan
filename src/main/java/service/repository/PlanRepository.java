package service.repository;

import model.InspectionPlan;

import java.util.List;

public interface PlanRepository {
    void savePlan(InspectionPlan plan);

    List<InspectionPlan> loadPlans();

    List<InspectionPlan> loadCompletePlans();

    InspectionPlan loadPlan(String planId);

    InspectionPlan completePlan(String planId);

    InspectionPlan createRevision(String planId);

    void deletePlan(String planId);
}
