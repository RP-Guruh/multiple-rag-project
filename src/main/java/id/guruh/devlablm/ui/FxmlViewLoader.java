package id.guruh.devlablm.ui;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class FxmlViewLoader {

    public record ViewNode(Parent root, Object controller) {}

    private final ApplicationContext applicationContext;

    public FxmlViewLoader(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * Cari URL FXML. Utamakan file mentah di src/main/resources saat development
     * agar perubahan FXML dapat langsung dibaca tanpa build ulang classpath.
     */
    public URL resolveFxmlUrl(String fxmlPath) {
        String cleanPath = fxmlPath.startsWith("/") ? fxmlPath.substring(1) : fxmlPath;
        Path devFile = Paths.get("src/main/resources", cleanPath);
        if (Files.exists(devFile)) {
            try {
                return devFile.toUri().toURL();
            } catch (Exception ignored) {
            }
        }
        return getClass().getResource(fxmlPath);
    }

    /**
     * Cari URL CSS dengan cache-busting fragment (#timestamp)
     * agar JavaFX StyleManager tidak meng-cache stylesheet lama saat reload.
     */
    public String resolveCssUrl(String cssPath) {
        String cleanPath = cssPath.startsWith("/") ? cssPath.substring(1) : cssPath;
        Path devFile = Paths.get("src/main/resources", cleanPath);
        if (Files.exists(devFile)) {
            try {
                return devFile.toUri().toURL().toExternalForm() + "#" + System.currentTimeMillis();
            } catch (Exception ignored) {
            }
        }
        URL resource = getClass().getResource(cssPath);
        return resource != null ? resource.toExternalForm() + "#" + System.currentTimeMillis() : "";
    }

    public ViewNode loadView(String fxmlPath) {
        URL resource = resolveFxmlUrl(fxmlPath);
        if (resource == null) {
            throw new IllegalArgumentException("File FXML tidak ditemukan: " + fxmlPath);
        }

        FXMLLoader loader = new FXMLLoader(resource);
        loader.setClassLoader(getClass().getClassLoader());
        loader.setControllerFactory(applicationContext::getBean);

        try {
            Parent root = loader.load();
            return new ViewNode(root, loader.getController());
        } catch (IOException e) {
            throw new RuntimeException("Gagal memuat FXML: " + fxmlPath, e);
        }
    }

    public Parent load(String fxmlPath) {
        return loadView(fxmlPath).root();
    }
}
