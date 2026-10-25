package id.guruh.devlablm.service;

import id.guruh.devlablm.model.ServerInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AppService {

    @Value("${server.port:8080}")
    private int port;

    public ServerInfo getServerInfo() {
        return new ServerInfo("DevLab LM Desktop & Web Application", "Online", LocalDateTime.now(), port);
    }

    public String processGreeting(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "Halo dari Spring Boot Service! Selamat datang di aplikasi JavaFX Desktop modern.";
        }
        return "Halo, " + name.trim() + "! Pesan ini diproses langsung dari Spring Boot Service.";
    }
}
