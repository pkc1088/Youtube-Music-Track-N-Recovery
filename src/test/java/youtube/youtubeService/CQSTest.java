package youtube.youtubeService;

import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.StopWatch;
import youtube.youtubeService.repository.playlists.PlaylistRepository;
import youtube.youtubeService.service.users.UserService;
import youtube.youtubeService.service.youtube.RecoverOrchestrationService;

@Slf4j
@SpringBootTest
@Transactional
public class CQSTest {

    @Autowired
    UserService userService;
    @Autowired
    RecoverOrchestrationService recoverOrchestrationService;

    @Test
    public void cqsTest() {

//        String unlistedVideo = "gQHcttM8niw";       // Regina Belle
//        String userId = "112735690496635663877";    // pkc1088
//        String refreshToken = userService.getUserByUserId(userId).orElseThrow(() -> new EntityNotFoundException("User not found: " + userId)).getRefreshToken();
//        String accessTokenForRecoverUser = userService.getNewAccessTokenByUserId(userId, refreshToken);

        // BEFORE: allPlaylistsRecoveryOfAllUsers 시작과 끝
        // AFTER: executeRecoveryPlan 걸린 시간 + OutboxStatusUpdater 시간
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        recoverOrchestrationService.allPlaylistsRecoveryOfAllUsers();
        stopWatch.stop();
        log.info("[Test] ALL Time: {} ms", stopWatch.getTotalTimeMillis());

    }

    void sleep() {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            // 이 예외가 발생했을 때 현재 스레드의 중단 상태를 다시 복구해주는 것이 좋습니다.
            Thread.currentThread().interrupt();
            // 혹은 예외를 로깅하고 적절한 중단 처리를 합니다.
            System.err.println("스레드 대기 중 인터럽트 발생!");
        }
    }
}
