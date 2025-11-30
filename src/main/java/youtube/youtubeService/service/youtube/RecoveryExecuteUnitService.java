package youtube.youtubeService.service.youtube;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import youtube.youtubeService.domain.ActionLog;
import youtube.youtubeService.domain.Outbox;
import youtube.youtubeService.domain.Playlists;
import youtube.youtubeService.domain.enums.QuotaType;
import youtube.youtubeService.dto.internal.PlannedOutboxDto;
import youtube.youtubeService.dto.internal.PlaylistRecoveryPlanDto;
import youtube.youtubeService.dto.internal.RecoveryTaskDto;
import youtube.youtubeService.exception.QuotaExceededException;
import youtube.youtubeService.service.ActionLogService;
import youtube.youtubeService.service.QuotaService;
import youtube.youtubeService.service.musics.MusicService;
import youtube.youtubeService.service.outbox.OutboxService;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecoveryExecuteUnitService {

    private final ActionLogService actionLogService;
    private final MusicService musicService;
    private final OutboxService outboxService;
    private final QuotaService quotaService;

    @Transactional
    public void processBulkSync(Playlists playlist, PlaylistRecoveryPlanDto plan) {

        // 최신화 - 추가
        if (plan.toInsertVideoIds() != null && !plan.toInsertVideoIds().isEmpty()) {
            musicService.saveAllVideos(plan.toInsertVideoIds(), playlist);
            log.info("[TX-Update] Bulk inserted {} musics for sync (Playlist: {})", plan.toInsertVideoIds().size(), playlist.getPlaylistId());
        }

        // 최신화 - 삭제
        if (plan.toDeleteVideoIds() != null && !plan.toDeleteVideoIds().isEmpty()) {
            musicService.deleteAllByIdInBatch(plan.toDeleteVideoIds());
            log.info("[TX-Update] Bulk deleted {} musics for sync (Playlist: {})", plan.toDeleteVideoIds().size(), playlist.getPlaylistId());
        }

        // Edge Case 1: DB(4) > API(2)
        if (plan.redundantEdgeDeleteIds() != null && !plan.redundantEdgeDeleteIds().isEmpty()) {
            musicService.deleteAllByIdInBatch(plan.redundantEdgeDeleteIds());
            log.info("[TX-Edge-1] Deleted {} extra musics from DB (Playlist: {})", plan.redundantEdgeDeleteIds().size(), playlist.getPlaylistId());
        }
    }

    @Transactional
    public void processSingleTask(String userId, String playlistId, String accessToken, RecoveryTaskDto task) {

        long requiredCost = task.outboxActions()
                .stream()
                .mapToLong(action -> (action.actionType() == Outbox.ActionType.ADD) ? QuotaType.VIDEO_INSERT.getCost() : QuotaType.VIDEO_DELETE.getCost())
                .sum();

        // 할당량 선확보: 실패 시 예외 던짐 -> 트랜잭션 롤백 -> 상위 루프에서 다음으로 skip
        if (!quotaService.checkAndConsumeLua(userId, requiredCost)) throw new QuotaExceededException("[Not enough quota for this task]");

        try {
            if (task.type() == RecoveryTaskDto.TaskType.RECOVERY && task.swapInfo() != null) {  // 기본 복구

                musicService.updateMusicWithReplacement(task.swapInfo().pk(), task.swapInfo().replacementMusic());
                actionLogService.actionLogSave(userId, playlistId, ActionLog.ActionType.RECOVER, task.swapInfo().backupMusic(), task.swapInfo().replacementMusic());
                log.info("[TX-Recover] Recovered ({} -> {}) at pos {}, saved an action log",  task.swapInfo().backupMusic().videoId(), task.swapInfo().replacementMusic().getVideoId(), task.swapInfo().pk());

            } else if (task.type() == RecoveryTaskDto.TaskType.EXTRA_RECOVERY) { // Edge Case 2: DB(2) < API(4)

                musicService.upsertMusic(task.replacementMusic());
                log.info("[TX-Edge-2] Inserted one extra music ({}) into DB (Playlist: {})", task.replacementMusic().getVideoId(), playlistId);
            }

            for (PlannedOutboxDto outboxDto : task.outboxActions()) {   // OUTBOX 처리 (DELETE_ONLY 등)
                outboxService.outboxInsert(outboxDto.actionType(), accessToken, userId, playlistId, outboxDto.videoId(), outboxDto.playlistItemIdsToDelete());
                log.info("[TX-Outbox] Registered Outbox ({}, {})", outboxDto.actionType(), outboxDto.videoId());
            }

        } catch (Exception e) {
            // DB 작업 중 에러 발생 시, 확보했던 할당량 반환(Rollback) 후 예외 전파
            quotaService.rollbackQuota(userId, requiredCost);
            log.info("[TX-Rollback] Returned {} quota to User({})", requiredCost, userId);
            throw e;
        }

    }
}
