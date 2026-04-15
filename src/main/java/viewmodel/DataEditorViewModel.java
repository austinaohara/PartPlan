package viewmodel;

import javafx.collections.ObservableList;
import model.Bubble;

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
}
