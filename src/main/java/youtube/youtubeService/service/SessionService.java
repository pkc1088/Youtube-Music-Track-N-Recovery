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
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import youtube.youtubeService.handler.CustomOAuth2User;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;
    private final StringRedisTemplate redisTemplate;


    public Set<String> getAllUserIds() {
        Set<String> userIds = new HashSet<>();

        RedisConnection connection = Objects.requireNonNull(redisTemplate.getConnectionFactory()).getConnection();

        Cursor<byte[]> cursor = connection.scan(ScanOptions.scanOptions().match("spring:session:sessions:*").count(1000).build());

        while (cursor.hasNext()) {
            String key = new String(cursor.next(), StandardCharsets.UTF_8);

            if (key.contains("expires")) continue;

            String sessionId = key.substring("spring:session:sessions:".length());
            Session session = sessionRepository.findById(sessionId);
            if (session == null) continue;
            Object scObj = session.getAttribute("SPRING_SECURITY_CONTEXT");
            if (scObj instanceof SecurityContext securityContext) {
                Authentication auth = securityContext.getAuthentication();

                if (auth != null && auth.getPrincipal() instanceof OAuth2User oauth2User) {
                    userIds.add(oauth2User.getName() + " : " + key);
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
            SecurityContext securityContext = (SecurityContext) securityContextObj;
            Authentication authentication = securityContext.getAuthentication();
            result.put("authenticated", authentication.isAuthenticated());

            Object principal = authentication.getPrincipal();
            result.put("principalClass", principal.getClass().getName());

            // [수정 포인트 2] CustomOAuth2User 우선 체크 및 상세 정보 추출
            if (principal instanceof CustomOAuth2User customUser) {
                // 1. 기본 속성
                Map<String, Object> attributes = new HashMap<>(customUser.getAttributes());
                result.put("attributes", attributes);

                // 2. [NEW] 커스텀 필드 추가 (대시보드에서 확인 가능!)
                result.put("countryCode", customUser.getCountryCode());
                result.put("channelId", customUser.getChannelId());

                // 3. 권한 정보
                result.put("roles", authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.toList()));

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

    private void cleanupKeysByPattern(String pattern, AtomicLong counter) {
        redisTemplate.execute((RedisConnection connection) -> {
            try (Cursor<byte[]> cursor = connection.scan(ScanOptions.scanOptions().match(pattern).count(1000).build())) {
                while (cursor.hasNext()) {
                    byte[] keyBytes = cursor.next();
                    String key = new String(keyBytes, StandardCharsets.UTF_8);
                    Long ttl = connection.ttl(keyBytes);
                    // TTL 이 -1 (영구 보존)인 경우만 좀비로 간주하고 삭제
                    if (ttl != null && ttl == -1 && !key.equals("quota:limit:daily")) {
                        connection.del(keyBytes);
                        counter.incrementAndGet();
                        log.info("[Zombie Cleanup] Deleted key: {}", key);
                    }
                }
            } catch (Exception e) {
                log.error("Error during zombie cleanup", e);
            }
            return null;
        });
    }

    public Map<String, Long> cleanupZombieKeys() {
        AtomicLong deletedQuotaCount = new AtomicLong(0);
        AtomicLong deletedSessionCount = new AtomicLong(0);
        // 1. Quota 좀비 청소 (quota:*)
        cleanupKeysByPattern("quota:*", deletedQuotaCount);
        // 2. Spring Session Expiration 좀비 청소 (spring:session:expirations:*) 주의: sessions:expires:... 는 건드리면 안 됨!
        cleanupKeysByPattern("spring:session:expirations:*", deletedSessionCount);

        return Map.of(
                "deletedQuotaKeys", deletedQuotaCount.get(),
                "deletedSessionExpirationKeys", deletedSessionCount.get()
        );
    }

    public List<String> getAllLegitKeys () {
        List<String> keys = new ArrayList<>();
        redisTemplate.execute((RedisConnection connection) -> {
            try (Cursor<byte[]> cursor = connection.scan(ScanOptions.scanOptions().count(100).build())) {
                while (cursor.hasNext()) {
                    byte[] keyBytes = cursor.next();
                    String key = new String(keyBytes, StandardCharsets.UTF_8);
                    keys.add(key + "\n");
                }
            } catch (Exception e) {
                log.error("Error during getAllLegitQuotas", e);
            }
            return null;
        });
        return keys;
    }
}
