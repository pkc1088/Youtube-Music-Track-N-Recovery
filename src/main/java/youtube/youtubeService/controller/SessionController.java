package youtube.youtubeService.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import youtube.youtubeService.service.SessionService;
import java.util.Map;
import java.util.Set;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class SessionController {

    private final SessionService sessionService;


    @GetMapping("/dashboard")
    public String dashboard() {
        return "admin-dashboard";
    }

    @ResponseBody
    @GetMapping("/sessions/all")
    public Set<String> getAllUserIds() {
        return sessionService.getAllUserIds();
    }

    @ResponseBody
    @GetMapping("/sessions/{sessionId}")
    public Map<String, Object> getSession(@PathVariable String sessionId) {
        return sessionService.getSession(sessionId);
    }

    @ResponseBody
    @GetMapping("/sessions/delete/{sessionId}")
    public Map<String, Object> deleteSession(@PathVariable String sessionId) {
        return sessionService.deleteSession(sessionId);
    }

    @ResponseBody
    @GetMapping("/cleanup")
    public Map<String, Long> cleanupZombies() {
        log.info("[Starting Zombie Key Cleanup...]");
        return sessionService.cleanupZombieKeys();
    }

    @ResponseBody
    @GetMapping("/getAllLegitKeys")
    public String getAllLegitKeys() {
        return sessionService.getAllLegitKeys().toString();
    }
}
