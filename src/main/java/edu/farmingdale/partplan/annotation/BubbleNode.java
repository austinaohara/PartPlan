package edu.farmingdale.partplan.annotation;

import javafx.scene.layout.StackPane;

public class BubbleNode extends StackPane {

    // TODO (Person 3): Add field: private Bubble bubbleModel

    // TODO (Person 3): Constructor — accept a Bubble model, call setupShape(), setupDrag(), setupSelection(), setupContextMenu()

    // TODO (Person 3): setupShape() — create a Circle (white fill, red stroke, ~18px radius) and a Text label showing specNumber, add both to this StackPane

    // TODO (Person 3): setupDrag() — on mouse pressed record offset, on mouse dragged update layoutX/layoutY. Use DragHandler or implement inline

    // TODO (Person 3): setupSelection() — on mouse clicked highlight border (e.g. blue stroke), deselect all others. Fire a selection event so AnnotationController can update the side panel

    // TODO (Person 3): setupContextMenu() — right-click menu with "Edit" and "Delete" options. Delete should call BubbleManager.removeBubble()

    // TODO (Person 3): updateDisplay() — re-renders the Text label if specNumber changes

    // TODO (Person 3): getBubbleModel() — returns the associated Bubble model

    // TODO (Person 2): Add CSS style class "bubble-node" in setupShape()
    // TODO (Person 2): Toggle CSS class "bubble-node-selected" on selection/deselection
}