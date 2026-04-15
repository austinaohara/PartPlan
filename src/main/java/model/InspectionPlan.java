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
    private List<PlanPage> pages;
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
        this.pages = new ArrayList<>();
        if (drawing != null) {
            this.pages.add(new PlanPage("Page 1", 1, drawing));
        }
        this.bubbles = bubbles == null ? new ArrayList<>() : new ArrayList<>(bubbles);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public InspectionPlan(
            String id,
            String name,
            String partNumber,
            String revision,
            String description,
            PlanDrawing drawing,
            List<PlanPage> pages,
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
        this.pages = pages == null ? new ArrayList<>() : new ArrayList<>(pages);
        if (this.drawing == null && !this.pages.isEmpty()) {
            this.drawing = this.pages.getFirst().getDrawing();
        }
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
        this.pages.clear();
        if (drawing != null) {
            this.pages.add(new PlanPage("Page 1", 1, drawing));
        }
        touch();
    }

    public List<PlanPage> getPages() {
        return pages;
    }

    public void setPages(List<PlanPage> pages) {
        this.pages = pages == null ? new ArrayList<>() : new ArrayList<>(pages);
        this.drawing = this.pages.isEmpty() ? null : this.pages.getFirst().getDrawing();
        touch();
    }

    public void addPage(PlanPage page) {
        if (page == null) {
            return;
        }

        pages.add(page);
        if (drawing == null) {
            drawing = page.getDrawing();
        }
        touch();
    }

    public void removePage(PlanPage page) {
        if (page == null) {
            return;
        }

        pages.remove(page);
        drawing = pages.isEmpty() ? null : pages.getFirst().getDrawing();
        touch();
    }

    public int nextPageNumber() {
        return pages.stream()
                .mapToInt(PlanPage::getPageNumber)
                .max()
                .orElse(0) + 1;
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

        insertBubbleAtSequence(bubble, bubble.getSequenceNumber());
        bubbles.add(bubble);
        touch();
    }

    public void removeBubble(Bubble bubble) {
        if (bubble == null) {
            return;
        }

        bubbles.remove(bubble);
        resequencePageBubbles(bubble.getPageId());
        touch();
    }

    public void moveBubbleToSequence(Bubble bubble, int requestedSequence) {
        if (bubble == null) {
            return;
        }

        String pageId = bubble.getPageId();
        List<Bubble> pageBubbles = bubbles.stream()
                .filter(candidate -> pageId.equals(candidate.getPageId()) && !candidate.getId().equals(bubble.getId()))
                .sorted(Comparator.comparingInt(Bubble::getSequenceNumber))
                .toList();

        int boundedSequence = Math.max(1, Math.min(requestedSequence, pageBubbles.size() + 1));
        for (Bubble candidate : pageBubbles) {
            if (candidate.getSequenceNumber() >= boundedSequence) {
                candidate.setSequenceNumber(candidate.getSequenceNumber() + 1);
                candidate.setLabel(String.valueOf(candidate.getSequenceNumber()));
            }
        }

        bubble.setSequenceNumber(boundedSequence);
        bubble.setLabel(String.valueOf(boundedSequence));
        resequencePageBubbles(pageId);
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

    private void insertBubbleAtSequence(Bubble bubble, int requestedSequence) {
        String pageId = bubble.getPageId();
        int boundedSequence = Math.max(1, Math.min(requestedSequence, nextBubbleSequenceNumberForPage(pageId)));
        for (Bubble candidate : bubbles) {
            if (pageId.equals(candidate.getPageId()) && candidate.getSequenceNumber() >= boundedSequence) {
                candidate.setSequenceNumber(candidate.getSequenceNumber() + 1);
                candidate.setLabel(String.valueOf(candidate.getSequenceNumber()));
            }
        }

        bubble.setSequenceNumber(boundedSequence);
        bubble.setLabel(String.valueOf(boundedSequence));
    }

    private void resequencePageBubbles(String pageId) {
        List<Bubble> pageBubbles = bubbles.stream()
                .filter(bubble -> pageId.equals(bubble.getPageId()))
                .sorted(Comparator.comparingInt(Bubble::getSequenceNumber))
                .toList();

        for (int index = 0; index < pageBubbles.size(); index++) {
            int sequence = index + 1;
            Bubble bubble = pageBubbles.get(index);
            bubble.setSequenceNumber(sequence);
            bubble.setLabel(String.valueOf(sequence));
        }
    }

    private int nextBubbleSequenceNumberForPage(String pageId) {
        return bubbles.stream()
                .filter(bubble -> pageId.equals(bubble.getPageId()))
                .mapToInt(Bubble::getSequenceNumber)
                .max()
                .orElse(0) + 1;
    }

    private void touch() {
        updatedAt = LocalDateTime.now();
    }
}
