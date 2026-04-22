package model;

import java.util.UUID;

public class PartBubbleDefinition {
    private final String id;
    private String name;
    private int sequenceNumber;
    private final String nominalValue;
    private final String lowerTolerance;
    private final String upperTolerance;
    private final String note;

    public PartBubbleDefinition(String name, int sequenceNumber) {
        this(UUID.randomUUID().toString(), name, sequenceNumber, "", "", "", "");
    }

    public PartBubbleDefinition(
            String id,
            String name,
            int sequenceNumber,
            String nominalValue,
            String lowerTolerance,
            String upperTolerance,
            String note
    ) {
        this.id = id;
        this.name = name;
        this.sequenceNumber = sequenceNumber;
        this.nominalValue = nominalValue == null ? "" : nominalValue;
        this.lowerTolerance = lowerTolerance == null ? "" : lowerTolerance;
        this.upperTolerance = upperTolerance == null ? "" : upperTolerance;
        this.note = note == null ? "" : note;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(int sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public String getNominalValue() {
        return nominalValue;
    }

    public String getLowerTolerance() {
        return lowerTolerance;
    }

    public String getUpperTolerance() {
        return upperTolerance;
    }

    public String getNote() {
        return note;
    }
}
