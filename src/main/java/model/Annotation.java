package model;

import java.time.LocalDateTime;
import java.util.UUID;

public abstract class Annotation implements DrawableAnnotation {
    private final String id;
    private double x;
    private double y;
    private String color;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    protected Annotation() {
        this(UUID.randomUUID().toString(), 0.0, 0.0, "#E53935", LocalDateTime.now(), LocalDateTime.now());
    }

    protected Annotation(String id, double x, double y, String color, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.color = color;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    @Override
    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
        touch();
    }

    @Override
    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
        touch();
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
        touch();
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    protected void touch() {
        updatedAt = LocalDateTime.now();
    }
}
