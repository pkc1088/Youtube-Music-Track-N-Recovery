package youtube.youtubeService.etc;

import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.StopWatch;
import youtube.youtubeService.domain.ActionLog;
import youtube.youtubeService.domain.Music;
import youtube.youtubeService.domain.Playlists;
import youtube.youtubeService.repository.playlists.PlaylistRepository;
import youtube.youtubeService.service.ActionLogService;
import youtube.youtubeService.service.musics.MusicService;
import youtube.youtubeService.service.outbox.OutboxProcessor;
import youtube.youtubeService.service.users.UserService;
import java.util.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@Slf4j
@SpringBootTest
public class OutboxPerformanceTest {

    @MockitoBean
    private OutboxProcessor outboxProcessor;
    @MockitoBean
    private ActionLogService actionLogService;


    @Autowired//MockitoSpyBean
    private UserService userService;
    @Autowired
    private PlaylistRepository playlistRepository;
    @Autowired
    private RecoverTestHelper recoverTestHelper;
    @Autowired
    private MusicService musicService;

    private String brokenVideoId, title, uploader, description, tags, playlistId;

    @BeforeEach
    void setUp() {

        try {
            brokenVideoId = "XzEoBAltBII";
            title = "The Manhattans - Kiss And Say GoodBye";
            uploader = "Whistle_Missile";
            description = "just a test video";
            tags = "The Manhattams,R&B,Soul,7th album";
            playlistId = "PLNj4bt23RjfsajCmUzYQbvZp0v-M8PU8t";

            when(outboxProcessor.processOutbox(any())).thenReturn(true);
            when(actionLogService.findTodayRecoverLog(any(ActionLog.ActionType.class), anyString())).thenReturn(Optional.empty());
        } catch (Exception e) {

        }

    }

    @Test
    void testEachRecover() {
        String userId = "112735690496635663877";
        String refreshToken = userService.getUserByUserId(userId).orElseThrow(() -> new EntityNotFoundException("User not found: " + userId)).getRefreshToken();
        String accessTokenForRecoverUser = userService.getNewAccessTokenByUserId(userId, refreshToken);  // pkc1088
        // String accessTokenForUploader = userService.getNewAccessTokenByUserId("107155055893692546350");     // WhistleMissile
        List<Long> data = new ArrayList<>();

        for (int i = 1; i <= 100; i++) {

            log.info("====================[{}] START. ====================", i);
            try {
                // Before
                sleep();
                Playlists playlist = playlistRepository.findByPlaylistId(playlistId);
                // Test
                StopWatch stopWatch = new StopWatch();
                stopWatch.start();
                //youtubeService.fileTrackAndRecover("112735690496635663877", playlist, "KR", accessTokenForRecoverUser);
                stopWatch.stop();
                log.info("[Test - {}] Transaction Time: {} ms", i, stopWatch.getTotalTimeMillis());
                data.add(stopWatch.getTotalTimeMillis());

                // AFTER (복구 됐을 영상을 다시 비정상 영상으로 돌려놔야 다음 iteration 때 탐지함)
                sleep();
                musicService.upsertMusic(new Music(brokenVideoId, title, uploader, description, tags, playlist));
            } catch (Exception e) {
                log.error("Error Occurred At {} - {}", i, e.toString(), e);
            }
        }

        System.out.println("------RESULT------");
        long sum = 0L;
        for (int i = 0; i < data.size(); i++) {
            System.out.println((i + 1) + " : " + data.get(i));
            sum += data.get(i);
        }
        System.out.println("------Average------");
        System.out.println(sum / data.size());

    }

    void sleep() {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("스레드 대기 중 인터럽트 발생!");
        }
    }

}
