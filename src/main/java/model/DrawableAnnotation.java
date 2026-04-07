package model;

public interface DrawableAnnotation {
    double getX();

    double getY();

    boolean containsPoint(double pointX, double pointY);
}
