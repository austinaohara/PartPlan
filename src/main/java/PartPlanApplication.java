import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class PartPlanApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/welcome.fxml"));
        Scene scene = new Scene(loader.load(), 1100, 700);

        stage.setTitle("PartPlan");
        stage.setScene(scene);
        stage.show();
    }

}
