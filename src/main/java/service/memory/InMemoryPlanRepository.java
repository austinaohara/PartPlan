package service.memory;

import model.InspectionPlan;
import model.PlanStatus;
import service.repository.PlanRepository;
import service.session.SessionManager;
import service.util.ModelCopies;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

        if (!plan.isPending()) {
            throw new IllegalStateException("Complete plans are read-only. Create a revision to make changes.");
        }

        InspectionPlan existing = plansForCurrentUser().get(plan.getId());
        if (existing != null && existing.isComplete()) {
            throw new IllegalStateException("Complete plans are read-only. Create a revision to make changes.");
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
    public synchronized List<InspectionPlan> loadCompletePlans() {
        return plansForCurrentUser().values().stream()
                .filter(InspectionPlan::isComplete)
                .map(ModelCopies::copyPlan)
                .sorted(Comparator.comparing(InspectionPlan::getUpdatedAt).reversed())
                .toList();
    }

    @Override
    public synchronized InspectionPlan loadPlan(String planId) {
        return ModelCopies.copyPlan(requirePlan(planId));
    }

    @Override
    public synchronized InspectionPlan completePlan(String planId) {
        InspectionPlan storedPlan = requirePlan(planId);
        if (!storedPlan.isPending()) {
            throw new IllegalStateException("Only pending plans can be completed.");
        }

        int nextVersion = nextCompletedVersion(storedPlan.getFamilyId());
        storedPlan.markComplete(Math.max(nextVersion, storedPlan.getVersion()), LocalDateTime.now());
        return ModelCopies.copyPlan(storedPlan);
    }

    @Override
    public synchronized InspectionPlan createRevision(String planId) {
        InspectionPlan storedPlan = requirePlan(planId);
        if (!storedPlan.isComplete()) {
            throw new IllegalStateException("Only complete plans can create a revision.");
        }

        InspectionPlan existingDraft = findPendingRevision(storedPlan.getFamilyId());
        if (existingDraft != null) {
            return ModelCopies.copyPlan(existingDraft);
        }

        int nextVersion = nextCompletedVersion(storedPlan.getFamilyId());
        InspectionPlan revision = new InspectionPlan(
                UUID.randomUUID().toString(),
                storedPlan.getFamilyId(),
                storedPlan.getName(),
                storedPlan.getPartNumber(),
                storedPlan.getRevision(),
                storedPlan.getDescription(),
                ModelCopies.copyDrawing(storedPlan.getDrawing()),
                storedPlan.getPages().stream().map(ModelCopies::copyPage).toList(),
                storedPlan.getBubbles().stream().map(ModelCopies::copyBubble).toList(),
                nextVersion,
                PlanStatus.PENDING,
                null,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        plansForCurrentUser().put(revision.getId(), revision);
        return ModelCopies.copyPlan(revision);
    }

    @Override
    public synchronized void deletePlan(String planId) {
        InspectionPlan storedPlan = requirePlan(planId);
        if (storedPlan.isComplete()) {
            throw new IllegalStateException("Complete plans cannot be deleted.");
        }
        plansForCurrentUser().remove(planId);
    }

    private InspectionPlan requirePlan(String planId) {
        InspectionPlan plan = plansForCurrentUser().get(planId);
        if (plan == null) {
            throw new IllegalStateException("Saved plan file was not found.");
        }
        return plan;
    }

    private InspectionPlan findPendingRevision(String familyId) {
        return plansForCurrentUser().values().stream()
                .filter(plan -> familyId.equals(plan.getFamilyId()) && plan.getStatus() == PlanStatus.PENDING)
                .max(Comparator.comparing(InspectionPlan::getUpdatedAt))
                .orElse(null);
    }

    private int nextCompletedVersion(String familyId) {
        return plansForCurrentUser().values().stream()
                .filter(plan -> familyId.equals(plan.getFamilyId()) && plan.isComplete())
                .mapToInt(InspectionPlan::getVersion)
                .max()
                .orElse(0) + 1;
    }

    private Map<String, InspectionPlan> plansForCurrentUser() {
        String uid = sessionManager.requireCurrentSession().getUid();
        return plansByUser.computeIfAbsent(uid, ignored -> new HashMap<>());
    }
}
