package id.guruh.devlablm.ui;

import atlantafx.base.theme.PrimerDark;
import id.guruh.devlablm.event.StageReadyEvent;
import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component
public class StageInitializer implements ApplicationListener<StageReadyEvent> {

    private final FxmlViewLoader fxmlViewLoader;

    public StageInitializer(FxmlViewLoader fxmlViewLoader) {
        this.fxmlViewLoader = fxmlViewLoader;
    }
    @Override
    public void onApplicationEvent(StageReadyEvent event) {
        Stage stage = event.getStage();

        Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());
        Parent root = fxmlViewLoader.load("/fxml/main.fxml");

        Scene scene = new Scene(root, 1200, 750);
        scene.getStylesheets().add(
                getClass().getResource("/css/app.css").toExternalForm());

        stage.setTitle("DevLab LM Studio - Train Your AI, Serve Your Customers");
        stage.setMinWidth(960);
        stage.setMinHeight(600);
        stage.setScene(scene);
        stage.centerOnScreen();
        stage.show();
    }
}
