package id.guruh.devlablm.ui.controller;

import atlantafx.base.controls.PasswordTextField;
import atlantafx.base.theme.Styles;
import id.guruh.devlablm.sqlite.service.AiSettingService;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class AiSettingController {

    private final AiSettingService aiSettingService;

    @FXML
    private TextField aiURL;
    @FXML
    private PasswordTextField aiToken;
    @FXML
    private TextField modelName;
    @FXML
    private Label statusLabel;

    public AiSettingController(AiSettingService aiSettingService) {
        this.aiSettingService = aiSettingService;
    }

    @FXML
    private void initialize() {
        // Isi form dari data yang sudah tersimpan di SQLite
        aiSettingService.load().ifPresent(s -> {
            aiURL.setText(s.getAiUrl());
            aiToken.setText(s.getAiKey());
            modelName.setText(s.getModelName());
        });

        // Bersihkan pesan status saat pengguna mengubah input
        aiURL.textProperty().addListener((o, a, b) -> statusLabel.setText(""));
        aiToken.passwordProperty().addListener((o, a, b) -> statusLabel.setText(""));
        modelName.textProperty().addListener((o, a, b) -> statusLabel.setText(""));
    }

    @FXML
    private void handleSave() {
        String url = aiURL.getText().trim();
        String token = aiToken.getPassword();
        String model = modelName.getText().trim();

        String error = validate(url, token, model);
        if (error != null) {
            showStatus(error, false);
            return;
        }

        try {
            aiSettingService.save(url, token, model);
            showStatus("Pengaturan berhasil disimpan", true);
        } catch (Exception e) {
            statusLabel.setText("");
            new Alert(Alert.AlertType.ERROR,
                    "Gagal menyimpan pengaturan. Coba lagi.").showAndWait();
            e.printStackTrace(); // atau pakai logger
        }
    }

    private void showStatus(String message, boolean success) {
        statusLabel.setText(message);
        statusLabel.getStyleClass().removeAll(Styles.SUCCESS, Styles.DANGER);
        statusLabel.getStyleClass().add(success ? Styles.SUCCESS : Styles.DANGER);
    }

    private String validate(String url, String token, String model) {
        if (url.isEmpty()) return "AI URL wajib diisi";
        if (!url.startsWith("http://") && !url.startsWith("https://"))
            return "AI URL harus diawali http:// atau https://";
        if (token == null || token.isBlank()) return "AI Key wajib diisi";
        if (model.isEmpty()) return "Model Name wajib diisi";
        return null; // null = valid
    }
}