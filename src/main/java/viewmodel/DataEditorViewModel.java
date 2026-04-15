package viewmodel;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import model.Bubble;

public class DataEditorViewModel {
    private final PlanEditorViewModel planEditorViewModel;
    private final ObservableList<Bubble> bubbles = FXCollections.observableArrayList();

    public DataEditorViewModel(PlanEditorViewModel planEditorViewModel) {
        this.planEditorViewModel = planEditorViewModel;

/*
        TODO: figure out how to make bubbles in
         the PE viewmodel an ObservableList here.
         below is a hack, it doesn't change with pe's VM.
         Maybe add an ObservableList of Bubbles to
         the pe VM?
*/
        bubbles.setAll(FXCollections.observableArrayList(planEditorViewModel.getCurrentPlan().getBubbles()));
    }

    public PlanEditorViewModel getPlanEditorViewModel(){
        return this.planEditorViewModel;
    }

    public ObservableList<Bubble> getBubbles(){
        return this.bubbles;
    }
}
