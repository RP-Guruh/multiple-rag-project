package id.guruh.devlablm.ui.controller;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import id.guruh.devlablm.ui.FxmlViewLoader;
import javafx.application.Application;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import org.springframework.stereotype.Component;

@Component
public class MainLayoutController {

    private final FxmlViewLoader fxmlViewLoader;
    private boolean isDarkMode = false;

    @FXML
    private StackPane contentArea;

    @FXML
    private Button navDashboardBtn;

    @FXML
    private Button navWebStatusBtn;

    @FXML
    private Button navSettingsBtn;

    @FXML
    private Button navAiSettingsBtn;

    @FXML
    private Button themeToggleBtn;

    public MainLayoutController(FxmlViewLoader fxmlViewLoader) {
        this.fxmlViewLoader = fxmlViewLoader;
    }

    @FXML
    public void initialize() {
        // Tampilkan halaman Dashboard sebagai halaman default saat pertama kali dibuka
        onNavDashboard();
    }

    @FXML
    public void onNavDashboard() {
        switchPage("/fxml/dashboard.fxml", navDashboardBtn);
    }

    @FXML
    public void onNavWebStatus() {
        switchPage("/fxml/web-status.fxml", navWebStatusBtn);
    }

    @FXML
    public void onNavAiSetting() {
        switchPage("/fxml/ai-settings.fxml", navAiSettingsBtn);
    }

    @FXML
    public void onNavSettings() {
        switchPage("/fxml/settings.fxml", navSettingsBtn);
    }

    private void switchPage(String fxmlPath, Button activeButton) {
        Parent view = fxmlViewLoader.load(fxmlPath);
        contentArea.getChildren().setAll(view);

        // Update styling active menu
        resetNavStyles();
        activeButton.getStyleClass().add("accent");
    }

    private void resetNavStyles() {
        navDashboardBtn.getStyleClass().remove("accent");
        navWebStatusBtn.getStyleClass().remove("accent");
        navSettingsBtn.getStyleClass().remove("accent");
    }

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
}
