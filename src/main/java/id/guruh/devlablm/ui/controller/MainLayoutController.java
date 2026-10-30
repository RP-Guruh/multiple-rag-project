package id.guruh.devlablm.ui.controller;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import id.guruh.devlablm.ui.FxmlViewLoader;
import id.guruh.devlablm.ui.HotReloadService;
import javafx.application.Application;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class MainLayoutController {

    private static final Logger log = LoggerFactory.getLogger(MainLayoutController.class);

    private final FxmlViewLoader fxmlViewLoader;
    private final HotReloadService hotReloadService;

    @FXML
    private StackPane contentArea;

    @FXML
    private Button navDashboardBtn;

    @FXML
    private Button navAiSettingsBtn;

    @FXML
    private Button navSettingsBtn;

    @FXML
    private Button navDatabaseSettingsBtn;

    @FXML
    private Button themeToggleBtn;

    private boolean isDarkMode = false;

    private String currentFxmlPath = "/fxml/dashboard.fxml";
    private Button currentActiveButton;

    public MainLayoutController(FxmlViewLoader fxmlViewLoader, HotReloadService hotReloadService) {
        this.fxmlViewLoader = fxmlViewLoader;
        this.hotReloadService = hotReloadService;
    }

    @FXML
    public void initialize() {
        hotReloadService.registerMainLayout(this);
        onNavDashboard();
    }

    @FXML
    public void onNavDashboard() {
        switchPage("/fxml/dashboard.fxml", navDashboardBtn);
    }

    @FXML
    public void onNavAiSetting() {
        switchPage("/fxml/ai-settings.fxml", navAiSettingsBtn);
    }

    @FXML
    public void onNavSettings() {
        switchPage("/fxml/settings.fxml", navSettingsBtn);
    }

    @FXML
    public void onNavDatabaseSetting(){ switchPage("/fxml/database-setting.fxml", navDatabaseSettingsBtn); }

    @FXML
    public void onToggleTheme() {
        isDarkMode = !isDarkMode;
        if (isDarkMode) {
            Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());
            themeToggleBtn.setText("Tema Terang");
        } else {
            Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
            themeToggleBtn.setText("Tema Gelap");
        }
    }

    public String getCurrentFxmlPath() {
        return currentFxmlPath;
    }

    public void navigateTo(String fxmlPath) {
        if ("/fxml/ai-settings.fxml".equals(fxmlPath)) {
            onNavAiSetting();
        } else if ("/fxml/settings.fxml".equals(fxmlPath)) {
            onNavSettings();
        } else {
            onNavDashboard();
        }
    }

    public void reloadCurrentPage() {
        if (currentFxmlPath != null) {
            try {
                Parent view = fxmlViewLoader.load(currentFxmlPath);
                contentArea.getChildren().setAll(view);
                resetNavStyles();
                if (currentActiveButton != null) {
                    currentActiveButton.getStyleClass().add("accent");
                }
                log.info("[HotReload] View reloaded: {}", currentFxmlPath);
            } catch (Exception e) {
                log.error("[HotReload] Error reloading {}: {}", currentFxmlPath, e.getMessage());
            }
        }
    }

    private void switchPage(String fxmlPath, Button activeButton) {
        this.currentFxmlPath = fxmlPath;
        this.currentActiveButton = activeButton;

        Parent view = fxmlViewLoader.load(fxmlPath);
        contentArea.getChildren().setAll(view);

        // Update styling active menu
        resetNavStyles();
        if (activeButton != null) {
            activeButton.getStyleClass().add("accent");
        }
    }

    private void resetNavStyles() {
        List<Button> buttons = List.of(navDashboardBtn, navAiSettingsBtn, navSettingsBtn, navDatabaseSettingsBtn);
        for (Button btn : buttons) {
            btn.getStyleClass().remove("accent");
        }
    }
}
