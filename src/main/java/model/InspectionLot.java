package model;

import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

public class InspectionLot {
    private final String id;
    private String name;
    private final String planId;
    private final String planName;
    private final PartLot lotData;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public InspectionLot(String name, String planId, String planName, int lotSize) {
        this(UUID.randomUUID().toString(), name, planId, planName, new PartLot(lotSize), LocalDateTime.now(), LocalDateTime.now());
    }

    public InspectionLot(
            String id,
            String name,
            String planId,
            String planName,
            PartLot lotData,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.id = id;
        this.name = name;
        this.planId = planId;
        this.planName = planName;
        this.lotData = lotData == null ? new PartLot(1) : lotData;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        touch();
    }

    public String getPlanId() {
        return planId;
    }

    public String getPlanName() {
        return planName;
    }

    public int getLotSize() {
        return lotData.getLotSize();
    }

    public void setLotSize(int lotSize) {
        lotData.setLotSize(lotSize);
        touch();
    }

    public LinkedList<PartRecord> getParts() {
        return lotData.getParts();
    }

    public List<PartBubbleDefinition> getBubbles() {
        return lotData.getBubbles();
    }

    public PartRecord getPart(int zeroBasedIndex) {
        return lotData.getPart(zeroBasedIndex);
    }

    public void replaceBubbles(List<PartBubbleDefinition> bubbleDefinitions) {
        lotData.replaceBubbles(bubbleDefinitions);
        touch();
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public InspectionLotSummary toSummary() {
        return new InspectionLotSummary(id, name, planId, planName, getLotSize(), createdAt, updatedAt);
    }

    private void touch() {
        updatedAt = LocalDateTime.now();
    }
}
