package youtube.youtubeService.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private final StringRedisTemplate redisTemplate;
    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    public List<String> listSessions() {
        return new ArrayList<>(redisTemplate.keys("spring:session:sessions:*"));
    }

    public Set<String> getAllUserIds() {
        Set<String> userIds = new HashSet<>();

        RedisConnection connection = Objects.requireNonNull(redisTemplate.getConnectionFactory()).getConnection();
        // 한번에 1000개씩 가져오기 힌트
        Cursor<byte[]> cursor = connection.scan(ScanOptions.scanOptions().match("spring:session:sessions:*").count(1000).build());

        while (cursor.hasNext()) {
            String key = new String(cursor.next(), StandardCharsets.UTF_8);

            if (key.contains("expires")) continue; // expires 키는 건너뜀

            String sessionId = key.substring("spring:session:sessions:".length());
            Session session = sessionRepository.findById(sessionId);
            if (session == null) continue;
            Object scObj = session.getAttribute("SPRING_SECURITY_CONTEXT");
            if (scObj instanceof SecurityContext securityContext) {
                Authentication auth = securityContext.getAuthentication();
                if (auth instanceof OAuth2AuthenticationToken oauthToken) {
                    Object principal = oauthToken.getPrincipal();
                    if (principal instanceof OidcUser oidcUser) {
                        userIds.add(oidcUser.getName() + " : " + key); // 바로 userId
                    }
                }
            }
        }
        cursor.close();
        return userIds;
    }

    public Map<String, Object> getSession(String sessionId) {
        Session session = sessionRepository.findById(sessionId);
        if (session == null) {
            return Map.of("error", "세션 없음");
        }

        Object securityContextObj = session.getAttribute("SPRING_SECURITY_CONTEXT");
        if (securityContextObj == null) {
            return Map.of("info", "SecurityContext 없음");
        }

        Map<String, Object> result = new HashMap<>();
        try {
            // SecurityContext 객체 확인
            SecurityContext securityContext = (SecurityContext) securityContextObj;
            Authentication authentication = securityContext.getAuthentication();
            result.put("authenticated", authentication.isAuthenticated());
            result.put("principalClass", authentication.getPrincipal().getClass().getName());
            // OAuth2User 정보 추출
            if (authentication instanceof OAuth2AuthenticationToken oauthToken) {
                Object principal = oauthToken.getPrincipal();
                if (principal instanceof OidcUser oidcUser) {
                    Map<String, Object> attributes = new HashMap<>(oidcUser.getAttributes());
                    result.put("attributes", attributes);
                    result.put("roles", authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toList()));
                }
            }
        } catch (Exception e) {
            result.put("error", "세션 정보 파싱 실패");
            result.put("exception", e.getMessage());
        }
        return result;
    }

    public Map<String, Object> deleteSession(String sessionId) {
        sessionRepository.deleteById(sessionId);
        return Map.of("status", "세션 강제 종료됨", "sessionId", sessionId);
    }

}
