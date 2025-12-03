package youtube.youtubeService.service.youtube;

import com.google.api.services.youtube.model.Video;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import youtube.youtubeService.api.YoutubeApiClient;
import youtube.youtubeService.domain.ActionLog;
import youtube.youtubeService.domain.Music;
import youtube.youtubeService.domain.Outbox;
import youtube.youtubeService.domain.Playlists;
import youtube.youtubeService.domain.enums.QuotaType;
import youtube.youtubeService.dto.internal.*;
import youtube.youtubeService.exception.quota.QuotaExceededException;
import youtube.youtubeService.service.ActionLogService;
import youtube.youtubeService.service.QuotaService;
import youtube.youtubeService.service.musics.MusicConverterHelper;
import youtube.youtubeService.service.musics.MusicService;
import youtube.youtubeService.service.playlists.PlaylistStateCheckService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecoveryPlanService {

    private final PlaylistStateCheckService playlistStateCheckService;
    private final MusicConverterHelper musicConverterHelper;
    private final ActionLogService actionLogService;
    private final YoutubeApiClient youtubeApiClient;
    private final MusicService musicService;
    private final QuotaService quotaService;


    public PlaylistRecoveryPlanDto prepareRecoveryPlan(String userId, Playlists playlist, String countryCode, String accessToken, List<MusicSummaryDto> preFetchedMusicList) throws IOException {

        List<RecoveryTaskDto> recoveryTasks = new ArrayList<>();
        List<Long> redundantEdgeDeleteIds = new ArrayList<>();
        PlannedPlaylistUpdateDto plannedPlaylistUpdateDto;
        String playlistId = playlist.getPlaylistId();

        plannedPlaylistUpdateDto = playlistStateCheckService.compareApiAndDbState(userId, countryCode, playlist, preFetchedMusicList);

        for (Map.Entry<String, List<String>> entry : plannedPlaylistUpdateDto.illegalVideos().entrySet()) {

            String videoIdToDelete = entry.getKey();
            List<String> playlistItemIdsToDelete = entry.getValue();
            int apiDuplicatedCount = playlistItemIdsToDelete.size();

            List<MusicDetailsDto> backupMusicListFromDb = musicService.getMusicListFromDBThruMusicId(videoIdToDelete, playlistId);  //  DB 에서 해당 videoId + playlistId 를 가진 모든 음악 조회

            if (backupMusicListFromDb.isEmpty()) {
                IntStream.range(0, apiDuplicatedCount)
                        .forEach(i -> {
                            log.info("[Planned] Not backed up, delete '{}' (count {}/{})", videoIdToDelete, i + 1, apiDuplicatedCount);
                            List<PlannedOutboxDto> actions = List.of(new PlannedOutboxDto(userId, playlistId, accessToken, Outbox.ActionType.DELETE, videoIdToDelete, playlistItemIdsToDelete.get(i)));
                            recoveryTasks.add(new RecoveryTaskDto(RecoveryTaskDto.TaskType.DELETE_ONLY, null, null, actions));
                        });
                continue;
            }

            MusicDetailsDto backupMusic = backupMusicListFromDb.get(0);
            Optional<ActionLog> recentLogOpt = actionLogService.findTodayRecoverLog(ActionLog.ActionType.RECOVER, backupMusic.videoId());

            Video replacementVideo = recentLogOpt
                    .map(recent -> {
                        log.info("[Reuse Replacement Video]: {}", recent.getSourceVideoId());
                        if (!quotaService.checkAndConsumeLua(userId, QuotaType.SINGLE_SEARCH.getCost())) throw new QuotaExceededException("prepareRecoveryPlan - Reuse");
                        return youtubeApiClient.fetchSingleVideo(recent.getSourceVideoId());
                    })
                    .filter(video -> {
                        boolean playable = youtubeApiClient.isVideoPlayable(video, countryCode);
                        if (!playable) {
                            log.info("[Reuse Failed] Candidate {} is not playable in {}. Fallback to full search.", video.getId(), countryCode);
                        }
                        return playable;
                    })
                    .orElseGet(() -> {
                        if (!quotaService.checkAndConsumeLua(userId, QuotaType.VIDEO_SEARCH.getCost() + QuotaType.SINGLE_SEARCH.getCost())) throw new QuotaExceededException("prepareRecoveryPlan - Search");
                        return musicService.searchVideoToReplace(backupMusic, countryCode);
                    });

            Music replacementMusic = musicConverterHelper.makeVideoToMusic(replacementVideo, playlist);

            // 복구 횟수만큼 추가 & 삭제 (1:1 매칭 가능한 만큼 복구)
            IntStream.range(0, Math.min(backupMusicListFromDb.size(), apiDuplicatedCount))
                    .forEach(i -> {
                        long pk = backupMusicListFromDb.get(i).id();
                        PlannedReplacementDto swapInfo = new PlannedReplacementDto(pk, replacementMusic, backupMusic);
                        List<PlannedOutboxDto> actions = List.of(
                                new PlannedOutboxDto(userId, playlistId, accessToken, Outbox.ActionType.ADD, replacementMusic.getVideoId(), null),
                                new PlannedOutboxDto(userId, playlistId, accessToken, Outbox.ActionType.DELETE, videoIdToDelete, playlistItemIdsToDelete.get(i))
                        );
                        recoveryTasks.add(new RecoveryTaskDto(RecoveryTaskDto.TaskType.RECOVERY, swapInfo, null, actions));
                        log.info("[Planned] Recover ({} -> {}) at pos {}", videoIdToDelete, replacementMusic.getVideoId(), pk);
                    });

            // Edge Case 1: DB(4) > API(2)
            if (backupMusicListFromDb.size() > apiDuplicatedCount) {
                backupMusicListFromDb.subList(apiDuplicatedCount, backupMusicListFromDb.size())
                        .forEach(m -> {
                            redundantEdgeDeleteIds.add(m.id());
                            log.info("[Planned] Delete extra duplicated video from DB : {}", m.id());
                        });
            }

            // Edge Case 2:  DB(2) < API(4)
            if (backupMusicListFromDb.size() < apiDuplicatedCount) {
                IntStream.range(backupMusicListFromDb.size(), apiDuplicatedCount)
                        .forEach(i -> {
                            List<PlannedOutboxDto> actions = List.of(
                                    new PlannedOutboxDto(userId, playlistId, accessToken, Outbox.ActionType.ADD, replacementMusic.getVideoId(), null),
                                    new PlannedOutboxDto(userId, playlistId, accessToken, Outbox.ActionType.DELETE, videoIdToDelete, playlistItemIdsToDelete.get(i))
                            );
                            recoveryTasks.add(new RecoveryTaskDto(RecoveryTaskDto.TaskType.EXTRA_RECOVERY, null, replacementMusic, actions));
                            log.info("[Planned] Add extra duplicated video to DB and Playlist: [{}]", replacementMusic.getVideoId());
                        });
            }

        }

        return new PlaylistRecoveryPlanDto(plannedPlaylistUpdateDto.toInsertVideos(), plannedPlaylistUpdateDto.toDeleteVideoIds(), redundantEdgeDeleteIds, recoveryTasks);
    }
}

