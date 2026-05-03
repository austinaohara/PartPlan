package service.repository;

import model.InspectionPlan;

import java.util.List;

public interface PlanRepository {
    void savePlan(InspectionPlan plan);

    List<InspectionPlan> loadPlans();

    InspectionPlan loadPlan(String planId);

    void deletePlan(String planId);
}
