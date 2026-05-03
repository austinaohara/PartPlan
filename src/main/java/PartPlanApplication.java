import app.AppContext;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import view.AppNavigator;

import java.io.IOException;

public class PartPlanApplication extends Application {
    private final AppContext appContext = AppContext.createDefault();

    @Override
    public void start(Stage stage) throws IOException {
        AppNavigator.initialize(appContext);
        FXMLLoader loader = AppNavigator.createLoader("/fxml/welcome.fxml");
        Scene scene = new Scene(loader.load(), 1100, 700);

        stage.setTitle("PartPlan");
        stage.setScene(scene);
        stage.show();
    }
}
