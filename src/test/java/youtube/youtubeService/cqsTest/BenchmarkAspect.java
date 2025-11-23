package youtube.youtubeService.cqsTest;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;

@Slf4j
@Aspect
@TestConfiguration
public class BenchmarkAspect {

    @Autowired private TestMetricsManager metricsManager;


    @Around("execution(* youtube.youtubeService.service.playlists.PlaylistRegistrationUnitService.fetchAllPlaylistItems(..)) || " +
            "execution(* youtube.youtubeService.service.playlists.PlaylistRegistrationUnitService.fetchAllVideos(..)) || " +
            "execution(* youtube.youtubeService.api.YoutubeApiClient.addVideoToActualPlaylist(..)) || " +
            "execution(* youtube.youtubeService.api.YoutubeApiClient.deleteFromActualPlaylist(..)) || " +
            "execution(* youtube.youtubeService.service.musics.MusicService.searchVideoToReplace(..)) || " +
            "execution(* youtube.youtubeService.service.ActionLogService.findTodayRecoverLog(..))")
    public Object measureSpecificMethods(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.nanoTime();
        String methodName = joinPoint.getSignature().getName();

        try {
            return joinPoint.proceed();
        } finally {
            double duration = (System.nanoTime() - start) / 1_000_000.0;

            metricsManager.recordDetailedTime(methodName, duration);
            log.info("[Delay Details] {}: {} ms", methodName, duration);
        }
    }

    // 1. API 호출 시간 측정 (OutboxProcessor.processOutbox)
    @Around("execution(* youtube.youtubeService.service.outbox.OutboxProcessor.processOutbox(..))")
    public Object measureApiTime(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.nanoTime();
        try {
            return joinPoint.proceed();
        } finally {
            long duration = (System.nanoTime() - start) / 1_000_000;
            metricsManager.recordApiTime(duration);
            // log.info("[AOP] API Call Time: {} ms", duration);
        }
    }

    // 2. DB 상태 업데이트 시간 측정 + Latch 해제 (updateOutboxStatus 는 비동기 로직의 마지막 단계이므로 여기서 카운트다운)
    @Around("execution(* youtube.youtubeService.service.outbox.OutboxStatusUpdater.updateOutboxStatus(..))")
    public Object measureDbTime(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.nanoTime();
        try {
            return joinPoint.proceed();
        } finally {
            long duration = (System.nanoTime() - start) / 1_000_000;
            metricsManager.recordDbTime(duration);
            // DB 작업까지 끝 -> 메인 스레드 깨움
            metricsManager.taskFinished();
            // log.info("[AOP] Async DB Update Time: {} ms", duration);
        }
    }
}
