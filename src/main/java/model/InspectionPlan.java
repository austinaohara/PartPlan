package model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class InspectionPlan {
    private final String id;
    private String name;
    private String partNumber;
    private String revision;
    private String description;
    private PlanDrawing drawing;
    private List<Bubble> bubbles;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public InspectionPlan() {
        this(UUID.randomUUID().toString(), "", "", "", "", null, new ArrayList<>(), LocalDateTime.now(), LocalDateTime.now());
    }

    public InspectionPlan(String name) {
        this(UUID.randomUUID().toString(), name, "", "", "", null, new ArrayList<>(), LocalDateTime.now(), LocalDateTime.now());
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
        this(id, name, partNumber, revision, description, drawing, new ArrayList<>(), createdAt, updatedAt);
    }

    public InspectionPlan(
            String id,
            String name,
            String partNumber,
            String revision,
            String description,
            PlanDrawing drawing,
            List<Bubble> bubbles,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.id = id;
        this.name = name;
        this.partNumber = partNumber;
        this.revision = revision;
        this.description = description;
        this.drawing = drawing;
        this.bubbles = bubbles == null ? new ArrayList<>() : new ArrayList<>(bubbles);
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

    public List<Bubble> getBubbles() {
        return bubbles;
    }

    public void setBubbles(List<Bubble> bubbles) {
        this.bubbles = bubbles == null ? new ArrayList<>() : new ArrayList<>(bubbles);
        touch();
    }

    public void addBubble(Bubble bubble) {
        if (bubble == null) {
            return;
        }

        bubbles.add(bubble);
        touch();
    }

    public void removeBubble(Bubble bubble) {
        if (bubble == null) {
            return;
        }

        bubbles.remove(bubble);
        touch();
    }

    public List<Bubble> getBubblesByStatus(BubbleStatus status) {
        return bubbles.stream()
                .filter(bubble -> bubble.getStatus() == status)
                .toList();
    }

    public List<Bubble> getBubblesInSequenceOrder() {
        return bubbles.stream()
                .sorted(Comparator.comparingInt(Bubble::getSequenceNumber))
                .toList();
    }

    public int nextBubbleSequenceNumber() {
        return bubbles.stream()
                .mapToInt(Bubble::getSequenceNumber)
                .max()
                .orElse(0) + 1;
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
