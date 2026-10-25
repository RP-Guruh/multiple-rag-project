package id.guruh.devlablm.model;

import java.time.LocalDateTime;

public record ServerInfo(String appName, String status, LocalDateTime timestamp, int port) {
}
