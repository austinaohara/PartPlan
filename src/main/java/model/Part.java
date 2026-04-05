package model;

import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.*;

public class Part {
    private final IntegerProperty specNumber = new SimpleIntegerProperty();
    private final IntegerProperty place = new SimpleIntegerProperty();
    private final StringProperty specType = new SimpleStringProperty();
    private final DoubleProperty specNominalSize = new SimpleDoubleProperty();
    private final DoubleProperty specTolerance = new SimpleDoubleProperty();
    private final StringProperty type = new SimpleStringProperty();
    private final DoubleProperty bonusTol = new SimpleDoubleProperty();
    private final DoubleProperty measurement = new SimpleDoubleProperty(); // columnControlBar will be controlled by this
    private final StringProperty inspectMethod = new SimpleStringProperty();

    public Part(int specNumber, int place, String specType, double specNominalSize, double specTolerance, String type, double bonusTol, double measurement, String inspectMethod) {
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

    public double getSpecNominalSize() {
        return specNominalSize.get();
    }

    public DoubleProperty specNominalSizeProperty() {
        return specNominalSize;
    }

    public double getSpecTolerance() {
        return specTolerance.get();
    }

    public DoubleProperty specToleranceProperty() {
        return specTolerance;
    }

    public String getType() {
        return type.get();
    }

    public StringProperty typeProperty() {
        return type;
    }

    public double getBonusTol() {
        return bonusTol.get();
    }

    public DoubleProperty bonusTolProperty() {
        return bonusTol;
    }

    public double getMeasurement() {
        return measurement.get();
    }

    public DoubleProperty measurementProperty() {
        return measurement;
    }

    public DoubleBinding specDeviation() {
        return specNominalSize
                .subtract(measurementProperty())
                .divide(specTolerance);
    }

    public String getInspectMethod() {
        return inspectMethod.get();
    }

    public StringProperty inspectMethodProperty() {
        return inspectMethod;
    }
}
