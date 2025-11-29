package youtube.youtubeService.cqsTest;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import youtube.youtubeService.api.YoutubeApiClient;
import youtube.youtubeService.domain.Music;
import youtube.youtubeService.domain.Playlists;
import youtube.youtubeService.domain.Users;
import youtube.youtubeService.dto.internal.MusicSummaryDto;
import youtube.youtubeService.dto.internal.PlaylistRecoveryPlanDto;
import youtube.youtubeService.repository.OutboxRepository;
import youtube.youtubeService.service.ActionLogService;
import youtube.youtubeService.service.musics.MusicService;
import youtube.youtubeService.service.playlists.PlaylistService;
import youtube.youtubeService.service.users.UserService;
import youtube.youtubeService.service.youtube.RecoveryExecuteService;
import youtube.youtubeService.service.youtube.RecoveryPlanService;

import java.util.*;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@Slf4j
@SpringBootTest
@Import({BenchmarkAspect.class, TestMetricsManager.class})
public class ImprovedCQSTest {

    @Autowired RecoveryPlanService recoveryPlanService;
    @Autowired RecoveryExecuteService recoveryExecuteService;
    @Autowired YoutubeApiClient youtubeApiClient;
    @Autowired UserService userService;
    @Autowired MusicService musicService;
    @Autowired PlaylistService playlistService;
    @Autowired OutboxRepository outboxRepository;

    @Autowired TestMetricsManager metricsManager;

    @MockitoSpyBean ActionLogService actionLogService;

    private final String TEST_USER_ID = "112735690496635663877";                    // pkc1088
    private final String TEST_PLAYLIST_ID = "PLNj4bt23RjfsajCmUzYQbvZp0v-M8PU8t";   // TestPlaylist
    private final String TEST_TARGET_VIDEO_ID = "gQHcttM8niw";                      // Regina Belle - WhistleMissile
    private final String REFRESH_TOKEN = "";

    private final int ITERATIONS = 50;
    private final int EXPECTED_EVENT_COUNT = 2;

    @BeforeEach
    void setupMocks() {
        doAnswer(i -> Optional.empty()).when(actionLogService).findTodayRecoverLog(any(), any());
    }

    @Test
    @DisplayName("Real-World Improved CQS Benchmark")
    public void benchmarkPrecisionTest() throws InterruptedException {

        List<Users> users = userService.findAllUsers();
        List<String> userIds = users.stream().map(Users::getUserId).toList();
        List<Playlists> allPlaylists = playlistService.findAllPlaylistsByUserIds(userIds);
        Map<String, List<Playlists>> playlistsByUser = allPlaylists.stream().collect(Collectors.groupingBy(playlist -> playlist.getUser().getUserId()));

        String accessToken = userService.getNewAccessTokenByUserId(TEST_USER_ID, REFRESH_TOKEN);

        // Warm-up (첫 번째 실행 버리기용)
        log.info("...Warming up...");
        runOneCycle(true, 0, playlistsByUser, accessToken);
        metricsManager.clearDetailedMetrics();
        log.info("...Warm-up done...");

        List<Long> legacyTimes = new ArrayList<>();
        List<Long> cqsTxTimes = new ArrayList<>();

        for (int i = 1; i <= ITERATIONS; i++) {
            runOneCycle(false, i, playlistsByUser, accessToken, legacyTimes, cqsTxTimes);
        }

        // ----------------------- FINAL RESULT -----------------------
        finalResult(legacyTimes, cqsTxTimes);
        metricsManager.printDetailedStats();
    }

    @SafeVarargs
    private void runOneCycle(boolean isWarmUp, int loopCount, Map<String, List<Playlists>> playlistsByUser, String accessToken, List<Long>... stats) throws InterruptedException {

        log.info("--- Loop {} Start ---", loopCount);
        metricsManager.resetLatch(EXPECTED_EVENT_COUNT);


        // ----------------------- PLAN -----------------------
        long startPlan = System.currentTimeMillis();

        List<Playlists> playListsSet = playlistsByUser.getOrDefault(TEST_USER_ID, Collections.emptyList());
        Playlists playlist = playListsSet.get(0);
        List<MusicSummaryDto> musicForThisPlaylist = musicService.findAllMusicSummaryByPlaylistIds(playListsSet).stream().collect(Collectors.groupingBy(MusicSummaryDto::playlistId)).getOrDefault(playlist.getPlaylistId(), Collections.emptyList());
        PlaylistRecoveryPlanDto plans = null;
        try {
            plans = recoveryPlanService.prepareRecoveryPlan(TEST_USER_ID, playlist, "KR", accessToken, musicForThisPlaylist);
        } catch (Exception e) {
            log.info("[Unexpected error during 'prepareRecoveryPlan': ]", e);
        }

        long planTime = System.currentTimeMillis() - startPlan;


        // ----------------------- EXECUTE -----------------------
        long startExecute = System.currentTimeMillis();

        if (plans != null && plans.hasActions()) {
            recoveryExecuteService.executeRecoveryPlan(TEST_USER_ID, playlist, accessToken, plans);
        }

        long executeTime = System.currentTimeMillis() - startExecute;


        // ----------------------- OUTBOX -----------------------
        metricsManager.await(); // Aspect 가 API->DB 작업 후 metricsManager.taskFinished()를 호출하면 여기서 즉시 깨어남
        long outboxTime = metricsManager.getTotalApiTime();
        long statusTime = metricsManager.getTotalDbTime();

        if (!isWarmUp && stats.length > 0) {
            long legacyTime = planTime + executeTime + outboxTime + statusTime;
            long cqsTxTime = executeTime + statusTime;

            stats[0].add(legacyTime);
            stats[1].add(cqsTxTime);

            log.info("[Loop Result {}]: planTime({}), executeTime({}), outboxTime({}), statusTime({}) => legacyTime({}), cqsTxTime({})",
                            loopCount, planTime, executeTime, outboxTime, statusTime, legacyTime, cqsTxTime);
        }

        sleep(4);
        resetToDirtyState(accessToken, playlist);
    }

    private void finalResult(List<Long> legacyTimes, List<Long> cqsTxTimes) {

        System.out.println("====================[ 성능 리포트 ]====================");
        System.out.println("Legacy Average Time: " + legacyTimes.stream().mapToLong(Long::longValue).average().orElse(0));
        System.out.println("CQS Average Time: " + cqsTxTimes.stream().mapToLong(Long::longValue).average().orElse(0));
        System.out.println("legacyTotalTimes: " + legacyTimes);
        System.out.println("cqsMainTxTimes: " + cqsTxTimes);
        System.out.println("=====================================================");
    }

    void resetToDirtyState(String accessToken, Playlists playlist) {
        try {
            // 1. 비정상 영상 다시 추가
            youtubeApiClient.addVideoToActualPlaylist(accessToken, TEST_PLAYLIST_ID, TEST_TARGET_VIDEO_ID);
            // 2. DB 에도 다시 비정상 영상 추가
            musicService.upsertMusic(makeMusic(playlist));
            // 3. 복구 영상 제거(DB, API): 사실 안해도 됨, 괜히 할당량 소모하는데

        } catch (Exception e) {
            log.info("[Unexpected error during 'resetToDirtyState': ]", e);
        }
    }

    private Music makeMusic(Playlists playlist) {
//        Music music = new Music();
//        music.setVideoId(TEST_TARGET_VIDEO_ID);
//        music.setVideoTitle("Regina Belle - So Many Tears(일부공개)");
//        music.setVideoUploader("Whistle_Missile");
//        music.setVideoDescription("");
//        music.setVideoTags("");
//        music.setPlaylist(playlist);
//        return music;
        return new Music(TEST_TARGET_VIDEO_ID, "Regina Belle - So Many Tears(일부공개)", "Whistle_Missile", "", "", playlist);
    }

    void sleep(int seconds) {
        while (seconds-->0) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("스레드 대기 중 인터럽트 발생!");
            }
        }
    }
}

