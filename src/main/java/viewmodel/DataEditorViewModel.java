package viewmodel;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import model.Bubble;

public class DataEditorViewModel {
    private final ObservableList<Bubble> bubbles = FXCollections.observableArrayList();

    public ObservableList<Bubble> getBubbles(){
        return bubbles;
    }

    public void addBubble(Bubble bubble){
        bubbles.add(bubble);
    }

    public void removeBubble(Bubble bubble){
        bubbles.remove(bubble);
    }


}
