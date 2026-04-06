package view.tableCells;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.TableCell;
import javafx.scene.paint.Color;
import model.Part;

public class ControlBarTableCell extends TableCell<Part, Double> {

    private static final double BAR_HEIGHT = 20;

    private static final double RED_RATIO    = 0.15;
    private static final double YELLOW_RATIO = 0.15;
    private static final double GREEN_RATIO  = 0.40;

    private double specDeviation;
    private double value;

    private final Canvas canvas = new Canvas();

    public ControlBarTableCell() {
        tableColumnProperty().addListener((obs, oldCol, newCol) -> {
            if (newCol != null) {
                newCol.widthProperty().addListener((o, oldW, newW) -> draw());
            }
        });
    }

    private void draw() {
        if (getTableColumn() == null || getItem() == null) return;

        double w = getTableColumn().getWidth();
        double h = BAR_HEIGHT;

        canvas.setWidth(w);
        canvas.setHeight(h);

        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, w, h);

        // --- section widths ---
        double redW    = w * RED_RATIO;
        double yellowW = w * YELLOW_RATIO;
        double greenW  = w * GREEN_RATIO;

        // --- draw sections left to right: red, yellow, green, yellow, red ---
        gc.setFill(Color.RED);
        gc.fillRect(0, 0, redW, h);

        gc.setFill(Color.YELLOW);
        gc.fillRect(redW, 0, yellowW, h);

        gc.setFill(Color.GREEN);
        gc.fillRect(redW + yellowW, 0, greenW, h);

        gc.setFill(Color.YELLOW);
        gc.fillRect(redW + yellowW + greenW, 0, yellowW, h);

        gc.setFill(Color.RED);
        gc.fillRect(redW + yellowW + greenW + yellowW, 0, redW, h);

        // --- draw the indicator line ---
        double clampedValue = Math.max(-specDeviation, Math.min(specDeviation, value));
        double lineX = (w / 2.0) + (clampedValue / specDeviation) * (w / 2.0);

        gc.setStroke(Color.BLACK);
        gc.setLineWidth(2);
        gc.strokeLine(lineX, 0, lineX, h);
    }

    @Override
    protected void updateItem(Double value, boolean empty) {
        super.updateItem(value, empty);
        if (empty || value == null) {
            setGraphic(null);
        } else {
            specDeviation = getTableRow().getItem().getSpecNominalSize();
            this.value = value;
            System.out.printf("specDev: %f\nvalue: %f", specDeviation, value);
            draw();
            setGraphic(canvas);
        }
    }
}