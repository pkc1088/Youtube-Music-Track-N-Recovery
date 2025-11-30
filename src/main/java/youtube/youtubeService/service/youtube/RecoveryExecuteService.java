package youtube.youtubeService.service.youtube;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import youtube.youtubeService.domain.Playlists;
import youtube.youtubeService.dto.internal.PlaylistRecoveryPlanDto;
import youtube.youtubeService.dto.internal.RecoveryTaskDto;
import youtube.youtubeService.exception.QuotaExceededException;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecoveryExecuteService {

    private final RecoveryExecuteUnitService recoveryExecuteUnitService;

    public void executeRecoveryPlan(String userId, Playlists playlist, String accessToken, PlaylistRecoveryPlanDto plan) {
        try {
            // --- 플레이리스트 최신화 ---
            recoveryExecuteUnitService.processBulkSync(playlist, plan);
            // --- Task 단위 작업 실행  ---
            if (plan.tasks() != null && !plan.tasks().isEmpty()) {
                int successCount = 0;
                int skipCount = 0;

                for (RecoveryTaskDto task : plan.tasks()) {
                    try {
                        recoveryExecuteUnitService.processSingleTask(userId, playlist.getPlaylistId(), accessToken, task);
                        successCount++;

                    } catch (QuotaExceededException e) {
                        skipCount++;
                        log.warn("[Skipping task due to quota limit. (TaskType: {}), Attempting next...]", task.type());

                    } catch (Exception e) {
                        log.error("[Task Error] Skipping specific task due to error", e);
                    }
                }

                log.info("Task Result: Success={}, Skipped={}", successCount, skipCount);
            }

        } catch (Exception e) {
            log.error("[Unexpected Error] During recovery execution for user {}, playlist {}. Rolling back.", userId, playlist.getPlaylistId(), e);
            throw new RuntimeException("ExecuteRecoveryPlan failed, Rolling back", e);
        }
    }

}