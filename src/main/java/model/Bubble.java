package model;

import java.time.LocalDateTime;
import java.util.UUID;

public class Bubble extends Annotation {
    private double radius;
    private String label;
    private String characteristic;
    private InspectionType inspectionType;
    private Double nominalValue;
    private Double lowerTolerance;
    private Double upperTolerance;
    private Boolean expectedPassFail;
    private Double measuredValue;
    private Boolean actualPassFail;
    private BubbleStatus status;
    private String note;
    private int sequenceNumber;

    public Bubble() {
        this(UUID.randomUUID().toString(), 0.0, 0.0, 18.0, "#E53935", "", "", InspectionType.NUMERIC,
                null, null, null, true, null, null, BubbleStatus.OPEN, "", 0,
                LocalDateTime.now(), LocalDateTime.now());
    }

    public Bubble(double x, double y, int sequenceNumber) {
        this(UUID.randomUUID().toString(), x, y, 18.0, "#E53935", String.valueOf(sequenceNumber), "",
                InspectionType.NUMERIC, null, null, null, true, null, null, BubbleStatus.OPEN, "", sequenceNumber,
                LocalDateTime.now(), LocalDateTime.now());
    }

    public Bubble(
            String id,
            double x,
            double y,
            double radius,
            String color,
            String label,
            String characteristic,
            InspectionType inspectionType,
            Double nominalValue,
            Double lowerTolerance,
            Double upperTolerance,
            Boolean expectedPassFail,
            Double measuredValue,
            Boolean actualPassFail,
            BubbleStatus status,
            String note,
            int sequenceNumber,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        super(id, x, y, color, createdAt, updatedAt);
        this.radius = radius;
        this.label = label;
        this.characteristic = characteristic;
        this.inspectionType = inspectionType;
        this.nominalValue = nominalValue;
        this.lowerTolerance = lowerTolerance;
        this.upperTolerance = upperTolerance;
        this.expectedPassFail = expectedPassFail;
        this.measuredValue = measuredValue;
        this.actualPassFail = actualPassFail;
        this.status = status;
        this.note = note;
        this.sequenceNumber = sequenceNumber;
    }

    @Override
    public boolean containsPoint(double pointX, double pointY) {
        double dx = pointX - getX();
        double dy = pointY - getY();
        return Math.sqrt(dx * dx + dy * dy) <= radius;
    }

    public void updateStatusFromResult() {
        if (inspectionType == InspectionType.NUMERIC) {
            updateNumericStatus();
            return;
        }

        updatePassFailStatus();
    }

    public double getRadius() {
        return radius;
    }

    public void setRadius(double radius) {
        this.radius = radius;
        touch();
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
        touch();
    }

    public String getCharacteristic() {
        return characteristic;
    }

    public void setCharacteristic(String characteristic) {
        this.characteristic = characteristic;
        touch();
    }

    public InspectionType getInspectionType() {
        return inspectionType;
    }

    public void setInspectionType(InspectionType inspectionType) {
        this.inspectionType = inspectionType;
        touch();
    }

    public Double getNominalValue() {
        return nominalValue;
    }

    public void setNominalValue(Double nominalValue) {
        this.nominalValue = nominalValue;
        touch();
    }

    public Double getLowerTolerance() {
        return lowerTolerance;
    }

    public void setLowerTolerance(Double lowerTolerance) {
        this.lowerTolerance = lowerTolerance;
        touch();
    }

    public Double getUpperTolerance() {
        return upperTolerance;
    }

    public void setUpperTolerance(Double upperTolerance) {
        this.upperTolerance = upperTolerance;
        touch();
    }

    public Boolean getExpectedPassFail() {
        return expectedPassFail;
    }

    public void setExpectedPassFail(Boolean expectedPassFail) {
        this.expectedPassFail = expectedPassFail;
        touch();
    }

    public Double getMeasuredValue() {
        return measuredValue;
    }

    public void setMeasuredValue(Double measuredValue) {
        this.measuredValue = measuredValue;
        updateStatusFromResult();
        touch();
    }

    public Boolean getActualPassFail() {
        return actualPassFail;
    }

    public void setActualPassFail(Boolean actualPassFail) {
        this.actualPassFail = actualPassFail;
        updateStatusFromResult();
        touch();
    }

    public BubbleStatus getStatus() {
        return status;
    }

    public void setStatus(BubbleStatus status) {
        this.status = status;
        touch();
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
        touch();
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(int sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
        touch();
    }

    private void updateNumericStatus() {
        if (measuredValue == null || nominalValue == null) {
            status = BubbleStatus.OPEN;
            return;
        }

        double minimum = nominalValue - valueOrZero(lowerTolerance);
        double maximum = nominalValue + valueOrZero(upperTolerance);
        status = measuredValue >= minimum && measuredValue <= maximum ? BubbleStatus.PASS : BubbleStatus.FAIL;
    }

    private void updatePassFailStatus() {
        if (actualPassFail == null) {
            status = BubbleStatus.OPEN;
            return;
        }

        boolean expected = expectedPassFail == null || expectedPassFail;
        status = actualPassFail == expected ? BubbleStatus.PASS : BubbleStatus.FAIL;
    }

    private double valueOrZero(Double value) {
        return value == null ? 0.0 : value;
    }
}
