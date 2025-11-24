package youtube.youtubeService.apiTest;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import youtube.youtubeService.domain.Music;
import youtube.youtubeService.dto.internal.MusicDetailsDto;
import youtube.youtubeService.policy.SearchPolicy;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
@SpringBootTest
//@ExtendWith(MockitoExtension.class)
public class GeminiModelTest {

    @Autowired
    SearchPolicy geminiSearchQuery;

    private final MusicDetailsDto musicToSearch = new MusicDetailsDto(
            7777L, "videoId123", "Kiss And Say GoodBye", "Manhattans",
            "This is an American Group", "R&B SOUL", "PLNj4bt23RjfsajCmUzYQbvZp0v-M8PU8t"
    );

    @BeforeEach
    void setUp() {
//        musicToSearch.videoId("videoId123");
//        musicToSearch.setVideoTitle("Kiss And Say GoodBye");
//        musicToSearch.setVideoUploader("Manhattans");
//        musicToSearch.setVideoDescription("This is an American Group");
//        musicToSearch.setVideoTags("R&B SOUL");

    }

    @Test
    void geminiModelTest() throws Exception {

//        for (int i = 1; i <= 50; i++) {
//            String resultQuery = searchPolicy.search(musicToSearch);
//            log.info("[{}] ------------> [{}]", i, resultQuery);
//        }

        int numTasks = 50;
        int numThreads = 10;
        // 1. 50개의 작업을 동시에 처리할 스레드 풀 생성
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        // 2. 50개의 비동기 작업을 저장할 리스트 생성
        List<CompletableFuture<Void>> allFutures = new ArrayList<>();

        for (int i = 1; i <= numTasks; i++) {
            final int index = i; // 람다에서 사용하기 위해 final 변수로 복사

            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    // searchPolicy (RateLimiter 포함)가 여기서 호출됨
                    String resultQuery = geminiSearchQuery.search(musicToSearch);
                    log.info("[{}] ------------> [{}]", index, resultQuery);
                } catch (Exception e) {
                    // 개별 스레드에서 발생하는 예외 로깅
                    log.error("[{}] Task failed: {}", index, e.getMessage());
                }
            }, executor);

            allFutures.add(future);
        }

        // 4. (중요) 50개의 모든 작업이 완료될 때까지 메인 스레드 대기
        CompletableFuture.allOf(allFutures.toArray(new CompletableFuture[0])).join();

        // 5. 스레드 풀 종료
        executor.shutdown();
        if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
            log.warn("Executor did not terminate in 10 seconds.");
            executor.shutdownNow();
        }

        log.info("--- All 50 concurrent tasks completed. ---");
    }
}
