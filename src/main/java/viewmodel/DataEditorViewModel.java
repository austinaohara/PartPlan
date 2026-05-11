package viewmodel;

import javafx.collections.ObservableList;
import model.Bubble;
import model.InspectionType;

public class DataEditorViewModel {
    private final PlanEditorViewModel planEditorViewModel;

    public DataEditorViewModel(PlanEditorViewModel planEditorViewModel) {
        this.planEditorViewModel = planEditorViewModel;
    }

    public PlanEditorViewModel getPlanEditorViewModel(){
        return this.planEditorViewModel;
    }

    public ObservableList<Bubble> getBubbles(){
        return this.planEditorViewModel.getPageBubbles();
    }

    public void updateBubblePrintFields(
            Bubble bubble,
            int sequenceNumber,
            String characteristic,
            InspectionType inspectionType,
            String nominalValueText,
            String lowerToleranceText,
            String upperToleranceText,
            String note
    ) {
        planEditorViewModel.updateBubblePrintFields(
                bubble,
                sequenceNumber,
                characteristic,
                inspectionType,
                nominalValueText,
                lowerToleranceText,
                upperToleranceText,
                note
        );
    }

    public void selectBubble(Bubble bubble) {
        planEditorViewModel.selectBubble(bubble);
    }
}
