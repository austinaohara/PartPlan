package service.memory;

import model.Bubble;
import model.InspectionLot;
import model.InspectionLotSummary;
import model.InspectionPlan;
import model.PartBubbleDefinition;
import model.PartRecord;
import model.PlanPage;
import service.repository.LotRepository;
import service.session.SessionManager;
import service.util.ModelCopies;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InMemoryLotRepository implements LotRepository {
    private final SessionManager sessionManager;
    private final Map<String, Map<String, InspectionLot>> lotsByUser = new HashMap<>();

    public InMemoryLotRepository(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    public synchronized List<InspectionLotSummary> loadLotSummaries() {
        return lotsForCurrentUser().values().stream()
                .map(InspectionLot::toSummary)
                .map(ModelCopies::copyLotSummary)
                .sorted(Comparator.comparing(InspectionLotSummary::getUpdatedAt).reversed())
                .toList();
    }

    @Override
    public synchronized InspectionLot createLot(String proposedLotName, InspectionPlan plan, int lotSize) {
        if (plan == null) {
            throw new IllegalArgumentException("plan must not be null");
        }

        InspectionLot lot = new InspectionLot(
                sanitizeLotName(proposedLotName, plan),
                plan.getId(),
                displayPlanName(plan),
                lotSize
        );
        lot.replaceBubbles(buildBubbleDefinitions(plan));
        lotsForCurrentUser().put(lot.getId(), ModelCopies.copyLot(lot));
        return ModelCopies.copyLot(lot);
    }

    @Override
    public synchronized InspectionLot loadLot(String lotId) {
        InspectionLot lot = lotsForCurrentUser().get(lotId);
        if (lot == null) {
            throw new IllegalStateException("Inspection lot was not found.");
        }
        return ModelCopies.copyLot(lot);
    }

    @Override
    public synchronized void saveLotName(String lotId, String lotName) {
        InspectionLot lot = requireLot(lotId);
        lot.setName(lotName);
        lot.setUpdatedAt(LocalDateTime.now());
    }

    @Override
    public synchronized void saveLotStructure(InspectionLot lot) {
        if (lot == null) {
            throw new IllegalArgumentException("lot must not be null");
        }

        lot.setUpdatedAt(LocalDateTime.now());
        lotsForCurrentUser().put(lot.getId(), ModelCopies.copyLot(lot));
    }

    @Override
    public synchronized void saveMeasurement(String lotId, String partId, String bubbleId, String value) {
        InspectionLot lot = requireLot(lotId);
        PartRecord part = lot.getParts().stream()
                .filter(candidate -> candidate.getId().equals(partId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Inspection part was not found."));
        part.setMeasurement(bubbleId, value);
        lot.setUpdatedAt(LocalDateTime.now());
    }

    @Override
    public synchronized void deleteLot(String lotId) {
        lotsForCurrentUser().remove(lotId);
    }

    private InspectionLot requireLot(String lotId) {
        InspectionLot lot = lotsForCurrentUser().get(lotId);
        if (lot == null) {
            throw new IllegalStateException("Inspection lot was not found.");
        }
        return lot;
    }

    private Map<String, InspectionLot> lotsForCurrentUser() {
        String uid = sessionManager.requireCurrentSession().getUid();
        return lotsByUser.computeIfAbsent(uid, ignored -> new HashMap<>());
    }

    private List<PartBubbleDefinition> buildBubbleDefinitions(InspectionPlan plan) {
        Map<String, Integer> pageOrder = new HashMap<>();
        for (PlanPage page : plan.getPages()) {
            pageOrder.put(page.getId(), page.getPageNumber());
        }

        List<Bubble> sortedBubbles = plan.getBubbles().stream()
                .sorted(Comparator
                        .comparingInt((Bubble bubble) -> pageOrder.getOrDefault(bubble.getPageId(), Integer.MAX_VALUE))
                        .thenComparingInt(Bubble::getSequenceNumber)
                        .thenComparing(Bubble::getId))
                .toList();

        boolean includePagePrefix = pageOrder.size() > 1;
        return java.util.stream.IntStream.range(0, sortedBubbles.size())
                .mapToObj(index -> {
                    Bubble bubble = sortedBubbles.get(index);
                    String name = buildBubbleName(bubble, pageOrder.getOrDefault(bubble.getPageId(), 0), includePagePrefix);
                    return new PartBubbleDefinition(
                            bubble.getId(),
                            name,
                            index + 1,
                            formatNullableNumber(bubble.getNominalValue()),
                            formatNullableNumber(bubble.getLowerTolerance()),
                            formatNullableNumber(bubble.getUpperTolerance()),
                            bubble.getNote() == null ? "" : bubble.getNote().trim()
                    );
                })
                .toList();
    }

    private String buildBubbleName(Bubble bubble, int pageNumber, boolean includePagePrefix) {
        String label = (bubble.getLabel() == null || bubble.getLabel().isBlank())
                ? "Bubble " + bubble.getSequenceNumber()
                : bubble.getLabel().trim();
        String characteristic = bubble.getCharacteristic() == null ? "" : bubble.getCharacteristic().trim();
        String bubbleText = characteristic.isBlank() ? label : label + " - " + characteristic;

        if (includePagePrefix && pageNumber > 0) {
            return "Page " + pageNumber + " | " + bubbleText;
        }

        return bubbleText;
    }

    private String sanitizeLotName(String proposedLotName, InspectionPlan plan) {
        if (proposedLotName == null || proposedLotName.isBlank()) {
            return displayPlanName(plan) + " Lot " + LocalDateTime.now().withNano(0);
        }
        return proposedLotName.trim();
    }

    private String displayPlanName(InspectionPlan plan) {
        if (plan == null || plan.getName() == null || plan.getName().isBlank()) {
            return "Untitled Plan";
        }
        return plan.getName().trim();
    }

    private String formatNullableNumber(Double value) {
        if (value == null) {
            return "";
        }
        if (value == Math.rint(value)) {
            return String.valueOf(value.intValue());
        }
        return value.toString();
    }
}
