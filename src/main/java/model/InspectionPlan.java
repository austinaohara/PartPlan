package model;

import java.time.LocalDateTime;
import java.util.UUID;

public class InspectionPlan {
    private final String id;
    private String name;
    private String partNumber;
    private String revision;
    private String description;
    private PlanDrawing drawing;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public InspectionPlan() {
        this(UUID.randomUUID().toString(), "", "", "", "", null, LocalDateTime.now(), LocalDateTime.now());
    }

    public InspectionPlan(String name) {
        this(UUID.randomUUID().toString(), name, "", "", "", null, LocalDateTime.now(), LocalDateTime.now());
    }

    public InspectionPlan(
            String id,
            String name,
            String partNumber,
            String revision,
            String description,
            PlanDrawing drawing,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.id = id;
        this.name = name;
        this.partNumber = partNumber;
        this.revision = revision;
        this.description = description;
        this.drawing = drawing;
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

    public String getPartNumber() {
        return partNumber;
    }

    public void setPartNumber(String partNumber) {
        this.partNumber = partNumber;
        touch();
    }

    public String getRevision() {
        return revision;
    }

    public void setRevision(String revision) {
        this.revision = revision;
        touch();
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
        touch();
    }

    public PlanDrawing getDrawing() {
        return drawing;
    }

    public void setDrawing(PlanDrawing drawing) {
        this.drawing = drawing;
        touch();
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public boolean hasDrawing() {
        return drawing != null;
    }

    public void rename(String newName) {
        this.name = newName;
        touch();
    }

    private void touch() {
        updatedAt = LocalDateTime.now();
    }
}
