package youtube.youtubeService.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import youtube.youtubeService.service.SessionService;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

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
    @GetMapping("/sessions")
    public List<String> listSessions() {
        return sessionService.listSessions();
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

}
