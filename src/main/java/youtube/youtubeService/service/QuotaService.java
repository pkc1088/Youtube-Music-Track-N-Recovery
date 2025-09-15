package youtube.youtubeService.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuotaService {

    private final StringRedisTemplate redisTemplate;
    private static final Set<String> ADMIN_USER_IDS = Set.of("112735690496635663877", "107155055893692546350");
    private static final long ADMIN_LIMIT = 100_000L;
    public static long DEFAULT_LIMIT = 3000L;

    @PostConstruct
    public void initGlobalLimit() {
        String key = "quota:limit:daily";
        Boolean exists = redisTemplate.hasKey(key);
        if (Boolean.FALSE.equals(exists)) {
            redisTemplate.opsForValue().set(key, String.valueOf(DEFAULT_LIMIT));
        }
    }

    // 조회용 키 생성
    public String getQuotaKey(String userId) {
        // String today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String todayPt = LocalDate.now(ZoneId.of("America/Los_Angeles")).format(DateTimeFormatter.BASIC_ISO_DATE);
        return "quota:" + userId + ":" + todayPt; // 오늘 날짜별 quota 키 생성
    }

    // 특정 유저 Quota 특정 값 셋팅
    public void setUserQuota(String userId, long newUsage) {
        redisTemplate.opsForValue().set(getQuotaKey(userId), String.valueOf(newUsage));
    }

    // 특정 유저 Quota 증가 (disadvantage)
    public void incrementUserQuota(String userId, long delta) {
        redisTemplate.opsForValue().increment(getQuotaKey(userId), delta);
    }

    // 특정 유저 Quota 감소 (advantage)
    public void decrementUserQuota(String userId, long delta) {
        redisTemplate.opsForValue().decrement(getQuotaKey(userId), delta);
    }

    // 전역 할당량 변경
    public void setGlobalLimit(long limit) {
        redisTemplate.opsForValue().set("quota:limit:daily", String.valueOf(limit));
    }

    // 전역 할당량 조회
    public long getDailyLimit(String userId) {
        if (ADMIN_USER_IDS.contains(userId)) return ADMIN_LIMIT; // 관리자면 무조건 ADMIN_LIMIT
        return Long.parseLong(redisTemplate.opsForValue().get("quota:limit:daily"));
    }

    // 모든 키 사용량 조회
    public Map<String, Object> getAllQuotas () {
        List<Map<String, Object>> usages = new ArrayList<>();

        // RedisConnection을 통해 Scan + Get 안전하게 수행
        redisTemplate.execute((RedisConnection connection) -> {
            try (Cursor<byte[]> cursor = connection.scan(ScanOptions.scanOptions().match("quota:*:*").count(100).build())) {
                while (cursor.hasNext()) {
                    byte[] keyBytes = cursor.next();
                    byte[] valBytes = connection.get(keyBytes); // 동일 connection에서 get
                    String key = new String(keyBytes, StandardCharsets.UTF_8);
                    long usage = (valBytes != null) ? Long.parseLong(new String(valBytes, StandardCharsets.UTF_8)) : 0L;

                    String[] parts = key.split(":");
                    if (parts.length >= 2) {
                        String userId = parts[1];
                        if ("limit".equals(userId)) continue;
                        usages.add(Map.of("key", key, "userId", userId, "usage", usage));
                    }
                }
            }
            return null;
        });

        Map<String, Object> result = new HashMap<>();
        // 전역 limit 조회
        String globalLimitVal = redisTemplate.opsForValue().get("quota:limit:daily");
        long globalLimit = (globalLimitVal != null) ? Long.parseLong(globalLimitVal) : QuotaService.DEFAULT_LIMIT;

        result.put("usages", usages);
        result.put("globalLimit", globalLimit);
        return result;
    }

    /**
     * 롤백 시 실제로 사용하지 않은 할당량은 되돌려줘야함
     */
    public void rollbackQuota(String userId, long cost) {
        redisTemplate.opsForValue().decrement(getQuotaKey(userId), cost);
    }

    /**
     * 소비 시도 (원자적 증가)
     */
    public boolean checkAndConsumeLua(String userId, long cost) {
        String key = getQuotaKey(userId);
        String script =
                "local current = redis.call('GET', KEYS[1]) " +
                "if not current then current = 0 else current = tonumber(current) end " +
                "if (current + tonumber(ARGV[1])) > tonumber(ARGV[2]) then " +
                "   return 0 " + // quota 초과 → 실패
                "else " +
                "   local newVal = redis.call('INCRBY', KEYS[1], ARGV[1]) " +
                "   if newVal == tonumber(ARGV[1]) then " +
                "       redis.call('EXPIRE', KEYS[1], ARGV[3]) " + // 새 키면 TTL 설정
                "   end " +
                "   return 1 " + // 성공
                "end";

        // long secondsUntilMidnight = Duration.between(LocalDateTime.now(), LocalDate.now().plusDays(1).atStartOfDay()).getSeconds();
        ZoneId pacific = ZoneId.of("America/Los_Angeles");
        long secondsUntilUtcMidnight = Duration.between(
                ZonedDateTime.now(pacific),
                ZonedDateTime.now(pacific).toLocalDate().plusDays(1).atStartOfDay(pacific)
        ).getSeconds();

        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>(script, Long.class);
        Long result = redisTemplate.execute(
                redisScript,
                Collections.singletonList(key),
                String.valueOf(cost),
                String.valueOf(getDailyLimit(userId)), // DAILY_LIMIT
                String.valueOf(secondsUntilUtcMidnight) // secondsUntilMidnight
        );

        return result != null && result == 1L;
    }






    public List<String> getAllLegitQuotas () {
        List<String> keys = new ArrayList<>();

        redisTemplate.execute((RedisConnection connection) -> {
            try (Cursor<byte[]> cursor = connection.scan(ScanOptions.scanOptions().count(100).build())) {
                while (cursor.hasNext()) {
                    byte[] keyBytes = cursor.next();
                    String key = new String(keyBytes, StandardCharsets.UTF_8);
                    keys.add(key + "\n");
                }
            }
            return null;
        });

        return keys;
    }
}

/*
public void rollbackQuota(String userId, long cost) {
        redisTemplate.opsForValue().decrement(getQuotaKey(userId), cost);
    }

public boolean checkAndConsumeLua(String userId, long cost) {
    String key = getQuotaKey(userId);
    String script =
            "local current = redis.call('GET', KEYS[1]) " +
                    "if not current then current = 0 else current = tonumber(current) end " +
                    "if (current + tonumber(ARGV[1])) > tonumber(ARGV[2]) then " +
                    "   return 0 " + // quota 초과 → 실패
                    "else " +
                    "   local newVal = redis.call('INCRBY', KEYS[1], ARGV[1]) " +
                    "   if newVal == tonumber(ARGV[1]) then " +
                    "       redis.call('EXPIRE', KEYS[1], ARGV[3]) " + // 새 키면 TTL 설정
                    "   end " +
                    "   return 1 " + // 성공
                    "end";

    long secondsUntilMidnight = Duration.between(LocalDateTime.now(), LocalDate.now().plusDays(1).atStartOfDay()).getSeconds();
    // targetTime = LocalTime.of(9, 0);
    DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>(script, Long.class);
    Long result = redisTemplate.execute(
            redisScript,
            Collections.singletonList(key),
            String.valueOf(cost),
            String.valueOf(DAILY_LIMIT),
            String.valueOf(secondsUntilMidnight)
    );

    return result != null && result == 1L;
}


 */