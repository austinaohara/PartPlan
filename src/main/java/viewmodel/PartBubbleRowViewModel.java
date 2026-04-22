package viewmodel;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class PartBubbleRowViewModel {
    private final String bubbleId;
    private final IntegerProperty sequenceNumber = new SimpleIntegerProperty();
    private final StringProperty bubbleName = new SimpleStringProperty();
    private final StringProperty nominalValue = new SimpleStringProperty();
    private final StringProperty lowerTolerance = new SimpleStringProperty();
    private final StringProperty upperTolerance = new SimpleStringProperty();
    private final StringProperty note = new SimpleStringProperty();
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
        this.nominalValue.set(nominalValue);
        this.lowerTolerance.set(lowerTolerance);
        this.upperTolerance.set(upperTolerance);
        this.note.set(note);
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

    public StringProperty nominalValueProperty() {
        return nominalValue;
    }

    public StringProperty lowerToleranceProperty() {
        return lowerTolerance;
    }

    public StringProperty upperToleranceProperty() {
        return upperTolerance;
    }

    public StringProperty noteProperty() {
        return note;
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
}
