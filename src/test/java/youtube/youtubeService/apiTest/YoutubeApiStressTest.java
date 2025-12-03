package youtube.youtubeService.apiTest;

import com.google.api.services.youtube.model.PlaylistItem;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import youtube.youtubeService.etc.RecoverTestHelper;
import youtube.youtubeService.domain.ActionLog;
import youtube.youtubeService.repository.playlists.PlaylistRepository;
import youtube.youtubeService.service.ActionLogService;
import youtube.youtubeService.service.musics.MusicService;
import youtube.youtubeService.service.playlists.PlaylistRegistrationUnitService;
import youtube.youtubeService.service.users.UserService;
import youtube.youtubeService.service.users.UserTokenService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@Slf4j
@SpringBootTest
public class YoutubeApiStressTest {

//    @MockitoBean
//    private OutboxProcessor outboxProcessor;
    @MockitoBean
    private ActionLogService actionLogService;

    @Autowired
    private OutboxInsertTest outboxInsertTest;
    @Autowired
    private UserService userService;
    @Autowired
    private UserTokenService userTokenService;
    @Autowired
    private PlaylistRepository playlistRepository;
    @Autowired
    private RecoverTestHelper recoverTestHelper;
    @Autowired
    private MusicService musicService;
//    @Autowired
//    private YoutubeService youtubeService;
    @Autowired
    private PlaylistRegistrationUnitService playlistRegistrationUnitService;

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

            when(actionLogService.findTodayRecoverLog(any(ActionLog.ActionType.class), anyString())).thenReturn(Optional.empty());
        } catch (Exception e) {
        }
    }

    @Test
    void testEachRecover() {
        String userId = "112735690496635663877";
        String refreshToken = userService.getUserByUserId(userId).orElseThrow(() -> new EntityNotFoundException("User not found: " + userId)).getRefreshToken();
        String accessTokenForRecoverUser = userTokenService.getNewAccessTokenByUserId(refreshToken);  // pkc1088
        List<String> videoIdToInsertList = List.of("Oz-b86LZ21c", "cJLH5yXoqi8", "Uc8wmLul3uw", "wtjro7_R3-4");
        List<PlaylistItem> pureApiPlaylistItems = new ArrayList<>();

        // CountDownLatch latch = new CountDownLatch(1);

        try {
            pureApiPlaylistItems = playlistRegistrationUnitService.fetchAllPlaylistItems(userId, playlistId);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 4곡 삭제하고 4곡 VEVO 추가 (트랜잭션 파야지 outboxEventHandler 발동함)
        outboxInsertTest.outboxInsert(pureApiPlaylistItems, accessTokenForRecoverUser, userId, playlistId, videoIdToInsertList);

        for(int i = 1; i <= 8; i++) {
            sleep();
        }

        log.info("[Test Done]");

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
