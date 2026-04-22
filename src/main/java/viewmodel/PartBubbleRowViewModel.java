package viewmodel;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class PartBubbleRowViewModel {
    private final String bubbleId;
    private final IntegerProperty sequenceNumber = new SimpleIntegerProperty();
    private final StringProperty bubbleName = new SimpleStringProperty();
    private final StringProperty note = new SimpleStringProperty();
    private final StringProperty requirement = new SimpleStringProperty();
    private final StringProperty measurementValue = new SimpleStringProperty();

    public PartBubbleRowViewModel(
            String bubbleId,
            int sequenceNumber,
            String bubbleName,
            String nominalValue,
            String lowerTolerance,
            String upperTolerance,
            String note,
            String measurementValue
    ) {
        this.bubbleId = bubbleId;
        this.sequenceNumber.set(sequenceNumber);
        this.bubbleName.set(bubbleName);
        this.note.set(note);
        this.requirement.set(buildRequirement(bubbleName, nominalValue, lowerTolerance, upperTolerance, note));
        this.measurementValue.set(measurementValue);
    }

    public String getBubbleId() {
        return bubbleId;
    }

    public int getSequenceNumber() {
        return sequenceNumber.get();
    }

    public IntegerProperty sequenceNumberProperty() {
        return sequenceNumber;
    }

    public String getBubbleName() {
        return bubbleName.get();
    }

    public StringProperty bubbleNameProperty() {
        return bubbleName;
    }

    public void setBubbleName(String value) {
        bubbleName.set(value);
    }

    public String getNote() {
        return note.get();
    }

    public StringProperty requirementProperty() {
        return requirement;
    }

    public String getMeasurementValue() {
        return measurementValue.get();
    }

    public StringProperty measurementValueProperty() {
        return measurementValue;
    }

    public void setMeasurementValue(String value) {
        measurementValue.set(value);
    }

    private String buildRequirement(
            String bubbleName,
            String nominalValue,
            String lowerTolerance,
            String upperTolerance,
            String note
    ) {
        String descriptiveBubbleName = extractDescriptiveBubbleName(normalized(bubbleName));
        String normalizedNominalValue = normalized(nominalValue);
        String normalizedLowerTolerance = normalized(lowerTolerance);
        String normalizedUpperTolerance = normalized(upperTolerance);
        String normalizedNote = normalized(note);

        String numericSpecification = buildNumericSpecification(
                normalizedNominalValue,
                normalizedUpperTolerance,
                normalizedLowerTolerance
        );
        if (!numericSpecification.isBlank()) {
            return descriptiveBubbleName.isBlank()
                    ? numericSpecification
                    : descriptiveBubbleName + " " + numericSpecification;
        }

        if (!normalizedNote.isBlank()) {
            return normalizedNote;
        }

        return descriptiveBubbleName;
    }

    private String buildNumericSpecification(String nominalValue, String upperTolerance, String lowerTolerance) {
        StringBuilder builder = new StringBuilder();
        appendSegment(builder, nominalValue);
        appendSegment(builder, upperTolerance.isBlank() ? "" : "+" + upperTolerance);
        appendSegment(builder, lowerTolerance.isBlank() ? "" : "-" + lowerTolerance);
        return builder.toString();
    }

    private void appendSegment(StringBuilder builder, String value) {
        if (value == null || value.isBlank()) {
            return;
        }

        if (!builder.isEmpty()) {
            builder.append(' ');
        }
        builder.append(value);
    }

    private String normalized(String value) {
        return value == null ? "" : value.trim();
    }

    private String extractDescriptiveBubbleName(String bubbleName) {
        if (bubbleName.isBlank()) {
            return "";
        }

        int pageSeparatorIndex = bubbleName.indexOf('|');
        String pagePrefix = "";
        String body = bubbleName;
        if (pageSeparatorIndex >= 0) {
            pagePrefix = bubbleName.substring(0, pageSeparatorIndex + 1).trim();
            body = bubbleName.substring(pageSeparatorIndex + 1).trim();
        }

        int characteristicSeparatorIndex = body.indexOf(" - ");
        String descriptiveBody = characteristicSeparatorIndex >= 0
                ? body.substring(characteristicSeparatorIndex + 3).trim()
                : "";

        if (pagePrefix.isBlank()) {
            return descriptiveBody;
        }

        return descriptiveBody.isBlank()
                ? pagePrefix
                : pagePrefix + " " + descriptiveBody;
    }
}
