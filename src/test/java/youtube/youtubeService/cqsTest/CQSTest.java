package youtube.youtubeService.cqsTest;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import youtube.youtubeService.api.YoutubeApiClient;
import youtube.youtubeService.domain.Music;
import youtube.youtubeService.domain.Outbox;
import youtube.youtubeService.domain.Playlists;
import youtube.youtubeService.domain.Users;
import youtube.youtubeService.dto.internal.MusicSummaryDto;
import youtube.youtubeService.dto.internal.OutboxCreatedEventDto;
import youtube.youtubeService.dto.internal.PlaylistRecoveryPlanDto;
import youtube.youtubeService.repository.OutboxRepository;
import youtube.youtubeService.service.ActionLogService;
import youtube.youtubeService.service.musics.MusicService;
import youtube.youtubeService.service.outbox.OutboxEventHandler;
import youtube.youtubeService.service.playlists.PlaylistService;
import youtube.youtubeService.service.users.UserService;
import youtube.youtubeService.service.users.UserTokenService;
import youtube.youtubeService.service.youtube.RecoveryOrchestratorService;
import youtube.youtubeService.service.youtube.RecoveryExecuteService;
import youtube.youtubeService.service.youtube.RecoveryPlanService;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@Slf4j
@SpringBootTest
@Import(CQSTest.TransactionTimeAspect.class)
public class CQSTest {

    @Autowired
    RecoveryOrchestratorService recoveryOrchestratorService;
    @Autowired RecoveryPlanService recoveryPlanService;
    @Autowired RecoveryExecuteService recoveryExecuteService;
    @Autowired YoutubeApiClient youtubeApiClient;

    @Autowired UserService userService;
    @Autowired UserTokenService userTokenService;
    @Autowired MusicService musicService;
    @Autowired PlaylistService playlistService;
    @Autowired OutboxRepository outboxRepository;

    @Autowired TransactionTimeAspect transactionTimeAspect;
    @MockitoSpyBean OutboxEventHandler outboxEventHandler;
    @MockitoSpyBean ActionLogService actionLogService;

    private final String TEST_USER_ID = "112735690496635663877";                    // pkc1088
    private final String TEST_PLAYLIST_ID = "PLNj4bt23Rjfsm0Km4iNM6RSBwXXOEym74";   // MusicListForTest
    private final String TEST_TARGET_VIDEO_ID = "gQHcttM8niw";                      // Regina Belle - WhistleMissile
    private final String REFRESH_TOKEN = "";

    private final int ITERATIONS = 3;
    private final int EXPECTED_EVENT_COUNT = 2;

    @BeforeEach
    void setupMocks() {
        doAnswer(i -> Optional.empty()).when(actionLogService).findTodayRecoverLog(any(), any());
    }

    @Test
    @DisplayName("Real-World CQS Benchmark")
    public void cqsTest() throws InterruptedException {
        List<Long> txTimes = new ArrayList<>();
        List<Long> apiTimes = new ArrayList<>();

        List<Users> users = userService.findAllUsers();
        List<String> userIds = users.stream().map(Users::getUserId).toList();
        List<Playlists> allPlaylists = playlistService.findAllPlaylistsByUserIdsOrderByLastChecked(userIds);//findAllPlaylistsByUserIds(userIds);
        Map<String, List<Playlists>> playlistsByUser = allPlaylists.stream().collect(Collectors.groupingBy(playlist -> playlist.getUser().getUserId()));


        String accessToken = userTokenService.getNewAccessTokenByUserId(REFRESH_TOKEN);

        for (int i = 1; i <= ITERATIONS; i++) {

            log.info("--- Loop {} Start ---", i);
            transactionTimeAspect.clear();
            CountDownLatch loopLatch = new CountDownLatch(EXPECTED_EVENT_COUNT);
            List<Long> capturedOutboxIds = new CopyOnWriteArrayList<>();

            doAnswer(invocation -> {
                OutboxCreatedEventDto dto = invocation.getArgument(0);
                capturedOutboxIds.add(dto.outboxId());
                loopLatch.countDown();
                return invocation.callRealMethod();
            }).when(outboxEventHandler).handleInitialOutboxEvent(any());


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
            long startOutbox = System.currentTimeMillis();

            boolean captured = loopLatch.await(3, TimeUnit.SECONDS); // 모든 이벤트가 리스너에 도달할 때까지 대기, 이벤트 발행 ~ 리스너 수신 사이의 딜레이 최대 3초간 기다림
            if (!captured) { log.warn("Outbox Event was not triggered! (Maybe no actions?)"); continue; }
            log.info("Captured Outbox ID: {}. Start Polling...", capturedOutboxIds);

            while (true) {
                boolean allFinished = true;
                for (Long outboxId : capturedOutboxIds) {

                    Outbox outbox = outboxRepository.findOutboxById(outboxId).orElseThrow(() -> new RuntimeException("Outbox disappeared! ID: " + outboxId));
                    // 하나라도 아직 PENDING/READY 상태면 끝나지 않은 것
                    if (outbox.getStatus() != Outbox.Status.SUCCESS && outbox.getStatus() != Outbox.Status.FAILED) {
                        allFinished = false;
                        break;
                    }
                }

                if (allFinished) break;

                if (System.currentTimeMillis() - startOutbox > 40000) {
                    throw new RuntimeException("Timeout waiting for Outbox completion! (Async thread might be dead)");
                }

                Thread.sleep(20);
            }

            long outboxTime = System.currentTimeMillis() - startOutbox;



            // ----------------------- Loop RESULT & RESET -----------------------
            long statusTime = transactionTimeAspect.getTotalTime();
            log.info("[Test {} - prepareRecoveryPlan] Time: {} ms", i, planTime);
            log.info("[Test {} - executeRecoveryPlan] Time: {} ms", i, executeTime);
            log.info("[Test {} - outboxEventHandler] Time: {} ms", i, outboxTime);
            log.info("[Test {} - outboxStatusUpdater] Time: {} ms", i, statusTime);

            long txTime = executeTime + statusTime;
            long apiTime = planTime + outboxTime;

            txTimes.add(txTime);
            apiTimes.add(apiTime);

            sleep(4);
            resetToDirtyState(accessToken, playlist);
        }

        // ----------------------- FINAL RESULT -----------------------
        finalResult(txTimes, apiTimes);
    }

    private void finalResult(List<Long> txTimes, List<Long> apiTimes) {
        long txTimesTotal = 0L;
        long apiTimesTotal = 0L;

        for (int i = 0; i < ITERATIONS; i++) {
            txTimesTotal += txTimes.get(i);
            apiTimesTotal += apiTimes.get(i);
        }

        log.info("====================[ 성능 리포트 ]====================");
        log.info("txTimesTotal: {}", txTimesTotal / txTimes.size());
        log.info("apiTimesTotal: {}", apiTimesTotal / apiTimes.size());
        log.info("==================[ ITERATIONS 값 ]==================");
        System.out.println("...........................................");
        System.out.println("txTimes: " + txTimes);
        System.out.println("...........................................");
        System.out.println("apiTimes: " + apiTimes);
        System.out.println("...........................................");
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

    @Aspect
    @TestConfiguration
    static class TransactionTimeAspect {

        private final List<Long> executionTimes = new CopyOnWriteArrayList<>();

        @Around("execution(* youtube.youtubeService.service.outbox.OutboxStatusUpdater.updateOutboxStatus(..))")
        public Object measureTransactionTime(ProceedingJoinPoint joinPoint) throws Throwable {
            long start = System.currentTimeMillis();
            try {
                return joinPoint.proceed(); // 실제 메서드 실행 (트랜잭션 수행)
            } finally {
                long duration = System.currentTimeMillis() - start;
                executionTimes.add(duration); // 소요 시간 기록
                log.debug("Captured Background TX Time: {} ms", duration);
            }
        }

        public long getTotalTime() {
            return executionTimes.stream().mapToLong(Long::longValue).sum();
        }

        public void clear() {
            executionTimes.clear();
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
        return new Music(TEST_TARGET_VIDEO_ID, "Regina Belle - So Many Tears(일부공개)", "Whistle_Missile", 200, "", "", playlist);
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

