package model;

import javafx.beans.property.*;

public class Part {
    private final IntegerProperty specNumber = new SimpleIntegerProperty();
    private final IntegerProperty place = new SimpleIntegerProperty();
    private final StringProperty specType = new SimpleStringProperty();
    private final FloatProperty specNominalSize = new SimpleFloatProperty();
    private final FloatProperty specTolerance = new SimpleFloatProperty();
    private final StringProperty type = new SimpleStringProperty();
    private final FloatProperty bonusTol = new SimpleFloatProperty();
    private final FloatProperty measurement = new SimpleFloatProperty(); // columnControlBar will be controlled by this
    private final StringProperty inspectMethod = new SimpleStringProperty();

    public Part(int specNumber, int place, String specType, float specNominalSize, float specTolerance, String type, float bonusTol, float measurement, String inspectMethod) {
        this.specNumber.set(specNumber);
        this.place.set(place);
        this.specType.set(specType);
        this.specNominalSize.set(specNominalSize);
        this.specTolerance.set(specTolerance);
        this.type.set(type);
        this.bonusTol.set(bonusTol);
        this.measurement.set(measurement); // columnControlBar will be controlled by this
        this.inspectMethod.set(inspectMethod);
    }

    public int getSpecNumber() {
        return specNumber.get();
    }

    public IntegerProperty specNumberProperty() {
        return specNumber;
    }

    public int getPlace() {
        return place.get();
    }

    public IntegerProperty placeProperty() {
        return place;
    }

    public String getSpecType() {
        return specType.get();
    }

    public StringProperty specTypeProperty() {
        return specType;
    }

    public float getSpecNominalSize() {
        return specNominalSize.get();
    }

    public FloatProperty specNominalSizeProperty() {
        return specNominalSize;
    }

    public float getSpecTolerance() {
        return specTolerance.get();
    }

    public FloatProperty specToleranceProperty() {
        return specTolerance;
    }

    public String getType() {
        return type.get();
    }

    public StringProperty typeProperty() {
        return type;
    }

    public float getBonusTol() {
        return bonusTol.get();
    }

    public FloatProperty bonusTolProperty() {
        return bonusTol;
    }

    public float getMeasurement() {
        return measurement.get();
    }

    public FloatProperty measurementProperty() {
        return measurement;
    }

    public String getInspectMethod() {
        return inspectMethod.get();
    }

    public StringProperty inspectMethodProperty() {
        return inspectMethod;
    }
}
