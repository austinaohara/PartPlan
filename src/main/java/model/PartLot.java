package model;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class PartLot {
    private final LinkedList<PartRecord> parts = new LinkedList<>();
    private final LinkedList<PartBubbleDefinition> bubbles = new LinkedList<>();
    private int lotSize;

    public PartLot() {
        this(1);
    }

    public PartLot(int lotSize) {
        setLotSize(lotSize);
    }

    public int getLotSize() {
        return lotSize;
    }

    public LinkedList<PartRecord> getParts() {
        return parts;
    }

    public List<PartBubbleDefinition> getBubbles() {
        return bubbles;
    }

    public PartRecord getPart(int zeroBasedIndex) {
        if (zeroBasedIndex < 0 || zeroBasedIndex >= parts.size()) {
            return null;
        }
        return parts.get(zeroBasedIndex);
    }

    public void setLotSize(int proposedLotSize) {
        int normalizedLotSize = Math.max(1, proposedLotSize);
        lotSize = normalizedLotSize;

        while (parts.size() < normalizedLotSize) {
            parts.add(createPart(parts.size() + 1));
        }
        while (parts.size() > normalizedLotSize) {
            parts.removeLast();
        }

        resequenceParts();
    }

    public void replaceBubbles(List<PartBubbleDefinition> bubbleDefinitions) {
        bubbles.clear();
        if (bubbleDefinitions != null) {
            bubbles.addAll(bubbleDefinitions);
        }

        Set<String> bubbleIds = bubbles.stream()
                .map(PartBubbleDefinition::getId)
                .collect(java.util.stream.Collectors.toSet());

        for (PartRecord part : parts) {
            part.retainMeasurements(bubbleIds);
            for (PartBubbleDefinition bubble : bubbles) {
                part.ensureMeasurement(bubble.getId());
            }
        }
    }

    private PartRecord createPart(int partNumber) {
        PartRecord part = new PartRecord(partNumber);
        for (PartBubbleDefinition bubble : bubbles) {
            part.ensureMeasurement(bubble.getId());
        }
        return part;
    }

    private void resequenceParts() {
        for (int index = 0; index < parts.size(); index++) {
            parts.get(index).setPartNumber(index + 1);
        }
    }

    private void resequenceBubbles() {
        for (int index = 0; index < bubbles.size(); index++) {
            bubbles.get(index).setSequenceNumber(index + 1);
        }
    }
}
