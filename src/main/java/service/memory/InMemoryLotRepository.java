package service.memory;

import model.InspectionLot;
import model.InspectionLotSummary;
import model.InspectionPlan;
import model.PartBubbleDefinition;
import model.PartLot;
import model.PartRecord;
import service.repository.LotRepository;
import service.repository.PlanRepository;
import service.session.SessionManager;
import service.util.ModelCopies;
import service.util.InspectionSpecBuilder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InMemoryLotRepository implements LotRepository {
    private final SessionManager sessionManager;
    private final PlanRepository planRepository;
    private final Map<String, Map<String, StoredLot>> lotsByUser = new HashMap<>();

    public InMemoryLotRepository(SessionManager sessionManager, PlanRepository planRepository) {
        this.sessionManager = sessionManager;
        this.planRepository = planRepository;
    }

    @Override
    public synchronized List<InspectionLotSummary> loadLotSummaries() {
        return lotsForCurrentUser().values().stream()
                .map(StoredLot::toSummary)
                .map(ModelCopies::copyLotSummary)
                .sorted(Comparator.comparing(InspectionLotSummary::getUpdatedAt).reversed())
                .toList();
    }

    @Override
    public synchronized InspectionLot createLot(String proposedLotName, InspectionPlan plan, int lotSize) {
        if (plan == null) {
            throw new IllegalArgumentException("plan must not be null");
        }

        if (!plan.isComplete()) {
            throw new IllegalStateException("Inspection lots can only be created from complete plans.");
        }

        List<PartBubbleDefinition> bubbleDefinitions = InspectionSpecBuilder.buildBubbleDefinitions(plan);
        PartLot lotData = new PartLot(lotSize);
        lotData.replaceBubbles(bubbleDefinitions);

        InspectionLot lot = new InspectionLot(
                java.util.UUID.randomUUID().toString(),
                sanitizeLotName(proposedLotName, plan),
                plan.getId(),
                plan.getFamilyId(),
                displayPlanName(plan),
                plan.getVersion(),
                lotData,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        lotsForCurrentUser().put(lot.getId(), StoredLot.fromLot(lot, bubbleDefinitions));
        return ModelCopies.copyLot(lot);
    }

    @Override
    public synchronized InspectionLot loadLot(String lotId) {
        StoredLot storedLot = requireLot(lotId);
        InspectionPlan plan = requireCompletePlan(storedLot.planId);
        List<PartBubbleDefinition> bubbleDefinitions = InspectionSpecBuilder.buildBubbleDefinitions(plan);
        return restoreLot(storedLot, bubbleDefinitions);
    }

    @Override
    public synchronized void saveLotName(String lotId, String lotName) {
        StoredLot lot = requireLot(lotId);
        lot.name = lotName;
        lot.updatedAt = LocalDateTime.now();
    }

    @Override
    public synchronized void saveLotStructure(InspectionLot lot) {
        if (lot == null) {
            throw new IllegalArgumentException("lot must not be null");
        }

        StoredLot existing = requireLot(lot.getId());
        List<String> bubbleIds = requiredBubbleIds(existing.planId);
        existing.name = lot.getName();
        existing.lotData = copyStoredLotData(lot.getLotSize(), lot.getParts(), bubbleIds);
        existing.updatedAt = LocalDateTime.now();
    }

    @Override
    public synchronized void saveMeasurement(String lotId, String partId, String bubbleId, String value) {
        StoredLot lot = requireLot(lotId);
        PartRecord part = lot.lotData.getParts().stream()
                .filter(candidate -> candidate.getId().equals(partId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Inspection part was not found."));
        part.setMeasurement(bubbleId, value);
        lot.updatedAt = LocalDateTime.now();
    }

    @Override
    public synchronized void deleteLot(String lotId) {
        lotsForCurrentUser().remove(lotId);
    }

    private StoredLot requireLot(String lotId) {
        StoredLot lot = lotsForCurrentUser().get(lotId);
        if (lot == null) {
            throw new IllegalStateException("Inspection lot was not found.");
        }
        return lot;
    }

    private InspectionPlan requireCompletePlan(String planId) {
        InspectionPlan plan = planRepository.loadPlan(planId);
        if (!plan.isComplete()) {
            throw new IllegalStateException("Inspection lots can only reference complete plans.");
        }
        return plan;
    }

    private List<String> requiredBubbleIds(String planId) {
        return InspectionSpecBuilder.buildBubbleDefinitions(requireCompletePlan(planId)).stream()
                .map(PartBubbleDefinition::getId)
                .toList();
    }

    private InspectionLot restoreLot(StoredLot storedLot, List<PartBubbleDefinition> bubbleDefinitions) {
        PartLot restoredData = new PartLot(storedLot.lotData.getLotSize());
        restoredData.replaceBubbles(bubbleDefinitions);

        List<PartRecord> restoredParts = storedLot.lotData.getParts().stream()
                .map(part -> copyPartRecord(part, bubbleDefinitions))
                .toList();
        restoredData.getParts().clear();
        restoredData.getParts().addAll(restoredParts);

        return new InspectionLot(
                storedLot.id,
                storedLot.name,
                storedLot.planId,
                storedLot.planFamilyId,
                storedLot.planName,
                storedLot.planVersion,
                restoredData,
                storedLot.createdAt,
                storedLot.updatedAt
        );
    }

    private PartLot copyStoredLotData(int lotSize, List<PartRecord> sourceParts, List<String> bubbleIds) {
        PartLot storedData = new PartLot(lotSize);
        List<PartRecord> copiedParts = new ArrayList<>();
        for (int index = 0; index < lotSize; index++) {
            PartRecord sourcePart = index < sourceParts.size() ? sourceParts.get(index) : null;
            PartRecord storedPart = sourcePart == null
                    ? new PartRecord(index + 1)
                    : new PartRecord(sourcePart.getId(), sourcePart.getPartNumber());
            for (String bubbleId : bubbleIds) {
                storedPart.setMeasurement(bubbleId, sourcePart == null ? "" : sourcePart.getMeasurement(bubbleId));
            }
            copiedParts.add(storedPart);
        }
        storedData.getParts().clear();
        storedData.getParts().addAll(copiedParts);
        return storedData;
    }

    private PartRecord copyPartRecord(PartRecord part, List<PartBubbleDefinition> bubbles) {
        PartRecord copy = new PartRecord(part.getId(), part.getPartNumber());
        for (PartBubbleDefinition bubble : bubbles) {
            copy.setMeasurement(bubble.getId(), part.getMeasurement(bubble.getId()));
        }
        return copy;
    }

    private Map<String, StoredLot> lotsForCurrentUser() {
        String uid = sessionManager.requireCurrentSession().getUid();
        return lotsByUser.computeIfAbsent(uid, ignored -> new HashMap<>());
    }

    private String sanitizeLotName(String proposedLotName, InspectionPlan plan) {
        if (proposedLotName == null || proposedLotName.isBlank()) {
            return displayPlanName(plan) + " v" + Math.max(1, plan.getVersion()) + " Lot " + LocalDateTime.now().withNano(0);
        }
        return proposedLotName.trim();
    }

    private String displayPlanName(InspectionPlan plan) {
        if (plan == null || plan.getName() == null || plan.getName().isBlank()) {
            return "Untitled Plan";
        }
        return plan.getName().trim();
    }

    private static final class StoredLot {
        private final String id;
        private String name;
        private final String planId;
        private final String planFamilyId;
        private final String planName;
        private final int planVersion;
        private PartLot lotData;
        private final LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        private StoredLot(
                String id,
                String name,
                String planId,
                String planFamilyId,
                String planName,
                int planVersion,
                PartLot lotData,
                LocalDateTime createdAt,
                LocalDateTime updatedAt
        ) {
            this.id = id;
            this.name = name;
            this.planId = planId;
            this.planFamilyId = planFamilyId;
            this.planName = planName;
            this.planVersion = planVersion;
            this.lotData = lotData;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }

        private static StoredLot fromLot(InspectionLot lot, List<PartBubbleDefinition> bubbleDefinitions) {
            List<String> bubbleIds = bubbleDefinitions.stream()
                    .map(PartBubbleDefinition::getId)
                    .toList();
            PartLot storedData = new PartLot(lot.getLotSize());
            List<PartRecord> copiedParts = new ArrayList<>();
            for (int index = 0; index < lot.getLotSize(); index++) {
                PartRecord sourcePart = lot.getPart(index);
                PartRecord storedPart = sourcePart == null
                        ? new PartRecord(index + 1)
                        : new PartRecord(sourcePart.getId(), sourcePart.getPartNumber());
                for (String bubbleId : bubbleIds) {
                    storedPart.setMeasurement(bubbleId, sourcePart == null ? "" : sourcePart.getMeasurement(bubbleId));
                }
                copiedParts.add(storedPart);
            }
            storedData.getParts().clear();
            storedData.getParts().addAll(copiedParts);

            return new StoredLot(
                    lot.getId(),
                    lot.getName(),
                    lot.getPlanId(),
                    lot.getPlanFamilyId(),
                    lot.getPlanName(),
                    lot.getPlanVersion(),
                    storedData,
                    lot.getCreatedAt(),
                    lot.getUpdatedAt()
            );
        }

        private InspectionLotSummary toSummary() {
            return new InspectionLotSummary(
                    id,
                    name,
                    planId,
                    planFamilyId,
                    planName,
                    planVersion,
                    lotData.getLotSize(),
                    createdAt,
                    updatedAt
            );
        }
    }
}
