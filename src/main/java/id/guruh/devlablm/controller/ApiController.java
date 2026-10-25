package id.guruh.devlablm.controller;

import id.guruh.devlablm.model.ServerInfo;
import id.guruh.devlablm.service.AppService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final AppService appService;

    public ApiController(AppService appService) {
        this.appService = appService;
    }

    @GetMapping("/status")
    public ServerInfo getStatus() {
        return appService.getServerInfo();
    }

    @GetMapping("/greet")
    public Map<String, String> greet(@RequestParam(defaultValue = "Tamu") String name) {
        return Map.of(
                "message", appService.processGreeting(name),
                "source", "Spring Boot REST Controller"
        );
    }
}
