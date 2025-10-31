package youtube.youtubeService;

import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoSnippet;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Spy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.util.StopWatch;
import youtube.youtubeService.api.YoutubeApiClient;
import youtube.youtubeService.domain.*;
import youtube.youtubeService.repository.ActionLogRepository;
import youtube.youtubeService.repository.OutboxRepository;
import youtube.youtubeService.repository.musics.MusicRepository;
import youtube.youtubeService.repository.playlists.PlaylistRepository;
import youtube.youtubeService.repository.users.UserRepository;
import youtube.youtubeService.service.ActionLogService;
import youtube.youtubeService.service.QuotaService;
import youtube.youtubeService.service.musics.MusicService;
import youtube.youtubeService.service.outbox.OutboxEventHandler;
import youtube.youtubeService.service.outbox.OutboxProcessor;
import youtube.youtubeService.service.outbox.OutboxService;
import youtube.youtubeService.service.outbox.OutboxStatusUpdater;
import youtube.youtubeService.service.playlists.PlaylistService;
import youtube.youtubeService.service.youtube.YoutubeService;

import java.io.IOException;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Slf4j
@SpringBootTest
public class OutboxPerformanceTest {

    @MockitoBean
    private PlaylistService playlistService;
    @MockitoBean
    private QuotaService quotaService;
    @MockitoBean
    private OutboxProcessor outboxProcessor;
    @MockitoBean
    private YoutubeApiClient youtubeApiClient;
    @MockitoSpyBean
    private MusicService musicService;
    @MockitoBean
    private ActionLogService actionLogService;

    @MockitoSpyBean
    private OutboxService outboxService;
    @Autowired
    private UserRepository usersRepository;
    @Autowired
    private PlaylistRepository playlistRepository;
    @Autowired
    private MusicRepository musicRepository;
    @Autowired
    private OutboxRepository outboxRepository;
    @Autowired
    private ActionLogRepository actionLogRepository;
    @Autowired
    private OutboxStatusUpdater outboxStatusUpdater;
    @Autowired
    private YoutubeService youtubeService;
    @Autowired
    private OutboxEventHandler outboxEventHandler;


    private Users testUser;
    private Playlists testPlaylist;
    private String testBrokenMusic;

    @BeforeEach
    void setUp() {

        testUser = new Users("112735690496635663877", Users.UserRole.ADMIN, "pkc1088", "UC6SN0-0k6z1fj5LmhYHd5UA", "pkc1088@gmail.com", "KR", "refreshToken123");
        testPlaylist = new Playlists("PLNj4bt23RjfsajCmUzYQbvZp0v-M8PU8t", "Test Playlist", Playlists.ServiceType.RECOVER, testUser);
        testBrokenMusic = "XzEoBAltBII"; //whistleMissile

        Music backupMusic = new Music();
        backupMusic.setId(1L);
        backupMusic.setVideoId(testBrokenMusic);

        // 2. (핵심) 모든 API 검색 노이즈 통제
        try {
            // 2-1. [MockBean] updatePlaylist Mocking (가장 큰 노이즈 제거)
            Map<String, List<String>> mockIllegalVideos = new HashMap<>();
            // 비정상 비디오 1개 케이스 시뮬레이션
            mockIllegalVideos.put(testBrokenMusic, List.of("playlistItemId123"));
            when(playlistService.updatePlaylist(anyString(), anyString(), any(Playlists.class))).thenReturn(mockIllegalVideos);

            // 2-2. [MockBean] 할당량 서비스 Mocking
            when(quotaService.checkAndConsumeLua(anyString(), anyLong())).thenReturn(true);

            // 2-3. [MockBean] YoutubeApiClient 검색 Mocking (recentLogOpt.isPresent() 경로용) <- 사실상 안 쓰임
            when(youtubeApiClient.fetchSingleVideo(anyString())).thenReturn(new Video().setId("REUSED_VIDEO_ID"));
            when(actionLogService.findTodayRecoverLog(any(ActionLog.ActionType.class), anyString())).thenReturn(Optional.empty());

            doReturn(new Music(358L, "wtjro7_R3", "The Manhattans - Kiss and Say Goodbye (Official Video)",
                    "TheManhattansVEVO", "Many months have passed us by", "7th album", testPlaylist))
                    .when(musicService).makeVideoToMusic(any(Video.class), any(Playlists.class));

            doNothing().when(musicService).dBTrackAndRecoverPosition(anyString(), any(Music.class), anyLong());
            // 2-4. [SpyBean] MusicService의 API 검색 부분만 Mocking
            doReturn(List.of(backupMusic)).when(musicService).getMusicListFromDBThruMusicId(anyString(), anyString());
            doReturn(new Video().setId("NEW_REPLACEMENT_ID")).when(musicService).searchVideoToReplace(any(Music.class), any(Playlists.class));

            // 3. (핵심) 이벤트 리스너 무력화
            when(outboxProcessor.processOutbox(any())).thenReturn(true);

        } catch (IOException e) {
            // (Mocking 예외 처리)
        }

//        doNothing().when(outboxProcessor).processOutbox(any());
    }

    @Test
    @DisplayName("Outbox 아키텍처 - 메인 트랜잭션 성능 측정 (모든 외부 API Mocking)")
    @Transactional // 테스트 종료 후 모든 DB 변경 롤백
    void measureOutboxTransactionTime_WithFullApiMocking() throws IOException {
        String accessToken = "test-access-token";
        String countryCode = "KR";

        // === When ===
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        youtubeService.fileTrackAndRecover(testUser.getUserId(), testPlaylist, countryCode, accessToken);
        stopWatch.stop();
        log.info("[Outbox] Main Transaction (Full API Mocked) Time: {} ms", stopWatch.getTotalTimeMillis());

        // === Then (검증) ===
        // 1. (검증) 트랜잭션 중 외부 "쓰기" API는 절대 호출되지 않았음
        verify(youtubeApiClient, never()).addVideoToActualPlaylist(any(), any(), any());
        verify(youtubeApiClient, never()).deleteFromActualPlaylist(any(), any());
        // 2. (검증) "검색" API는 Spy/Mock된 메서드만 호출됨
        verify(musicService, times(1)).searchVideoToReplace(any(Music.class), any(Playlists.class));
        verify(playlistService, times(1)).updatePlaylist(anyString(), anyString(), any(Playlists.class));
        // 3. (검증) "내부 작업"은 실제 호출되었음 (Setup의 mockIllegalVideos 기준)
        // (1개 비디오 -> 총 1회 복구 시도)
        int expectedRecoverCount = 1;
        verify(actionLogService, times(expectedRecoverCount)).actionLogSave(any(), any(), any(), any(), any());
        int expectedOutboxEvents = 2; // (ADD 1, DELETE 1)
        verify(outboxService, times(expectedOutboxEvents)).outboxInsert(any(Outbox.ActionType.class), anyString(), anyString(), anyString(), anyString(), anyString());
        // 4. (검증) 이벤트 핸들러는 호출되지 않았음 (트랜잭션 분리 성공)
        verify(outboxEventHandler, never()).handleOutboxEvent(any());
    }


    @Test
    void measureOutboxTransactionTimeWithAutoStatusUpdateWith100Times() {
//        String userId = "107155055893692546350";
//        String accessToken = userService.getNewAccessTokenByUserId(userId);
//        String videoId = "XzEoBAltBII";
//        String status = "public"; //"private";
//
//        youtubeApiClient.updateVideoPrivacyStatus(accessToken, videoId, status);
    }

    @Test
    void measureOutboxTransactionTime() {
        //long overallStart = System.nanoTime();
        // recoverOrchestrationService.allPlaylistsRecoveryOfAllUsers();
        //long overallEnd = System.nanoTime();

        //long allTx = (overallEnd - overallStart) / 1_000_000;
        //long outboxTx = (end - mid) / 1_000_000;
        //log.info("[Outbox ALL Transaction Time] {} ms", allTx);
        //log.info("[Outbox requires_new update transaction time] {} ms", outboxTx);
        //log.info("[Outbox total transaction time] {} ms", mainTx + outboxTx);
    }


}
