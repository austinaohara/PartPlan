package service.firestore;

import model.InspectionLot;
import model.InspectionLotSummary;
import model.InspectionPlan;
import model.PartBubbleDefinition;
import model.PartLot;
import model.PartRecord;
import service.repository.LotRepository;
import service.repository.PlanRepository;
import service.util.InspectionSpecBuilder;
import service.util.ModelCopies;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class FirestoreLotRepository implements LotRepository {
    private final FirestoreRestClient firestore;
    private final PlanRepository planRepository;

    public FirestoreLotRepository(FirestoreRestClient firestore, PlanRepository planRepository) {
        this.firestore = firestore;
        this.planRepository = planRepository;
    }

    @Override
    public List<InspectionLotSummary> loadLotSummaries() {
        return firestore.listDocuments(lotCollectionPath()).stream()
                .map(this::toLotSummary)
                .sorted(Comparator.comparing(InspectionLotSummary::getUpdatedAt).reversed())
                .map(ModelCopies::copyLotSummary)
                .toList();
    }

    @Override
    public List<InspectionLotSummary> loadLotSummariesForPlan(String planId) {
        return loadLotSummaries().stream()
                .filter(summary -> summary.getPlanId().equals(planId))
                .toList();
    }

    @Override
    public InspectionLot createLot(String proposedLotName, InspectionPlan plan, int lotSize) {
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
                UUID.randomUUID().toString(),
                sanitizeLotName(proposedLotName, plan),
                plan.getId(),
                plan.getFamilyId(),
                displayPlanName(plan),
                plan.getVersion(),
                lotData,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        upsertLot(lot);
        return ModelCopies.copyLot(lot);
    }

    @Override
    public InspectionLot loadLot(String lotId) {
        FirestoreRestClient.FirestoreDocument lotDocument = requireLot(lotId);
        InspectionLot lot = restoreLot(lotDocument);
        return ModelCopies.copyLot(lot);
    }

    @Override
    public InspectionLot upversionLot(String lotId, InspectionPlan targetPlan) {
        if (targetPlan == null) {
            throw new IllegalArgumentException("targetPlan must not be null");
        }
        if (!targetPlan.isComplete()) {
            throw new IllegalStateException("Inspection lots can only target complete plan versions.");
        }

        InspectionLot currentLot = loadLot(lotId);
        if (!currentLot.getPlanFamilyId().equals(targetPlan.getFamilyId())) {
            throw new IllegalStateException("Inspection lots can only upversion within the same plan family.");
        }
        if (targetPlan.getVersion() <= currentLot.getPlanVersion()) {
            throw new IllegalStateException("No newer completed plan version is available for this inspection lot.");
        }

        List<PartBubbleDefinition> bubbleDefinitions = InspectionSpecBuilder.buildBubbleDefinitions(targetPlan);
        List<String> bubbleIds = bubbleDefinitions.stream().map(PartBubbleDefinition::getId).toList();
        PartLot updatedLotData = copyStoredLotData(currentLot.getLotSize(), currentLot.getParts(), bubbleIds);
        InspectionLot updatedLot = new InspectionLot(
                currentLot.getId(),
                currentLot.getName(),
                targetPlan.getId(),
                targetPlan.getFamilyId(),
                displayPlanName(targetPlan),
                targetPlan.getVersion(),
                updatedLotData,
                currentLot.getCreatedAt(),
                LocalDateTime.now()
        );
        upsertLot(updatedLot);
        return restoreLot(requireLot(updatedLot.getId()));
    }

    @Override
    public void saveLotName(String lotId, String lotName) {
        InspectionLot lot = loadLot(lotId);
        lot.setName(lotName);
        lot.setUpdatedAt(LocalDateTime.now());
        upsertLot(lot);
    }

    @Override
    public void saveLotStructure(InspectionLot lot) {
        if (lot == null) {
            throw new IllegalArgumentException("lot must not be null");
        }
        upsertLot(lot);
    }

    @Override
    public void saveMeasurement(String lotId, String partId, String bubbleId, String value) {
        InspectionLot lot = loadLot(lotId);
        PartRecord part = lot.getParts().stream()
                .filter(candidate -> candidate.getId().equals(partId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Inspection part was not found."));
        part.setMeasurement(bubbleId, value);
        lot.setUpdatedAt(LocalDateTime.now());
        upsertLot(lot);
    }

    @Override
    public void deleteLotsForPlan(String planId) {
        for (InspectionLotSummary summary : loadLotSummariesForPlan(planId)) {
            deleteLot(summary.getId());
        }
    }

    @Override
    public void deleteLot(String lotId) {
        firestore.deleteCollection(partCollectionPath(lotId));
        firestore.deleteDocument(lotDocumentPath(lotId));
    }

    private void upsertLot(InspectionLot lot) {
        firestore.upsertDocument(lotDocumentPath(lot.getId()), lotFields(lot));
        syncParts(lot);
    }

    private void syncParts(InspectionLot lot) {
        Map<String, FirestoreRestClient.FirestoreDocument> existing = new LinkedHashMap<>();
        for (FirestoreRestClient.FirestoreDocument document : firestore.listDocuments(partCollectionPath(lot.getId()))) {
            existing.put(document.id(), document);
        }

        List<String> bubbleIds = lot.getBubbles().stream().map(PartBubbleDefinition::getId).toList();
        for (PartRecord part : lot.getParts()) {
            existing.remove(part.getId());
            firestore.upsertDocument(partDocumentPath(lot.getId(), part.getId()), partFields(part, bubbleIds));
        }

        for (String removedPartId : existing.keySet()) {
            firestore.deleteDocument(partDocumentPath(lot.getId(), removedPartId));
        }
    }

    private Map<String, Object> lotFields(InspectionLot lot) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("lotId", lot.getId());
        fields.put("name", lot.getName());
        fields.put("planId", lot.getPlanId());
        fields.put("planFamilyId", lot.getPlanFamilyId());
        fields.put("planName", lot.getPlanName());
        fields.put("planVersion", lot.getPlanVersion());
        fields.put("lotSize", lot.getLotSize());
        fields.put("createdAt", lot.getCreatedAt());
        fields.put("updatedAt", lot.getUpdatedAt());
        return fields;
    }

    private Map<String, Object> partFields(PartRecord part, List<String> bubbleIds) {
        Map<String, Object> measurements = new LinkedHashMap<>();
        for (String bubbleId : bubbleIds) {
            measurements.put(bubbleId, part.getMeasurement(bubbleId));
        }

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("partId", part.getId());
        fields.put("partNumber", part.getPartNumber());
        fields.put("measurements", measurements);
        return fields;
    }

    private InspectionLot restoreLot(FirestoreRestClient.FirestoreDocument lotDocument) {
        Map<String, Object> fields = lotDocument.fields();
        InspectionPlan plan = planRepository.loadPlan(stringValue(fields, "planId", ""));
        if (!plan.isComplete()) {
            throw new IllegalStateException("Inspection lots can only reference complete plans.");
        }

        List<PartBubbleDefinition> bubbleDefinitions = InspectionSpecBuilder.buildBubbleDefinitions(plan);
        PartLot lotData = new PartLot(intValue(fields, "lotSize", 1));
        lotData.replaceBubbles(bubbleDefinitions);

        List<PartRecord> parts = loadParts(lotDocument.id(), bubbleDefinitions);
        lotData.getParts().clear();
        lotData.getParts().addAll(parts);

        return new InspectionLot(
                stringValue(fields, "lotId", lotDocument.id()),
                stringValue(fields, "name", "Inspection Lot"),
                stringValue(fields, "planId", ""),
                stringValue(fields, "planFamilyId", ""),
                stringValue(fields, "planName", ""),
                intValue(fields, "planVersion", 0),
                lotData,
                dateTimeValue(fields.get("createdAt")),
                dateTimeValue(fields.get("updatedAt"))
        );
    }

    private List<PartRecord> loadParts(String lotId, List<PartBubbleDefinition> bubbles) {
        List<PartRecord> parts = new ArrayList<>();
        for (FirestoreRestClient.FirestoreDocument document : firestore.listDocuments(partCollectionPath(lotId))) {
            Map<String, Object> fields = document.fields();
            PartRecord part = new PartRecord(
                    stringValue(fields, "partId", document.id()),
                    intValue(fields, "partNumber", 1)
            );
            Map<String, Object> measurements = mapValue(fields.get("measurements"));
            for (PartBubbleDefinition bubble : bubbles) {
                part.setMeasurement(bubble.getId(), stringValue(measurements, bubble.getId(), ""));
            }
            parts.add(part);
        }
        parts.sort(Comparator.comparingInt(PartRecord::getPartNumber));
        return parts;
    }

    private FirestoreRestClient.FirestoreDocument requireLot(String lotId) {
        return firestore.getDocument(lotDocumentPath(lotId))
                .orElseThrow(() -> new IllegalStateException("Inspection lot was not found."));
    }

    private InspectionLotSummary toLotSummary(FirestoreRestClient.FirestoreDocument document) {
        Map<String, Object> fields = document.fields();
        return new InspectionLotSummary(
                stringValue(fields, "lotId", document.id()),
                stringValue(fields, "name", "Inspection Lot"),
                stringValue(fields, "planId", ""),
                stringValue(fields, "planFamilyId", ""),
                stringValue(fields, "planName", ""),
                intValue(fields, "planVersion", 0),
                intValue(fields, "lotSize", 1),
                dateTimeValue(fields.get("createdAt")),
                dateTimeValue(fields.get("updatedAt"))
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

    private String lotCollectionPath() {
        return "users/" + firestore.currentUserId() + "/inspectionLots";
    }

    private String lotDocumentPath(String lotId) {
        return lotCollectionPath() + "/" + lotId;
    }

    private String partCollectionPath(String lotId) {
        return lotDocumentPath(lotId) + "/parts";
    }

    private String partDocumentPath(String lotId, String partId) {
        return partCollectionPath(lotId) + "/" + partId;
    }

    private String stringValue(Map<String, Object> fields, String key, String fallback) {
        Object value = fields.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private int intValue(Map<String, Object> fields, String key, int fallback) {
        Object value = fields.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Integer.parseInt(text);
        }
        return fallback;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private LocalDateTime dateTimeValue(Object value) {
        return value instanceof LocalDateTime dateTime ? dateTime : null;
    }
}
