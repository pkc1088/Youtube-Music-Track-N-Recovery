package youtube.youtubeService.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {
    @Bean(name = "outboxExecutor")
    public ThreadPoolTaskExecutor outboxExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);  // 동시에 실행 가능한 Outbox 작업 수
        executor.setMaxPoolSize(10);   // 절대 최대치
        executor.setQueueCapacity(100); // 대기열
        executor.setThreadNamePrefix("OutboxExecutor-");
        // 스레드 풀이 꽉 차면, 작업을 등록한 스레드가 직접 이 일을 처리합니다.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
