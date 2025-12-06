package youtube.youtubeService.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.CacheKeyPrefix;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

//@Configuration
//@EnableCaching
//public class RedisCacheConfig {
//
//    @Bean
//    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
//        // 기본 설정: Key 는 String, Value 는 JSON 으로 직렬화
//        RedisCacheConfiguration configuration = RedisCacheConfiguration.defaultCacheConfig()
//                .disableCachingNullValues() // null 값은 캐싱 안 함
//                .entryTtl(Duration.ofMinutes(30)) // 기본 TTL 30분
//                .computePrefixWith(CacheKeyPrefix.simple())
//                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
//                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()));
//
//        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
//
//        cacheConfigurations.put("playlistSelection", configuration.entryTtl(Duration.ofMinutes(10)));
//
//        // cacheConfigurations.put("users", defaultConfig.entryTtl(Duration.ofHours(24)));
//        // cacheConfigurations.put("playlist_ids", defaultConfig.entryTtl(Duration.ofMinutes(30)));
//
//        return RedisCacheManager.RedisCacheManagerBuilder
//                .fromConnectionFactory(connectionFactory)
//                .cacheDefaults(configuration)
//                .withInitialCacheConfigurations(cacheConfigurations)
//                .build();
//    }
//}



