package service.memory;

import model.InspectionPlan;
import service.repository.PlanRepository;
import service.session.SessionManager;
import service.util.ModelCopies;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InMemoryPlanRepository implements PlanRepository {
    private final SessionManager sessionManager;
    private final Map<String, Map<String, InspectionPlan>> plansByUser = new HashMap<>();

    public InMemoryPlanRepository(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    public synchronized void savePlan(InspectionPlan plan) {
        if (plan == null) {
            throw new IllegalArgumentException("plan must not be null");
        }

        plan.setUpdatedAt(LocalDateTime.now());
        plansForCurrentUser().put(plan.getId(), ModelCopies.copyPlan(plan));
    }

    @Override
    public synchronized List<InspectionPlan> loadPlans() {
        return plansForCurrentUser().values().stream()
                .map(ModelCopies::copyPlan)
                .sorted(Comparator.comparing(InspectionPlan::getUpdatedAt).reversed())
                .toList();
    }

    @Override
    public synchronized InspectionPlan loadPlan(String planId) {
        InspectionPlan plan = plansForCurrentUser().get(planId);
        if (plan == null) {
            throw new IllegalStateException("Saved plan file was not found.");
        }
        return ModelCopies.copyPlan(plan);
    }

    @Override
    public synchronized void deletePlan(String planId) {
        plansForCurrentUser().remove(planId);
    }

    private Map<String, InspectionPlan> plansForCurrentUser() {
        String uid = sessionManager.requireCurrentSession().getUid();
        return plansByUser.computeIfAbsent(uid, ignored -> new HashMap<>());
    }
}
