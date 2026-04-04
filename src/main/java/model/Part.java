package model;

public class Part {
    private int specNumber;
    private int place;
    private String specType;
    private float specNominalSize;
    private float specTolerance;
    private String type;
    private float bonusTol;
    private float measurement; // columnControlBar will be controlled by this
    private String inspectMethod;

    public Part(int specNumber, int place, String specType, float specNominalSize, float specTolerance, String type, float bonusTol, float measurement, String inspectMethod) {
        this.specNumber = specNumber;
        this.place = place;
        this.specType = specType;
        this.specNominalSize = specNominalSize;
        this.specTolerance = specTolerance;
        this.type = type;
        this.bonusTol = bonusTol;
        this.measurement = measurement;
        this.inspectMethod = inspectMethod;
    }

    public int getSpecNumber() {
        return specNumber;
    }

    public void setSpecNumber(int specNumber) {
        this.specNumber = specNumber;
    }

    public int getPlace() {
        return place;
    }

    public void setPlace(int place) {
        this.place = place;
    }

    public String getSpecType() {
        return specType;
    }

    public void setSpecType(String specType) {
        this.specType = specType;
    }

    public float getSpecNominalSize() {
        return specNominalSize;
    }

    public void setSpecNominalSize(float specNominalSize) {
        this.specNominalSize = specNominalSize;
    }

    public float getSpecTolerance() {
        return specTolerance;
    }

    public void setSpecTolerance(float specTolerance) {
        this.specTolerance = specTolerance;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public float getBonusTol() {
        return bonusTol;
    }

    public void setBonusTol(float bonusTol) {
        this.bonusTol = bonusTol;
    }

    public float getMeasurement() {
        return measurement;
    }

    public void setMeasurement(float measurement) {
        this.measurement = measurement;
    }

    public String getInspectMethod() {
        return inspectMethod;
    }

    public void setInspectMethod(String inspectMethod) {
        this.inspectMethod = inspectMethod;
    }
}
