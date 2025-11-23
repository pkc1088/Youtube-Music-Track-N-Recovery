package youtube.youtubeService.service.youtube;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import youtube.youtubeService.domain.ActionLog;
import youtube.youtubeService.domain.Music;
import youtube.youtubeService.domain.Outbox;
import youtube.youtubeService.domain.Playlists;
import youtube.youtubeService.domain.enums.QuotaType;
import youtube.youtubeService.dto.internal.MusicDetailsDto;
import youtube.youtubeService.dto.internal.PlannedOutboxDto;
import youtube.youtubeService.dto.internal.PlaylistRecoveryPlanDto;
import youtube.youtubeService.dto.internal.PlannedReplacementDto;
import youtube.youtubeService.exception.QuotaExceededException;
import youtube.youtubeService.service.ActionLogService;
import youtube.youtubeService.service.QuotaService;
import youtube.youtubeService.service.musics.MusicService;
import youtube.youtubeService.service.outbox.OutboxService;


@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class RecoveryExecuteService {

    private final ActionLogService actionLogService;
    private final MusicService musicService;
    private final OutboxService outboxService;
    private final QuotaService quotaService;


    @Transactional
    public void executeRecoveryPlan(String userId, Playlists playlist, String accessToken, PlaylistRecoveryPlanDto plan) {
        long reservedQuotaBackedUp = 0L;
        String playlistId = playlist.getPlaylistId();

        try {

            if (plan.videosToInsert() != null && !plan.videosToInsert().isEmpty()) {
                musicService.saveAllVideos(plan.videosToInsert(), playlist);
                log.info("[TX-Update] Playlist {}: Bulk inserted {} musics from sync", playlistId, plan.videosToInsert().size());
            }

            if (plan.videosToDelete() != null && !plan.videosToDelete().isEmpty()) {
                musicService.deleteAllByIdInBatch(plan.videosToDelete());
                log.info("[TX-Update] Playlist {}: Bulk deleted {} musics from sync", playlistId, plan.videosToDelete().size());
            }

            // --- 계획한 작업 실행 (메인 복구 로직) ---

            // Music DB 업데이트 및 ActionLog 저장 (recoveryPlanDtoList)
            if (plan.plannedReplacementDtoList() != null && !plan.plannedReplacementDtoList().isEmpty()) {
                for (PlannedReplacementDto updateTask : plan.plannedReplacementDtoList()) {
                    long pk = updateTask.pk();
                    Music replacementMusic = updateTask.replacementMusic();
                    MusicDetailsDto backupMusic = updateTask.backupMusic();
                    musicService.updateMusicWithReplacement(pk, replacementMusic);
                    actionLogService.actionLogSave(userId, playlistId, ActionLog.ActionType.RECOVER, backupMusic, replacementMusic);
                    log.info("[TX-Recover] Recovered [{}] -> [{}] at pos {}, saved an action log", backupMusic.videoId(), replacementMusic.getVideoId(), pk);
                }

                log.info("[TX-Recover] Playlist {}: Updated {} musics and saved action logs", playlistId, plan.plannedReplacementDtoList().size());
            }

            // Outbox 작업 실행 (Delete-Only, Main-Recovery Add/Delete, Edge-Case-2 Add/Delete 포함)
            if (plan.plannedOutboxList() != null && !plan.plannedOutboxList().isEmpty()) {
                for (PlannedOutboxDto outboxTask : plan.plannedOutboxList()) {

                    Outbox.ActionType actionType = outboxTask.actionType();

                    if (actionType == Outbox.ActionType.ADD) {

                        String videoIdForAction = outboxTask.videoId();

                        reservedQuotaBackedUp = consumeWithOutbox(userId, playlistId, accessToken, reservedQuotaBackedUp,
                                QuotaType.VIDEO_INSERT.getCost(), actionType, videoIdForAction, null);

                        log.info("[TX-Outbox] Register Outbox (INSERT, {})", videoIdForAction);

                    } else if (actionType == Outbox.ActionType.DELETE) {

                        String videoIdForAction = outboxTask.videoId();
                        String playlistItemId = outboxTask.playlistItemIdsToDelete();

                        reservedQuotaBackedUp = consumeWithOutbox(userId, playlistId, accessToken, reservedQuotaBackedUp,
                                QuotaType.VIDEO_DELETE.getCost(), actionType, videoIdForAction, playlistItemId);

                        log.info("[TX-Outbox] Register Outbox (DELETE, {})", videoIdForAction);

                    }
                }

                log.info("[TX-Outbox] Playlist {}: Inserted {} actions into Outbox", playlistId, plan.plannedOutboxList().size());
            }


            // 엣지 케이스 1: DB 에만 더 많음 (DB 삭제)
            if (plan.edgeDelete() != null && !plan.edgeDelete().isEmpty()) {
                musicService.deleteAllByIdInBatch(plan.edgeDelete());
                log.info("[TX-Edge] Playlist {}: Edge Case 1 - Deleted {} extra DB musics", playlistId, plan.edgeDelete().size());
            }

            // 엣지 케이스 2: API 에만 더 많음 (DB 추가): Outbox 작업은 이미 위에서 해줬음
            if (plan.edgeInsert() != null && !plan.edgeInsert().isEmpty()) {
                musicService.bulkInsertMusic(plan.edgeInsert());
                log.info("[TX-Edge] Playlist {}: Edge Case 2 - Inserted {} extra DB musics", playlistId, plan.edgeInsert().size());
            }


        } catch (QuotaExceededException e) {
            log.warn("[TX-ROLLBACK] Quota exceeded for user {}, playlist {}. Rolling back all DB changes. Msg: {}", userId, playlistId, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("[TX-ROLLBACK] Unexpected error during recovery execution for user {}, playlist {}. Rolling back.", userId, playlistId, e);
            throw new RuntimeException("Transactional recovery failed, rolling back", e);
        }
    }

    private long consumeWithOutbox(String userId, String playlistId, String accessToken, long reservedQuotaBackedUp,
                                   long cost, Outbox.ActionType actionType, String videoId, String playlistItemIdToDelete) {

        if(!quotaService.checkAndConsumeLua(userId, cost)) {
            quotaService.rollbackQuota(userId, reservedQuotaBackedUp);
            log.info("[TX-Quota Return] Returned amount of {} reserved quota and throw QEX", reservedQuotaBackedUp);
            throw new QuotaExceededException("Quota Exceed");
        } else {
            reservedQuotaBackedUp += cost;
            outboxService.outboxInsert(actionType, accessToken, userId, playlistId, videoId, playlistItemIdToDelete);
        }

        return reservedQuotaBackedUp;
    }
}
