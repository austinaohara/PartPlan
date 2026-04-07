package model;

public class Part {
    private int specNumber;
    private int place;
    private String specType;
    private double specNominalSize;
    private double specTolerance;
    private String type;
    private double bonusTol;
    private double measurement; // columnControlBar will be controlled by this
    private String inspectMethod;

    public Part(int specNumber, int place, String specType, double specNominalSize, double specTolerance,
                String type, double bonusTol, double measurement, String inspectMethod) {
        this.specNumber = specNumber;
        this.place = place;
        this.specType = specType;
        this.specNominalSize = specNominalSize;
        this.specTolerance = specTolerance;
        this.type = type;
        this.bonusTol = bonusTol;
        this.measurement = measurement; // columnControlBar will be controlled by this
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

    public double getSpecNominalSize() {
        return specNominalSize;
    }

    public void setSpecNominalSize(double specNominalSize) {
        this.specNominalSize = specNominalSize;
    }

    public double getSpecTolerance() {
        return specTolerance;
    }

    public void setSpecTolerance(double specTolerance) {
        this.specTolerance = specTolerance;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public double getBonusTol() {
        return bonusTol;
    }

    public void setBonusTol(double bonusTol) {
        this.bonusTol = bonusTol;
    }

    public double getMeasurement() {
        return measurement;
    }

    public void setMeasurement(double measurement) {
        this.measurement = measurement;
    }

    public double specDeviation() {
        return (specNominalSize - measurement) / specTolerance;
    }

    public String getInspectMethod() {
        return inspectMethod;
    }

    public void setInspectMethod(String inspectMethod) {
        this.inspectMethod = inspectMethod;
    }
}
