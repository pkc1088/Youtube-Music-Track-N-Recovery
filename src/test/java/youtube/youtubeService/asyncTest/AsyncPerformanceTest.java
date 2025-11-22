package youtube.youtubeService.asyncTest;

import com.google.api.services.youtube.model.*;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.util.StopWatch;
import youtube.youtubeService.api.YoutubeApiClient;
import youtube.youtubeService.domain.Music;
import youtube.youtubeService.domain.Playlists;
import youtube.youtubeService.domain.Users;
import youtube.youtubeService.dto.internal.VideoFilterResultPageDto;
import youtube.youtubeService.repository.musics.SdjMusicRepository;
import youtube.youtubeService.repository.playlists.SdjPlaylistRepository;
import youtube.youtubeService.repository.users.UserRepository;
import youtube.youtubeService.service.ActionLogService;
import youtube.youtubeService.service.QuotaService;
import youtube.youtubeService.service.musics.MusicService;
import youtube.youtubeService.service.outbox.OutboxEventHandler;
import youtube.youtubeService.service.playlists.PlaylistRegistrationUnitService;
import youtube.youtubeService.service.playlists.PlaylistService;
import youtube.youtubeService.service.users.UserService;
import youtube.youtubeService.service.youtube.RecoverOrchestrationService;
import java.io.IOException;
import java.util.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@Slf4j
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class AsyncPerformanceTest {

    @Autowired
    private RecoverOrchestrationService recoverOrchestrationService;

    // === 모킹(Mocking) 대상 ===
    @MockitoSpyBean
    private UserService userService;
    @MockitoSpyBean
    private PlaylistService playlistService;
    @MockitoSpyBean
    private ActionLogService actionLogService;
    @MockitoSpyBean
    private MusicService musicService;
    @MockitoSpyBean
    private PlaylistRegistrationUnitService playlistRegistrationUnitService;
    @MockitoSpyBean
    private UserRepository userRepository;
    @MockitoSpyBean
    private OutboxEventHandler outboxEventHandler;
    @MockitoBean
    private YoutubeApiClient youtubeApiClient;
    @MockitoBean
    private QuotaService quotaService;

    @Autowired
    SdjPlaylistRepository sdjPlaylistRepository;
    @Autowired
    SdjMusicRepository musicRepository;

    private final List<String> mockUserIds = new ArrayList<>();
    private final int MOCK_USER_COUNT = 100;
    private final int PLAYLISTS_PER_USER = 10;
    private final int MUSIC_PER_PLAYLIST = 100;

    @BeforeAll
    void setupMocks() throws IOException {

        log.info("--- Mocks Setup Start ---");

        // 1. (UserService) 유저 제한 해제 (즉시 "mock-token" 반환)
        doAnswer(i -> {
            Thread.sleep(370);
            return "mock-access-token";
        }).when(userService).getNewAccessTokenByUserId(any(), any());

        // 2. (Planner) '읽기' 할당량 항상 통과
        doReturn(true).when(quotaService).checkAndConsumeLua(any(), any(Long.class));

        // 3. (Planner) API 호출 모킹
        doAnswer(i -> {
            Thread.sleep(750);
            String userId = i.getArgument(0, String.class);
            String playlistId = i.getArgument(1, String.class);
            return makeFakePlaylistItem(userId, playlistId);
        }).when(playlistRegistrationUnitService).fetchAllPlaylistItems(any(), any());

        doAnswer(i -> {
            Thread.sleep(700);
            String userId = i.getArgument(0, String.class);
            List<String> videoIds = i.getArgument(1);
            return makeFakeVideo(userId, videoIds);
        }).when(playlistRegistrationUnitService).fetchAllVideos(any(), anyList(), any());

        doAnswer(i -> {
            Thread.sleep(35);
            return Optional.empty();
        }).when(actionLogService).findTodayRecoverLog(any(), any());

        doAnswer(i -> {
            Thread.sleep(1550); // GEMINI Search + API Query search + API SingleVideo search
            Music backupMusic = i.getArgument(0, Music.class);
            return makeFakeReplacementVideo(backupMusic);
        }).when(musicService).searchVideoToReplace(any());

        doAnswer(i -> {
            Thread.sleep(950);
            return true;
        }).when(youtubeApiClient).addVideoToActualPlaylist(any(), any(), any());

        doAnswer(i -> {
            Thread.sleep(770);
            return true;
        }).when(youtubeApiClient).deleteFromActualPlaylist(any(), any());

        doNothing().when(outboxEventHandler).retryFailedOutboxEvents(any());

        log.info("--- Mocks Setup Done ---");
    }

    private VideoFilterResultPageDto makeFakeVideo(String userId, List<String> videoIds) {
        List<Video> legalVideos = new ArrayList<>();
        List<Video> unlistedCountryVideos = new ArrayList<>();
        for (String videoId : videoIds) {
            if (videoId.startsWith("video-1-")) {
                unlistedCountryVideos.add(makeFakeSimpleVideo(videoId));
            } else {
                legalVideos.add(makeFakeSimpleVideo(videoId));
            }
        }

        return new VideoFilterResultPageDto(legalVideos, unlistedCountryVideos);
    }

    private Video makeFakeReplacementVideo(Music backupMusic) {
        Video video = new Video();
        video.setId("replacement-video");
        VideoSnippet snippet = new VideoSnippet();
        snippet.setTitle(backupMusic.getVideoTitle());
        snippet.setChannelTitle(backupMusic.getVideoUploader());
        snippet.setDescription(backupMusic.getVideoDescription());
        snippet.setTags(Collections.singletonList(backupMusic.getVideoTags()));
        video.setSnippet(snippet);

        return video;
    }

    private Video makeFakeSimpleVideo(String videoId) {
        Video video = new Video();
        video.setId(videoId);
        return video;
    }

    public Music makeFakeMusic(String videoId, String title, String uploader, String description, String tags, Playlists playlist) {
        Music music = new Music();
        music.setVideoId(videoId);
        music.setVideoTitle(title);
        music.setVideoUploader(uploader);
        music.setVideoDescription(description);
        music.setVideoTags(tags);
        music.setPlaylist(playlist);
        return music;
    }

    private List<PlaylistItem> makeFakePlaylistItem(String userId, String playlistId) {
        List<PlaylistItem> mockPlaylistItems = new ArrayList<>();
        // PlaylistItem getId(), getSnippet().getResourceId().getVideoId()

        for (int m = 1; m <= MUSIC_PER_PLAYLIST; m++) {
            PlaylistItem playlistItem = new PlaylistItem();
            playlistItem.setId("playlistItem-" + m + "-" + playlistId);
            PlaylistItemSnippet snippet = new PlaylistItemSnippet();
            ResourceId resourceId = new ResourceId();
            resourceId.setVideoId("video-" + m + "-" +  playlistId);
            snippet.setResourceId(resourceId);
            playlistItem.setSnippet(snippet);

            mockPlaylistItems.add(playlistItem);
        }
        return mockPlaylistItems;
    }

    private List<Users> createMockUserEntities(boolean withId) {
        List<Users> users = new ArrayList<>();

        for (int i = 1; i <= MOCK_USER_COUNT; i++) {
            String userId = "user-" + i;
            if (withId) mockUserIds.add(userId); // 기존 데이터와 충돌 안일어나게 mockUserIds 에 담은 후 이것만 날리려고 셋팅한듯

            users.add(new Users(userId, Users.UserRole.USER, "USERNAME", "CHANNEL", "EMAIL", "KR", "mock-refresh-token"));
        }
        return users;
    }

    @BeforeEach
    void createMockData() {
        log.info("--- Creating Mock Data ({}) Users, Playlists, Musics) ---", MOCK_USER_COUNT);

        List<Users> users = createMockUserEntities(true);
        for (Users user : users) userRepository.saveUser(user);

        List<Playlists> allPlaylists = new ArrayList<>();
        List<Music> allMusic = new ArrayList<>();

        for (Users user : users) {
            // User:        user-3
            // playlist:    playlist-2-user-3
            // music:       video-1-playlist-2-user-3
            mockUserIds.add(user.getUserId());

            for (int p = 1; p <= PLAYLISTS_PER_USER; p++) {
                Playlists playlist = new Playlists("playlist-" + p + "-" + user.getUserId(), "Mock Playlist", Playlists.ServiceType.RECOVER, user);
                allPlaylists.add(playlist);

                for (int m = 1; m <= MUSIC_PER_PLAYLIST; m++) {
                    // 100 개 중 1개는 복구 대상(illegal)이라고 가정
                    String videoId = "video-" + m + "-" +  playlist.getPlaylistId(); // (m == 1) ? "illegal-video-" + i :  여기서 하는게 아님 DB 는 항상 깨끗해야함
                    allMusic.add(makeFakeMusic(videoId, "Mock Title", "Mock Uploader", "Mock Description", "Mock Tags", playlist));
                }

            }
        }

        sdjPlaylistRepository.saveAll(allPlaylists);
        musicRepository.saveAll(allMusic);

        log.info("--- Mock Data Creation Done ---");
    }


    @AfterEach
    void cleanupMockData() {
        log.info("--- Cleaning Up Mock Data ---");
        for (String userId : mockUserIds) userRepository.deleteById(userId);
        mockUserIds.clear();
        log.info("--- Mock Data Cleanup Done ---");
    }

    @Test
    @DisplayName("전체 복구 배치 성능 측정 (비동기)")
    void measureBatchPerformance() {

        log.info("==================================================");
        log.info("Starting Batch Performance Test (@ActiveProfiles('{}'))", System.getProperty("spring.profiles.active", "default"));
        log.info("==================================================");

        StopWatch totalWatch = new StopWatch();
        totalWatch.start();

        // 핵심 측정 대상
        recoverOrchestrationService.allPlaylistsRecoveryOfAllUsers();

        totalWatch.stop();

        log.info("====================[ 성능 리포트 ]====================");
        log.info("총 유저: {} 명", MOCK_USER_COUNT);
        log.info("총 플레이리스트: {} 개", MOCK_USER_COUNT * PLAYLISTS_PER_USER);
        log.info("총 실행 시간: {} ms ({} 초)", totalWatch.getTotalTimeMillis(), totalWatch.getTotalTimeSeconds());
        log.info("==================================================");
    }


}

/*
//        playlistRegistrationUnitService.forTestSavePlaylists(allPlaylists);
//        playlistRegistrationUnitService.forTestSaveMusics(allMusic);
    @Transactional
    public void forTestSavePlaylists(List<Playlists> allPlaylists) {
        for (Playlists p : allPlaylists) {
            if (entityManager.find(Playlists.class, p.getPlaylistId()) == null) {
                entityManager.persist(p);
            } else {
                entityManager.merge(p); // ID가 있으면 merge
            }
        }
        entityManager.flush();
        entityManager.clear();
    }
    @Transactional
    public void forTestSaveMusics(List<Music> allMusic) {
        for (Music m : allMusic) {
            entityManager.merge(m);
        }
        entityManager.flush();
        entityManager.clear();
    }



 */