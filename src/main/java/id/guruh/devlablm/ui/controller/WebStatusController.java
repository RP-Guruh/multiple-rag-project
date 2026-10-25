package id.guruh.devlablm.ui.controller;

import id.guruh.devlablm.model.ServerInfo;
import id.guruh.devlablm.service.AppService;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

@Component
public class WebStatusController {

    private final AppService appService;

    @FXML
    private Label appNameLabel;

    @FXML
    private Label statusLabel;

    @FXML
    private Label portLabel;

    @FXML
    private Label timeLabel;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    public WebStatusController(AppService appService) {
        this.appService = appService;
    }

    @FXML
    public void initialize() {
        onRefreshStatus();
    }

    @FXML
    public void onRefreshStatus() {
        ServerInfo info = appService.getServerInfo();
        appNameLabel.setText("Aplikasi: " + info.appName());
        statusLabel.setText("Status Server: " + info.status());
        portLabel.setText("Port Aktif: " + info.port());
        timeLabel.setText("Timestamp Terakhir: " + info.timestamp().format(FORMATTER));
    }
}
