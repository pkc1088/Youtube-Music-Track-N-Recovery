package youtube.youtubeService.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import youtube.youtubeService.service.youtube.RecoveryOrchestratorService;

@Slf4j
@RestController
@RequestMapping("/api/scheduler")
public class ScheduledTaskController {

    @Value("${youtube.api.key}")
    private String apiKey;

    private final RecoveryOrchestratorService recoveryOrchestratorService;

    @Autowired
    public ScheduledTaskController(RecoveryOrchestratorService recoveryOrchestratorService) {
        this.recoveryOrchestratorService = recoveryOrchestratorService;
    }

    @PostMapping("/track-recovery")
    public ResponseEntity<String> runDailyTask(@RequestHeader("api-key") String apiKey) {
        log.info("track-recovery endpoint triggered");
        if (apiKey == null || !apiKey.equals(this.apiKey)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Invalid API Key");
        }
        recoveryOrchestratorService.allPlaylistsRecoveryOfAllUsers();
        log.info("track-recovery endpoint done");
        return ResponseEntity.ok("Auto Track&Recovery Task executed");
    }

}