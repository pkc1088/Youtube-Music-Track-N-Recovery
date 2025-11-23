package youtube.youtubeService.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "userExecutor")
    public ThreadPoolTaskExecutor userExecutor() {
        log.info(":::[userExecutor init]");
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);  // 동시에 실행 가능한 Outbox 작업 수
        executor.setMaxPoolSize(10);   // 절대 최대치
        executor.setQueueCapacity(500); // 나머지는 대기 Integer.MAX_VALUE
        executor.setThreadNamePrefix("userExecutor-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy()); // 스레드 풀이 꽉 차면, 작업을 등록한 스레드가 직접 이 일을 처리
        executor.initialize();
        return executor;
    }
}
