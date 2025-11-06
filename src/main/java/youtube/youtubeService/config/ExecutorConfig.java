package youtube.youtubeService.config;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 작업을 파티셔닝하여 순서를 보장하는 Executor 컴포넌트
 */
@Slf4j
@Configuration
public class ExecutorConfig {

    // 고정된 스레드 풀 - 이 숫자는 동시에 처리할 수 있는 최대 플레이리스트 파티션 수
    private static final int PARTITION_SIZE = 10;

    /**
     * Outbox 처리를 위한 파티션된 Executor Bean
     */
    @Bean
    public PartitionedExecutor partitionedOutboxExecutor() {
        return new PartitionedExecutor(PARTITION_SIZE);
    }

    /**
     * 파티셔닝 로직을 캡슐화한 클래스
     */
    public static class PartitionedExecutor {

        private final ExecutorService[] partitions;
        private final int poolSize;

        public PartitionedExecutor(int poolSize) {
            this.poolSize = poolSize;
            this.partitions = new ExecutorService[poolSize];
            log.info(":::[PartitionedExecutor Initialized]");
            for (int i = 0; i < poolSize; i++) {
                // 각 파티션은 1개의 스레드만 가짐 (순서 보장을 위해)
                int finalI = i;
                partitions[i] = Executors.newSingleThreadExecutor(
                        runnable -> new Thread(runnable, "PartitionExecutor-" + finalI)
                );
            }
        }

        /**
         * 파티션 키(playlistId)를 기반으로 적절한 스레드에 작업을 제출합니다.
         * @param partitionKey 순서를 보장해야 하는 리소스 ID (e.g., playlistId)
         * @param task         실행할 작업
         */
        public CompletableFuture<Void> submit(String partitionKey, Runnable task) {
            // 키를 해시하여 어떤 파티션(스레드)에 할당할지 결정
            int index = Math.abs(partitionKey.hashCode()) % poolSize;
            // 해당 파티션(단일 스레드 Executor)에 작업을 제출
            return CompletableFuture.runAsync(task, partitions[index]);
        }

        /**
         * Spring이 이 빈을 파괴하기 직전에 호출
         */
        @PreDestroy
        public void shutdown() {
            log.info("Shutting down PartitionedExecutor...");
            for (int i = 0; i < poolSize; i++) {
                // 1. 더 이상 새로운 작업을 받지 않음
                partitions[i].shutdown();
            }
            try {
                // (선택적) 모든 스레드가 종료될 때까지 잠시 대기
                for (int i = 0; i < poolSize; i++) {
                    if (!partitions[i].awaitTermination(5, TimeUnit.SECONDS)) {
                        log.warn("PartitionExecutor-{} did not terminate gracefully.", i);
                        partitions[i].shutdownNow(); // 강제 종료
                    }
                }
            } catch (InterruptedException e) {
                log.error("PartitionedExecutor shutdown interrupted.", e);
                Thread.currentThread().interrupt();
            }
            log.info("PartitionedExecutor shutdown complete.");
        }
    }
}