package model;

import java.util.UUID;

public class PlanPage {
    private final String id;
    private String name;
    private int pageNumber;
    private PlanDrawing drawing;

    public PlanPage() {
        this(UUID.randomUUID().toString(), "", 0, null);
    }

    public PlanPage(String name, int pageNumber, PlanDrawing drawing) {
        this(UUID.randomUUID().toString(), name, pageNumber, drawing);
    }

    public PlanPage(String id, String name, int pageNumber, PlanDrawing drawing) {
        this.id = id;
        this.name = name;
        this.pageNumber = pageNumber;
        this.drawing = drawing;
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

    public int getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(int pageNumber) {
        this.pageNumber = pageNumber;
    }

    public PlanDrawing getDrawing() {
        return drawing;
    }

    public void setDrawing(PlanDrawing drawing) {
        this.drawing = drawing;
    }

    public boolean hasDrawing() {
        return drawing != null;
    }
}
