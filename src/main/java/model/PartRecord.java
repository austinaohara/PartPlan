package model;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class PartRecord {
    private final String id;
    private int partNumber;
    private final Map<String, String> measurements = new LinkedHashMap<>();
    private final Map<String, String> comments = new LinkedHashMap<>();

    public PartRecord(int partNumber) {
        this(UUID.randomUUID().toString(), partNumber);
    }

    public PartRecord(String id, int partNumber) {
        this.id = id;
        this.partNumber = partNumber;
    }

    public String getId() {
        return id;
    }

    public int getPartNumber() {
        return partNumber;
    }

    public void setPartNumber(int partNumber) {
        this.partNumber = partNumber;
    }

    public String getMeasurement(String bubbleId) {
        return measurements.getOrDefault(bubbleId, "");
    }

    public void setMeasurement(String bubbleId, String value) {
        if (bubbleId == null || bubbleId.isBlank()) {
            return;
        }
        measurements.put(bubbleId, value == null ? "" : value.trim());
    }

    public String getComment(String bubbleId) {
        return comments.getOrDefault(bubbleId, "");
    }

    public void setComment(String bubbleId, String value) {
        if (bubbleId == null || bubbleId.isBlank()) {
            return;
        }
        comments.put(bubbleId, value == null ? "" : value.trim());
    }

    public void ensureMeasurement(String bubbleId) {
        if (bubbleId == null || bubbleId.isBlank()) {
            return;
        }
        measurements.putIfAbsent(bubbleId, "");
    }

    public void ensureComment(String bubbleId) {
        if (bubbleId == null || bubbleId.isBlank()) {
            return;
        }
        comments.putIfAbsent(bubbleId, "");
    }

    public void removeMeasurement(String bubbleId) {
        measurements.remove(bubbleId);
    }

    public void removeComment(String bubbleId) {
        comments.remove(bubbleId);
    }

    public void retainMeasurements(Collection<String> bubbleIds) {
        measurements.keySet().retainAll(bubbleIds);
    }

    public void retainComments(Collection<String> bubbleIds) {
        comments.keySet().retainAll(bubbleIds);
    }

    @Override
    public String toString() {
        return "Part " + partNumber;
    }
}
