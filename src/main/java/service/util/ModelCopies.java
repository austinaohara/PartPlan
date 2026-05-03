package service.util;

import model.Bubble;
import model.InspectionLot;
import model.InspectionLotSummary;
import model.InspectionPlan;
import model.PartBubbleDefinition;
import model.PartLot;
import model.PartRecord;
import model.PlanDrawing;
import model.PlanPage;

import java.util.ArrayList;
import java.util.List;

public final class ModelCopies {
    private ModelCopies() {
    }

    public static InspectionPlan copyPlan(InspectionPlan plan) {
        if (plan == null) {
            return null;
        }

        List<PlanPage> pages = plan.getPages().stream()
                .map(ModelCopies::copyPage)
                .toList();
        List<Bubble> bubbles = plan.getBubbles().stream()
                .map(ModelCopies::copyBubble)
                .toList();

        return new InspectionPlan(
                plan.getId(),
                plan.getName(),
                plan.getPartNumber(),
                plan.getRevision(),
                plan.getDescription(),
                copyDrawing(plan.getDrawing()),
                pages,
                bubbles,
                plan.getCreatedAt(),
                plan.getUpdatedAt()
        );
    }

    public static InspectionLot copyLot(InspectionLot lot) {
        if (lot == null) {
            return null;
        }

        return new InspectionLot(
                lot.getId(),
                lot.getName(),
                lot.getPlanId(),
                lot.getPlanName(),
                copyPartLot(lot),
                lot.getCreatedAt(),
                lot.getUpdatedAt()
        );
    }

    public static InspectionLotSummary copyLotSummary(InspectionLotSummary summary) {
        if (summary == null) {
            return null;
        }

        return new InspectionLotSummary(
                summary.getId(),
                summary.getName(),
                summary.getPlanId(),
                summary.getPlanName(),
                summary.getLotSize(),
                summary.getCreatedAt(),
                summary.getUpdatedAt()
        );
    }

    public static PlanDrawing copyDrawing(PlanDrawing drawing) {
        if (drawing == null) {
            return null;
        }
        return new PlanDrawing(drawing.getFileName(), drawing.getStoredPath(), drawing.getFileType());
    }

    public static PlanPage copyPage(PlanPage page) {
        if (page == null) {
            return null;
        }
        return new PlanPage(page.getId(), page.getName(), page.getPageNumber(), copyDrawing(page.getDrawing()));
    }

    public static Bubble copyBubble(Bubble bubble) {
        if (bubble == null) {
            return null;
        }
        return new Bubble(
                bubble.getId(),
                bubble.getPageId(),
                bubble.getX(),
                bubble.getY(),
                bubble.getRadius(),
                bubble.isUseDefaultDiameter(),
                bubble.getColor(),
                bubble.isUseDefaultColor(),
                bubble.getLabel(),
                bubble.getCharacteristic(),
                bubble.getInspectionType(),
                bubble.getNominalValue(),
                bubble.getLowerTolerance(),
                bubble.getUpperTolerance(),
                bubble.getExpectedPassFail(),
                bubble.getMeasuredValue(),
                bubble.getActualPassFail(),
                bubble.getStatus(),
                bubble.getNote(),
                bubble.getSequenceNumber(),
                bubble.getCreatedAt(),
                bubble.getUpdatedAt()
        );
    }

    public static PartBubbleDefinition copyBubbleDefinition(PartBubbleDefinition definition) {
        if (definition == null) {
            return null;
        }
        return new PartBubbleDefinition(
                definition.getId(),
                definition.getName(),
                definition.getSequenceNumber(),
                definition.getNominalValue(),
                definition.getLowerTolerance(),
                definition.getUpperTolerance(),
                definition.getNote()
        );
    }

    private static PartLot copyPartLot(InspectionLot lot) {
        PartLot partLot = new PartLot(lot.getLotSize());
        List<PartBubbleDefinition> copiedBubbles = lot.getBubbles().stream()
                .map(ModelCopies::copyBubbleDefinition)
                .toList();
        partLot.replaceBubbles(copiedBubbles);

        List<PartRecord> copiedParts = new ArrayList<>();
        for (PartRecord part : lot.getParts()) {
            copiedParts.add(copyPartRecord(part, copiedBubbles));
        }
        partLot.getParts().clear();
        partLot.getParts().addAll(copiedParts);
        return partLot;
    }

    private static PartRecord copyPartRecord(PartRecord part, List<PartBubbleDefinition> bubbles) {
        PartRecord copy = new PartRecord(part.getId(), part.getPartNumber());
        for (PartBubbleDefinition bubble : bubbles) {
            copy.setMeasurement(bubble.getId(), part.getMeasurement(bubble.getId()));
        }
        return copy;
    }
}
