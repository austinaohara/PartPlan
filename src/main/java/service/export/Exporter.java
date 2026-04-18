package service.export;

import model.InspectionPlan;

import java.io.IOException;
import java.nio.file.Path;

public interface Exporter {
    void export(InspectionPlan data, Path output)throws IOException;
}
