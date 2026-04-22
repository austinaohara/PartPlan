package model;

import java.time.LocalDateTime;

public class InspectionLotSummary {
    private final String id;
    private final String name;
    private final String planId;
    private final String planName;
    private final int lotSize;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public InspectionLotSummary(
            String id,
            String name,
            String planId,
            String planName,
            int lotSize,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.id = id;
        this.name = name;
        this.planId = planId;
        this.planName = planName;
        this.lotSize = lotSize;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getPlanId() {
        return planId;
    }

    public String getPlanName() {
        return planName;
    }

    public int getLotSize() {
        return lotSize;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public String toString() {
        return name;
    }
}
