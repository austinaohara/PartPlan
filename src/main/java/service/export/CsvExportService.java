package service.export;

import model.Bubble;
import model.InspectionPlan;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class CsvExportService implements Exporter {

    @Override
    public void export(InspectionPlan inspectionPlan, Path output) {
        try {
            List<Bubble> bubbles = inspectionPlan.getBubblesInSequenceOrder();
            StringBuilder csv = new StringBuilder();

            csv.append("Sequence,Label,Characteristic,Type,Nominal,LowerTolerance,UpperTolerance,Measured,Status\n");

            for (Bubble bubble : bubbles) {
                csv.append(bubble.getSequenceNumber()).append(",");
                csv.append(escape(bubble.getLabel())).append(",");
                csv.append(escape(bubble.getCharacteristic())).append(",");
                csv.append(bubble.getInspectionType()).append(",");
                csv.append(value(bubble.getNominalValue())).append(",");
                csv.append(value(bubble.getLowerTolerance())).append(",");
                csv.append(value(bubble.getUpperTolerance())).append(",");
                csv.append(value(bubble.getMeasuredValue())).append(",");
                csv.append(bubble.getStatus()).append("\n");

                Files.writeString(output, csv.toString());

            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to export CSV", e);
        }
    }

    private String value(Double value) {
        return value == null ? "" : value.toString();
    }

    private String escape(String string) {
        if (string == null) return "";
        return "\"" + string.replace("\"", "\"\"") + "\"";
    }


}
