package viewmodel;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import model.Part;

public class DataEditorViewModel {
    private ObservableList<Part> parts = FXCollections.observableArrayList();

    public ObservableList<Part> getParts(){
        return parts;
    }

    public void addPart(Part part){
        parts.add(part);
    }

    public void removePart(Part part){
        parts.remove(part);
    }


}
