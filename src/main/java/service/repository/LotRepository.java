package service.repository;

import model.InspectionLot;
import model.InspectionLotSummary;
import model.InspectionPlan;

import java.util.List;

public interface LotRepository {
    List<InspectionLotSummary> loadLotSummaries();

    InspectionLot createLot(String proposedLotName, InspectionPlan plan, int lotSize);

    InspectionLot loadLot(String lotId);

    void saveLotName(String lotId, String lotName);

    void saveLotStructure(InspectionLot lot);

    void saveMeasurement(String lotId, String partId, String bubbleId, String value);

    void deleteLot(String lotId);
}
