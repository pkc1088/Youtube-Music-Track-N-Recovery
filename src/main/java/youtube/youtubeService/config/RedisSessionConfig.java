package youtube.youtubeService.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.session.data.redis.config.ConfigureRedisAction;

@Configuration
public class RedisSessionConfig {

    @Bean
    @ConditionalOnMissingBean
    public static ConfigureRedisAction configureRedisAction() {
        // 빈 자체를 none으로 고정해서 enableRedisKeyspaceNotificationsInitializer가 동작 안 하도록
        return ConfigureRedisAction.NO_OP;
    }
}