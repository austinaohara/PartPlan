package service.export;

import model.InspectionPlan;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class InspectionExportService {
    private final Map<ExportFormat, Exporter> exporters = new HashMap<>();

    public InspectionExportService() {
        exporters.put(ExportFormat.CSV, new CsvExportService());
        exporters.put(ExportFormat.PDF, new PdfExportService());
    }

    public void export(InspectionPlan inspectionPlan, ExportFormat format, Path output) throws IOException {
        Exporter exporter = exporters.get(format);
        if (exporter == null) {
            throw new IllegalArgumentException(format + " is an unsupported export format.");
        }
        exporter.export(inspectionPlan, output);
    }
}
