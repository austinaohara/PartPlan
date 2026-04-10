package viewmodel;

import model.InspectionPlan;

public class DataEditorViewModel {
    private final PlanEditorViewModel planEditorViewModel;

    public DataEditorViewModel(PlanEditorViewModel planEditorViewModel) {
        this.planEditorViewModel = planEditorViewModel;
    }

    public InspectionPlan getPlan(){
        return planEditorViewModel.getCurrentPlan();
    }
}
