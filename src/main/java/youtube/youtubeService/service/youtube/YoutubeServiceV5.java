package youtube.youtubeService.service.youtube;

import com.google.api.services.youtube.model.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import youtube.youtubeService.api.YoutubeApiClient;
import youtube.youtubeService.domain.*;
import youtube.youtubeService.domain.enums.QuotaType;
import youtube.youtubeService.dto.*;
import youtube.youtubeService.exception.QuotaExceededException;
import youtube.youtubeService.service.ActionLogService;
import youtube.youtubeService.service.QuotaService;
import youtube.youtubeService.service.musics.MusicService;
import youtube.youtubeService.service.outbox.OutboxService;
import youtube.youtubeService.service.playlists.PlaylistService;
import java.io.IOException;
import java.util.*;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class YoutubeServiceV5 implements YoutubeService {

    private final ActionLogService actionLogService;
    private final YoutubeApiClient youtubeApiClient;
    private final PlaylistService playlistService;
    private final MusicService musicService;
    private final OutboxService outboxService;
    private final QuotaService quotaService;

    @Override
    public void fileTrackAndRecover(String userId, Playlists playlist, String countryCode, String accessToken, List<Music> preFetchedMusicList) {

        Map<String, List<String>> illegalVideosInfo;
        String playlistId = playlist.getPlaylistId();
        long reservedQuotaBackedUp = 0L;

        try {
            illegalVideosInfo = playlistService.updatePlaylist(userId, countryCode, playlist, preFetchedMusicList);
        } catch (IOException e) {
            log.info("[skip this playlist: {}]", playlistId);
            return;
        }

        if (illegalVideosInfo.isEmpty()) {
            log.info("[nothing to recover]");
            return;
        }
        // 2. 각 비정상 음악 처리
        for (Map.Entry<String, List<String>> entry : illegalVideosInfo.entrySet()) {
            String videoIdToDelete = entry.getKey();
            List<String> playlistItemIdsToDelete = entry.getValue();
            int apiDuplicatedCount = playlistItemIdsToDelete.size();
            //  3. DB 에서 해당 videoId + playlistId 를 가진 모든 음악 조회
            List<Music> backupMusicListFromDb = musicService.getMusicListFromDBThruMusicId(videoIdToDelete, playlistId);

            if (backupMusicListFromDb.isEmpty()) {
                // 백업이 없으면 그냥 중복 개수만큼 삭제
                for (int i = 0; i < apiDuplicatedCount; i++) {
                    log.info("Not backed up - delete '{}' (count {}/{})", videoIdToDelete, i + 1, apiDuplicatedCount);
                    reservedQuotaBackedUp = consumeWithOutbox(userId, playlistId, accessToken, reservedQuotaBackedUp,
                            QuotaType.VIDEO_DELETE.getCost(), Outbox.ActionType.DELETE, videoIdToDelete, playlistItemIdsToDelete.get(i));
                }
                continue;
            }

            Music backupMusic = backupMusicListFromDb.get(0); // = videoIdToDelete
            Optional<ActionLog> recentLogOpt = actionLogService.findTodayRecoverLog(ActionLog.ActionType.RECOVER, backupMusic.getVideoId());
            Music replacementMusic;
            Video replacementVideo;

            if (recentLogOpt.isPresent()) {
                log.info("[Reuse Replacement Video]: {}", recentLogOpt.get().getSourceVideoId());
                if(!quotaService.checkAndConsumeLua(userId, QuotaType.SINGLE_SEARCH.getCost())) throw new QuotaExceededException("Quota Exceed");
                replacementVideo = youtubeApiClient.fetchSingleVideo(recentLogOpt.get().getSourceVideoId());
            } else {
                if(!quotaService.checkAndConsumeLua(userId, QuotaType.VIDEO_SEARCH.getCost() + QuotaType.SINGLE_SEARCH.getCost())) throw new QuotaExceededException("Quota Exceed");
                replacementVideo = musicService.searchVideoToReplace(backupMusic, playlist);
            }

            replacementMusic = musicService.makeVideoToMusic(replacementVideo, playlist);
            actionLogService.actionLogSave(userId, playlistId, ActionLog.ActionType.RECOVER, backupMusic, replacementMusic);

            // 복구 횟수만큼 추가 & 삭제 (1:1 매칭 가능한 만큼 복구)
            for (int i = 0; i < Math.min(backupMusicListFromDb.size(), apiDuplicatedCount); i++) {
                long pk = backupMusicListFromDb.get(i).getId();
                // DB 교체 처리
                musicService.updateMusicWithReplacement(videoIdToDelete, replacementMusic, pk);

                reservedQuotaBackedUp = consumeWithOutbox(userId, playlistId, accessToken, reservedQuotaBackedUp,
                        QuotaType.VIDEO_INSERT.getCost(), Outbox.ActionType.ADD, replacementMusic.getVideoId(), null);

                reservedQuotaBackedUp = consumeWithOutbox(userId, playlistId, accessToken, reservedQuotaBackedUp,
                        QuotaType.VIDEO_DELETE.getCost(), Outbox.ActionType.DELETE, videoIdToDelete, playlistItemIdsToDelete.get(i));

                log.info("Recovered [{}] -> [{}] at pos {}", videoIdToDelete, replacementMusic.getVideoId(), pk);
            }
            // 엣지케이스 1
            if (backupMusicListFromDb.size() > apiDuplicatedCount) { // 4(DB) > 2(API)
                for (int i = apiDuplicatedCount; i < backupMusicListFromDb.size(); i++) {
                    long rowPk = backupMusicListFromDb.get(i).getId();
                    musicService.deleteById(rowPk);
                    log.info("Delete extra duplicated video on DB : {}", rowPk);
                }
            }
            // 엣지케이스 2
            if(backupMusicListFromDb.size() < apiDuplicatedCount) { // 2(DB) < 4(API)
                for (int i = backupMusicListFromDb.size(); i < apiDuplicatedCount; i++) { // 남은 횟수만큼 더 업데이트 해줘야한다.
                    musicService.upsertMusic(replacementMusic);

                    reservedQuotaBackedUp = consumeWithOutbox(userId, playlistId, accessToken, reservedQuotaBackedUp,
                            QuotaType.VIDEO_INSERT.getCost(), Outbox.ActionType.ADD, replacementMusic.getVideoId(), null);

                    reservedQuotaBackedUp = consumeWithOutbox(userId, playlistId, accessToken, reservedQuotaBackedUp,
                            QuotaType.VIDEO_DELETE.getCost(), Outbox.ActionType.DELETE, videoIdToDelete, playlistItemIdsToDelete.get(i));

                    log.info("Add extra duplicated video on DB : [{}]", replacementMusic.getVideoId());
                    log.info("Add extra replacement video on the Playlist : [{}]", replacementMusic.getVideoId());
                }
            }

        }

    }

    /**
     * outbox 넣기전에 할당량 소비 예측 후 언제든 초과되면 런타임 예외 던져야함 (플리 단위로 롤백이니)
     * 그리고 여기서 롤백 시 할당량 재지급도 해줘야함 카운트 고려해서
     */
    private long consumeWithOutbox(String userId, String playlistId, String accessToken, long reservedQuotaBackedUp,
                                   long cost, Outbox.ActionType actionType, String videoId, String playlistItemIdToDelete) {

        if(!quotaService.checkAndConsumeLua(userId, cost)) {
            quotaService.rollbackQuota(userId, reservedQuotaBackedUp);
            throw new QuotaExceededException("Quota Exceed");
        } else {
            reservedQuotaBackedUp += cost;
            outboxService.outboxInsert(actionType, accessToken, userId, playlistId, videoId, playlistItemIdToDelete);
        }
        // log.info("[Quota Reserve]: {}, [Reserved So Far]: {}", cost, reservedQuotaBackedUp);
        return reservedQuotaBackedUp;
    }
}


/** OG CODE BEFORE 1104
     @Slf4j
     @Service
     @Transactional
     @RequiredArgsConstructor
     public class YoutubeServiceV5 implements YoutubeService {

     private final ActionLogService actionLogService;
     private final YoutubeApiClient youtubeApiClient;
     private final PlaylistService playlistService;
     private final MusicService musicService;
     private final OutboxService outboxService;
     private final QuotaService quotaService;

     //    public YoutubeServiceV5(ActionLogService actionLogService, YoutubeApiClient youtubeApiClient,
     //                            PlaylistService playlistService, MusicService musicService, OutboxService outboxService, QuotaService quotaService) {
     //        this.actionLogService = actionLogService;
     //        this.youtubeApiClient = youtubeApiClient;
     //        this.playlistService = playlistService;
     //        this.musicService = musicService;
     //        this.outboxService = outboxService;
     //        this.quotaService = quotaService;
     //    }

     @Override
     public void fileTrackAndRecover(String userId, Playlists playlist, String countryCode, String accessToken) {
     //        long startTime = System.nanoTime();
     // 1. 업데이트 및 비정상 음악 목록 가져오기
     Map<String, List<String>> illegalVideosInfo;
     String playlistId = playlist.getPlaylistId();
     long reservedQuotaBackedUp = 0L;

     try {
     illegalVideosInfo = playlistService.updatePlaylist(userId, countryCode, playlist);
     } catch (IOException e) {
     log.info("[skip this playlist: {}]", playlistId);
     return;
     }

     if (illegalVideosInfo.isEmpty()) {
     log.info("[nothing to recover]");
     return;
     }
     // 2. 각 비정상 음악 처리
     for (Map.Entry<String, List<String>> entry : illegalVideosInfo.entrySet()) {
     String videoIdToDelete = entry.getKey();
     List<String> playlistItemIdsToDelete = entry.getValue();
     int apiDuplicatedCount = playlistItemIdsToDelete.size();
     //  3. DB 에서 해당 videoId + playlistId 를 가진 모든 음악 조회
     List<Music> backupMusicListFromDb = musicService.getMusicListFromDBThruMusicId(videoIdToDelete, playlistId);

     if (backupMusicListFromDb.isEmpty()) {
     // 백업이 없으면 그냥 중복 개수만큼 삭제
     for (int i = 0; i < apiDuplicatedCount; i++) {
     log.info("Not backed up - delete '{}' (count {}/{})", videoIdToDelete, i + 1, apiDuplicatedCount);
     reservedQuotaBackedUp = consumeWithOutbox(userId, playlistId, accessToken, reservedQuotaBackedUp,
     QuotaType.VIDEO_DELETE.getCost(), Outbox.ActionType.DELETE, videoIdToDelete, playlistItemIdsToDelete.get(i));
     }
     continue;
     }

     Music backupMusic = backupMusicListFromDb.get(0); // = videoIdToDelete
     Optional<ActionLog> recentLogOpt = actionLogService.findTodayRecoverLog(ActionLog.ActionType.RECOVER, backupMusic.getVideoId());
     Music replacementMusic;
     Video replacementVideo;

     if (recentLogOpt.isPresent()) {
     log.info("[Reuse Replacement Video]: {}", recentLogOpt.get().getSourceVideoId());
     if(!quotaService.checkAndConsumeLua(userId, QuotaType.SINGLE_SEARCH.getCost())) throw new QuotaExceededException("Quota Exceed");
     replacementVideo = youtubeApiClient.fetchSingleVideo(recentLogOpt.get().getSourceVideoId());
     } else {
     if(!quotaService.checkAndConsumeLua(userId, QuotaType.VIDEO_SEARCH.getCost() + QuotaType.SINGLE_SEARCH.getCost())) throw new QuotaExceededException("Quota Exceed");
     replacementVideo = musicService.searchVideoToReplace(backupMusic, playlist);
     }

     replacementMusic = musicService.makeVideoToMusic(replacementVideo, playlist);
     actionLogService.actionLogSave(userId, playlistId, ActionLog.ActionType.RECOVER, backupMusic, replacementMusic);

     // 복구 횟수만큼 추가 & 삭제
     for (int i = 0; i < Math.min(backupMusicListFromDb.size(), apiDuplicatedCount); i++) { // 1:1 매칭 가능한 만큼 복구
     long pk = backupMusicListFromDb.get(i).getId();
     // DB 교체 처리
     musicService.updateMusicWithReplacement(videoIdToDelete, replacementMusic, pk);

     reservedQuotaBackedUp = consumeWithOutbox(userId, playlistId, accessToken, reservedQuotaBackedUp,
     QuotaType.VIDEO_INSERT.getCost(), Outbox.ActionType.ADD, replacementMusic.getVideoId(), null);

     reservedQuotaBackedUp = consumeWithOutbox(userId, playlistId, accessToken, reservedQuotaBackedUp,
     QuotaType.VIDEO_DELETE.getCost(), Outbox.ActionType.DELETE, videoIdToDelete, playlistItemIdsToDelete.get(i));

     log.info("Recovered [{}] -> [{}] at pos {}", videoIdToDelete, replacementMusic.getVideoId(), pk);
     }
     // 엣지케이스 1
     if (backupMusicListFromDb.size() > apiDuplicatedCount) { // 4(DB) > 2(API)
     for (int i = apiDuplicatedCount; i < backupMusicListFromDb.size(); i++) {
     long rowPk = backupMusicListFromDb.get(i).getId();
     musicService.deleteById(rowPk);
     log.info("Delete extra duplicated video on DB : {}", rowPk);
     }
     }
     // 엣지케이스 2
     if(backupMusicListFromDb.size() < apiDuplicatedCount) { // 2(DB) < 4(API)
     for (int i = backupMusicListFromDb.size(); i < apiDuplicatedCount; i++) { // 남은 횟수만큼 더 업데이트 해줘야한다.
     musicService.upsertMusic(replacementMusic);

     reservedQuotaBackedUp = consumeWithOutbox(userId, playlistId, accessToken, reservedQuotaBackedUp,
     QuotaType.VIDEO_INSERT.getCost(), Outbox.ActionType.ADD, replacementMusic.getVideoId(), null);

     reservedQuotaBackedUp = consumeWithOutbox(userId, playlistId, accessToken, reservedQuotaBackedUp,
     QuotaType.VIDEO_DELETE.getCost(), Outbox.ActionType.DELETE, videoIdToDelete, playlistItemIdsToDelete.get(i));

     log.info("Add extra duplicated video on DB : [{}]", replacementMusic.getVideoId());
     log.info("Add extra replacement video on the Playlist : [{}]", replacementMusic.getVideoId());
     }
     }

     // if(userId != null) throw new RuntimeException("Intentioned Runtime Exception"); // 고의적 예외 던짐
     }

     //        long endTime = System.nanoTime(); // 측정 종료
     //        long elapsedMs = (endTime - startTime) / 1_000_000;
     //        log.info("[fileTrackAndRecover transaction time] {} ms", elapsedMs);
     }


    private long consumeWithOutbox(String userId, String playlistId, String accessToken, long reservedQuotaBackedUp,
                                   long cost, Outbox.ActionType actionType, String videoId, String playlistItemIdToDelete) {
    //        for legacy test
    //        try {
    //            if (actionType.equals(Outbox.ActionType.DELETE)) {
    //                log.info("API DELETE : {}", videoId);
    //                youtubeApiClient.deleteFromActualPlaylist(accessToken, playlistItemIdToDelete);
    //            } else if (actionType.equals(Outbox.ActionType.ADD)) {
    //                log.info("API ADD : {}", videoId);
    //                youtubeApiClient.addVideoToActualPlaylist(accessToken, playlistId, videoId);
    //            }
    //        } catch (Exception e) {
    //            log.warn("API call failed for Actual API: {} - {}", videoId, e.getMessage());
    //        }

        if(!quotaService.checkAndConsumeLua(userId, cost)) {
            quotaService.rollbackQuota(userId, reservedQuotaBackedUp);
            throw new QuotaExceededException("Quota Exceed");
        } else {
            reservedQuotaBackedUp += cost;
            outboxService.outboxInsert(actionType, accessToken, userId, playlistId, videoId, playlistItemIdToDelete);
        }
        // log.info("[Quota Reserve]: {}, [Reserved So Far]: {}", cost, reservedQuotaBackedUp);
        return reservedQuotaBackedUp;
    }
}

 */


/** OGCODE BEFORE 1024

 @Slf4j
 @Service
 @Transactional
 public class YoutubeServiceV5 implements YoutubeService {

 private final ActionLogService actionLogService;
 private final YoutubeApiClient youtubeApiClient;
 private final PlaylistService playlistService;
 private final MusicService musicService;
 private final OutboxService outboxService;
 private final QuotaService quotaService;

 public YoutubeServiceV5(ActionLogService actionLogService, YoutubeApiClient youtubeApiClient,
 PlaylistService playlistService, MusicService musicService, OutboxService outboxService, QuotaService quotaService) {
 this.actionLogService = actionLogService;
 this.youtubeApiClient = youtubeApiClient;
 this.playlistService = playlistService;
 this.musicService = musicService;
 this.outboxService = outboxService;
 this.quotaService = quotaService;
 }

 @Override
 public void fileTrackAndRecover(String userId, Playlists playlist, String countryCode, String accessToken) {
 // 1. 업데이트 및 비정상 음악 목록 가져오기
 Map<String, Integer> illegalVideoIdWithCounts;
 String playlistId = playlist.getPlaylistId();
 long pagination, reservedQuotaBackedUp = 0L;

 try {
 IllegalVideosAndPaginationDto updatePlaylistResult = playlistService.updatePlaylist(userId, countryCode, playlist);
 illegalVideoIdWithCounts = updatePlaylistResult.getIllegalVideoIdWithCounts();
 pagination = updatePlaylistResult.getPagination();
 } catch (IOException e) {
 log.info("[skip this playlist: {}]", playlistId);
 return;
 }

 if (illegalVideoIdWithCounts.isEmpty()) {
 log.info("[nothing to recover]");
 return;
 }
 // 2. 각 비정상 음악 처리
 for (Map.Entry<String, Integer> entry : illegalVideoIdWithCounts.entrySet()) {
 String videoIdToDelete = entry.getKey(); // XzEoBAltBII
 int apiDuplicatedCount = entry.getValue(); // 1~2
 //  3. DB 에서 해당 videoId + playlistId 를 가진 모든 음악 조회
 List<Music> backupMusicListFromDb = musicService.getMusicListFromDBThruMusicId(videoIdToDelete, playlistId);

 if (backupMusicListFromDb.isEmpty()) {
 // 백업이 없으면 그냥 중복 개수만큼 삭제
 for (int i = 0; i < apiDuplicatedCount; i++) {
 log.info("Not backed up - delete '{}' (count {}/{})", videoIdToDelete, i + 1, apiDuplicatedCount);
 reservedQuotaBackedUp = consumeWithOutbox(userId, playlistId, accessToken, reservedQuotaBackedUp,
 QuotaType.VIDEO_DELETE.getCost(), Outbox.ActionType.DELETE, videoIdToDelete);
 }
 continue;
 }

 Music backupMusic = backupMusicListFromDb.get(0); // = videoIdToDelete
 Optional<ActionLog> recentLogOpt = actionLogService.findTodayRecoverLog(ActionLog.ActionType.RECOVER, backupMusic.getVideoId());
 Music replacementMusic;
 Video replacementVideo;

 if (recentLogOpt.isPresent()) {
 log.info("[Reuse Replacement Video]: {}", recentLogOpt.get().getSourceVideoId());
 if(!quotaService.checkAndConsumeLua(userId, QuotaType.SINGLE_SEARCH.getCost())) throw new QuotaExceededException("Quota Exceed");
 replacementVideo = youtubeApiClient.fetchSingleVideo(recentLogOpt.get().getSourceVideoId());
 } else {
 if(!quotaService.checkAndConsumeLua(userId, QuotaType.VIDEO_SEARCH.getCost() + QuotaType.SINGLE_SEARCH.getCost())) throw new QuotaExceededException("Quota Exceed");
 replacementVideo = musicService.searchVideoToReplace(backupMusic, playlist);
 }

 replacementMusic = musicService.makeVideoToMusic(replacementVideo, playlist);
 actionLogService.actionLogSave(userId, playlistId, ActionLog.ActionType.RECOVER, backupMusic, replacementMusic);

 // 복구 횟수만큼 추가 & 삭제
 for (int i = 0; i < Math.min(backupMusicListFromDb.size(), apiDuplicatedCount); i++) { // 1:1 매칭 가능한 만큼 복구
 long pk = backupMusicListFromDb.get(i).getId();
 // DB 교체 처리
 musicService.dBTrackAndRecoverPosition(videoIdToDelete, replacementMusic, pk);

 reservedQuotaBackedUp = consumeWithOutbox(userId, playlistId, accessToken, reservedQuotaBackedUp,
 QuotaType.VIDEO_INSERT.getCost(), Outbox.ActionType.ADD, replacementMusic.getVideoId());

 reservedQuotaBackedUp = consumeWithOutbox(userId, playlistId, accessToken, reservedQuotaBackedUp,
 QuotaType.VIDEO_DELETE.getCost() + pagination, Outbox.ActionType.DELETE, videoIdToDelete); // 여기에만 pagination 만큼 더해주면됨

 log.info("Recovered [{}] -> [{}] at pos {}", videoIdToDelete, replacementMusic.getVideoId(), pk);
 }
 // 엣지케이스 1
 if (backupMusicListFromDb.size() > apiDuplicatedCount) { // 4(DB) > 2(API)
 for (int i = apiDuplicatedCount; i < backupMusicListFromDb.size(); i++) {
 long rowPk = backupMusicListFromDb.get(i).getId();
 musicService.deleteById(rowPk);
 log.info("Delete extra duplicated video on DB : {}", rowPk);
 }
 }
 // 엣지케이스 2
 if(backupMusicListFromDb.size() < apiDuplicatedCount) { // 2(DB) < 4(API)
 for (int i = backupMusicListFromDb.size(); i < apiDuplicatedCount; i++) { // 남은 횟수만큼 더 업데이트 해줘야한다.
 musicService.upsertMusic(replacementMusic);

 reservedQuotaBackedUp = consumeWithOutbox(userId, playlistId, accessToken, reservedQuotaBackedUp,
 QuotaType.VIDEO_INSERT.getCost(), Outbox.ActionType.ADD, replacementMusic.getVideoId());

 reservedQuotaBackedUp = consumeWithOutbox(userId, playlistId, accessToken, reservedQuotaBackedUp,
 QuotaType.VIDEO_DELETE.getCost(), Outbox.ActionType.DELETE, videoIdToDelete);

 log.info("Add extra duplicated video on DB : [{}]", replacementMusic.getVideoId());
 log.info("Add extra replacement video on the Playlist : [{}]", replacementMusic.getVideoId());
 }
 }

 // if(userId != null) throw new RuntimeException("Intentioned Runtime Exception"); // 고의적 예외 던짐
 }
 }

private long consumeWithOutbox(String userId, String playlistId, String accessToken, long reservedQuotaBackedUp,
                               long cost, Outbox.ActionType actionType, String videoId) {

    if(!quotaService.checkAndConsumeLua(userId, cost)) {
        quotaService.rollbackQuota(userId, reservedQuotaBackedUp);
        throw new QuotaExceededException("Quota Exceed");
    } else {
        reservedQuotaBackedUp += cost;
        outboxService.outboxInsert(actionType, accessToken, userId, playlistId, videoId);
    }
    log.info("[Quota Reserve]: {}, [Reserved So Far]: {}", cost, reservedQuotaBackedUp);
    return reservedQuotaBackedUp;
}
}

 */