package id.guruh.devlablm.ui.controller;

import atlantafx.base.controls.PasswordTextField;
import atlantafx.base.theme.Styles;
import id.guruh.devlablm.sqlite.service.DatabaseSettingService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Properties;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class DatabaseSettingController {

    private final DatabaseSettingService service;

    @FXML private TextField hostField;
    @FXML private TextField portField;
    @FXML private TextField databaseField;
    @FXML private TextField usernameField;
    @FXML private PasswordTextField passwordField;
    @FXML private TextField schemaField;
    @FXML private CheckBox sslCheck;
    @FXML private Label statusLabel;

    public DatabaseSettingController(DatabaseSettingService service) {
        this.service = service;
    }

    @FXML
    private void initialize() {
        service.load().ifPresent(s -> {
            hostField.setText(s.getHost());
            portField.setText(s.getPort() == null ? "" : String.valueOf(s.getPort()));
            databaseField.setText(s.getDatabase());
            usernameField.setText(s.getUsername());
            schemaField.setText(s.getSchema());
            sslCheck.setSelected(Boolean.TRUE.equals(s.getSslEnabled()));
            try {
                passwordField.setText(service.decryptPassword(s));
            } catch (IllegalStateException e) {
                showStatus("Password tersimpan tidak bisa dibaca, silakan isi ulang.", false);
            }
        });

        hostField.textProperty().addListener((o, a, b) -> statusLabel.setText(""));
        portField.textProperty().addListener((o, a, b) -> statusLabel.setText(""));
        databaseField.textProperty().addListener((o, a, b) -> statusLabel.setText(""));
        usernameField.textProperty().addListener((o, a, b) -> statusLabel.setText(""));
        passwordField.passwordProperty().addListener((o, a, b) -> statusLabel.setText(""));
        schemaField.textProperty().addListener((o, a, b) -> statusLabel.setText(""));
    }

    @FXML
    private void handleSave() {
        String error = validate();
        if (error != null) {
            showStatus(error, false);
            return;
        }

        try {
            service.save(
                    hostField.getText().trim(),
                    Integer.parseInt(portField.getText().trim()),
                    databaseField.getText().trim(),
                    usernameField.getText().trim(),
                    passwordField.getPassword(),
                    schemaField.getText().trim(),
                    sslCheck.isSelected()
            );
            showStatus("Pengaturan database berhasil disimpan", true);
        } catch (Exception e) {
            statusLabel.setText("");
            new Alert(Alert.AlertType.ERROR,
                    "Gagal menyimpan pengaturan. Coba lagi.").showAndWait();
            e.printStackTrace();
        }
    }

    @FXML
    private void handleTestConnection() {
        String error = validate();
        if (error != null) {
            showStatus(error, false);
            return;
        }

        String host = hostField.getText().trim();
        int port = Integer.parseInt(portField.getText().trim());
        String db = databaseField.getText().trim();
        String user = usernameField.getText().trim();
        String pass = passwordField.getPassword();
        boolean ssl = sslCheck.isSelected();

        showStatus("Menguji koneksi...", true);

        Thread t = new Thread(() -> {
            String url = "jdbc:postgresql://" + host + ":" + port + "/" + db;
            Properties props = new Properties();
            props.setProperty("user", user);
            props.setProperty("password", pass);
            props.setProperty("connectTimeout", "5"); // detik
            if (ssl) props.setProperty("sslmode", "require");

            try (Connection c = DriverManager.getConnection(url, props)) {
                boolean ok = c.isValid(3);
                Platform.runLater(() -> showStatus(
                        ok ? "Koneksi berhasil" : "Koneksi gagal", ok));
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> showStatus(
                        "Koneksi gagal: periksa host, port, database, username, dan password.", false));
            }
        }, "pg-test-connection");
        t.setDaemon(true);
        t.start();
    }

    private String validate() {
        String host = hostField.getText().trim();
        String portText = portField.getText().trim();
        String db = databaseField.getText().trim();
        String user = usernameField.getText().trim();
        String pass = passwordField.getPassword();

        if (host.isEmpty()) return "Host wajib diisi";
        if (portText.isEmpty()) return "Port wajib diisi";
        try {
            int port = Integer.parseInt(portText);
            if (port < 1 || port > 65535) return "Port harus antara 1 dan 65535";
        } catch (NumberFormatException e) {
            return "Port harus berupa angka";
        }
        if (db.isEmpty()) return "Nama database wajib diisi";
        if (user.isEmpty()) return "Username wajib diisi";
        if (pass == null || pass.isBlank()) return "Password wajib diisi";
        return null; // null = valid
    }

    private void showStatus(String message, boolean success) {
        statusLabel.setText(message);
        statusLabel.getStyleClass().removeAll(Styles.SUCCESS, Styles.DANGER);
        statusLabel.getStyleClass().add(success ? Styles.SUCCESS : Styles.DANGER);
    }
}