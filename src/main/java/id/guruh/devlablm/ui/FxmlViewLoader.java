package id.guruh.devlablm.ui;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URL;

@Component
public class FxmlViewLoader {

    private final ApplicationContext applicationContext;

    public FxmlViewLoader(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * Memuat file FXML dengan mendelegasikan pembuatan Controller ke Spring ApplicationContext.
     * Ini memungkinkan controller FXML menggunakan dependency injection (@Autowired / constructor injection).
     */
    public Parent load(String fxmlPath) {
        URL resource = getClass().getResource(fxmlPath);
        if (resource == null) {
            throw new IllegalArgumentException("File FXML tidak ditemukan di classpath: " + fxmlPath);
        }

        FXMLLoader loader = new FXMLLoader(resource);
        // Kunci integrasi Spring Boot + FXML:
        loader.setControllerFactory(applicationContext::getBean);

        try {
            return loader.load();
        } catch (IOException e) {
            throw new RuntimeException("Gagal memuat FXML: " + fxmlPath, e);
        }
    }
}
