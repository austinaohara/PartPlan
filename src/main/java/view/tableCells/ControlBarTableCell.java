package view.tableCells;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.TableCell;
import javafx.scene.paint.Color;
import model.Bubble;

public class ControlBarTableCell extends TableCell<Bubble, Double> {

    private static final double BAR_HEIGHT = 20;

    private static final double RED_RATIO    = 0.15;
    private static final double YELLOW_RATIO = 0.15;
    private static final double GREEN_RATIO  = 0.40;

    private double lowerTolerance;
    private double upperTolerance;
    private double deviationValue;

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
        double clampedValue = Math.max(-lowerTolerance, Math.min(deviationValue, upperTolerance));
        double range = clampedValue >= 0 ? upperTolerance : lowerTolerance;
        double lineX = (w / 2.0) + (clampedValue / range) * (w / 2.0); // normalizes range to -1 to 1

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
            Bubble currentBubble = getTableRow().getItem();
            this.lowerTolerance = currentBubble.getLowerTolerance();
            this.upperTolerance = currentBubble.getUpperTolerance();

            double nominalValue = currentBubble.getNominalValue();
            double measuredValue = value;
            this.deviationValue = measuredValue - nominalValue;
        }
    }
}