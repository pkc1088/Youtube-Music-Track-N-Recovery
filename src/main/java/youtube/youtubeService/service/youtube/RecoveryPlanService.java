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
import youtube.youtubeService.exception.NoPlaylistFoundException;
import youtube.youtubeService.exception.QuotaExceededException;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class RecoveryPlanService {

    private final ActionLogService actionLogService;
    private final YoutubeApiClient youtubeApiClient;
    private final PlaylistStateCheckService playlistStateCheckService;
    private final MusicService musicService;
    private final QuotaService quotaService;
    private final MusicConverterHelper musicConverterHelper;

    public PlaylistRecoveryPlanDto prepareRecoveryPlan(String userId, Playlists playlist, String countryCode, String accessToken, List<MusicSummaryDto> preFetchedMusicList) throws IOException {

        String playlistId = playlist.getPlaylistId();
        PlannedPlaylistUpdateDto plannedPlaylistUpdateDto;

        try {
            plannedPlaylistUpdateDto = playlistStateCheckService.compareApiAndDbState(userId, countryCode, playlist, preFetchedMusicList);
        } catch (NoPlaylistFoundException npe) {
            throw npe;
        } catch (IOException e) {
            log.info("[skip this playlist: {}]", playlistId);
            throw e;
        }

        List<PlannedOutboxDto> plannedOutboxList = new ArrayList<>();
        List<PlannedReplacementDto> plannedReplacementDto = new ArrayList<>();
        List<Music> edgeInsert = new ArrayList<>();
        List<Long> edgeDelete = new ArrayList<>();


        for (Map.Entry<String, List<String>> entry : plannedPlaylistUpdateDto.illegalVideos().entrySet()) {
            String videoIdToDelete = entry.getKey();
            List<String> playlistItemIdsToDelete = entry.getValue();
            int apiDuplicatedCount = playlistItemIdsToDelete.size();
            //  DB 에서 해당 videoId + playlistId 를 가진 모든 음악 조회
//            List<Music> backupMusicListFromDb = musicService.getMusicListFromDBThruMusicId(videoIdToDelete, playlistId);
            List<MusicDetailsDto> backupMusicListFromDb = musicService.getMusicListFromDBThruMusicId(videoIdToDelete, playlistId);

            if (backupMusicListFromDb.isEmpty()) {
                // 백업이 없으면 그냥 중복 개수만큼 삭제
                for (int i = 0; i < apiDuplicatedCount; i++) {
                    log.info("[Planned] Not backed up - delete '{}' (count {}/{})", videoIdToDelete, i + 1, apiDuplicatedCount);
                    plannedOutboxList.add(new PlannedOutboxDto(userId, playlistId, accessToken, Outbox.ActionType.DELETE, videoIdToDelete, playlistItemIdsToDelete.get(i)));
                }
                continue;
            }

//            Music backupMusic = backupMusicListFromDb.get(0);
            MusicDetailsDto backupMusic = backupMusicListFromDb.get(0);
//            StopWatch transactionWatch = new StopWatch(); transactionWatch.start();
            Optional<ActionLog> recentLogOpt = actionLogService.findTodayRecoverLog(ActionLog.ActionType.RECOVER, backupMusic.videoId());
//            transactionWatch.stop(); log.info("[Test] findTodayRecoverLog Time: {} ms", transactionWatch.getTotalTimeMillis());
            Music replacementMusic;
            Video replacementVideo;

            if (recentLogOpt.isPresent()) {
                log.info("[Reuse Replacement Video]: {}", recentLogOpt.get().getSourceVideoId());
                if(!quotaService.checkAndConsumeLua(userId, QuotaType.SINGLE_SEARCH.getCost())) throw new QuotaExceededException("Quota Exceed");
                replacementVideo = youtubeApiClient.fetchSingleVideo(recentLogOpt.get().getSourceVideoId());
            } else {
                if(!quotaService.checkAndConsumeLua(userId, QuotaType.VIDEO_SEARCH.getCost() + QuotaType.SINGLE_SEARCH.getCost())) throw new QuotaExceededException("Quota Exceed");
                replacementVideo = musicService.searchVideoToReplace(backupMusic);
            }

            replacementMusic = musicConverterHelper.makeVideoToMusic(replacementVideo, playlist);

            // 복구 횟수만큼 추가 & 삭제 (1:1 매칭 가능한 만큼 복구)
            for (int i = 0; i < Math.min(backupMusicListFromDb.size(), apiDuplicatedCount); i++) {
                long pk = backupMusicListFromDb.get(i).id();
                // DB 교체 처리
                plannedReplacementDto.add(new PlannedReplacementDto(pk, replacementMusic, backupMusic));

                plannedOutboxList.add(new PlannedOutboxDto(userId, playlistId, accessToken, Outbox.ActionType.ADD, replacementMusic.getVideoId(), null));
                plannedOutboxList.add(new PlannedOutboxDto(userId, playlistId, accessToken, Outbox.ActionType.DELETE, videoIdToDelete, playlistItemIdsToDelete.get(i)));

                log.info("[Planned] Recover [{}] -> [{}] at pos {}", videoIdToDelete, replacementMusic.getVideoId(), pk);
            }
            // 엣지케이스 1
            if (backupMusicListFromDb.size() > apiDuplicatedCount) { // 4(DB) > 2(API)
                for (int i = apiDuplicatedCount; i < backupMusicListFromDb.size(); i++) {
                    long rowPk = backupMusicListFromDb.get(i).id();
                    edgeDelete.add(rowPk);
                    log.info("[Planned] Delete extra duplicated video on DB : {}", rowPk);
                }
            }
            // 엣지케이스 2
            if(backupMusicListFromDb.size() < apiDuplicatedCount) { // 2(DB) < 4(API)
                for (int i = backupMusicListFromDb.size(); i < apiDuplicatedCount; i++) { // 남은 횟수만큼 더 업데이트 해줘야한다.
                    edgeInsert.add(replacementMusic);
                    plannedOutboxList.add(new PlannedOutboxDto(userId, playlistId, accessToken, Outbox.ActionType.ADD, replacementMusic.getVideoId(), null));
                    plannedOutboxList.add(new PlannedOutboxDto(userId, playlistId, accessToken, Outbox.ActionType.DELETE, videoIdToDelete, playlistItemIdsToDelete.get(i)));
                    log.info("[Planned] Add extra duplicated video on DB : [{}]", replacementMusic.getVideoId());
                    log.info("[Planned] Add extra replacement video on the Playlist : [{}]", replacementMusic.getVideoId());
                }
            }

        }


        return new PlaylistRecoveryPlanDto(plannedPlaylistUpdateDto.videosToInsert(), plannedPlaylistUpdateDto.videosToDelete(), plannedOutboxList, plannedReplacementDto, edgeInsert, edgeDelete);
    }
}
