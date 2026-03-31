import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class PartPlanApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/plan-editor.fxml"));
        Scene scene = new Scene(loader.load(), 1000, 700);

        stage.setTitle("PartPlan");
        stage.setScene(scene);
        stage.show();
    }
}
