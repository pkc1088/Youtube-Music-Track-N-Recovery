package youtube.youtubeService.service.youtube;

import com.google.api.services.youtube.model.*;
import jakarta.transaction.Transactional;
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

    /**
     * outbox 넣기전에 할당량 소비 예측 후 언제든 초과되면 런타임 예외 던져야함 (플리 단위로 롤백이니)
     * 그리고 여기서 롤백 시 할당량 재지급도 해줘야함 카운트 고려해서
     */
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

/** OGCODE BEFORE 0903
 @Slf4j
 @Service
 @Transactional
 public class YoutubeServiceV5 implements YoutubeService {

 private final ActionLogService actionLogService;
 private final YoutubeApiClient youtubeApiClient;
 private final PlaylistService playlistService;
 private final MusicService musicService;
 private final OutboxService outboxService;

 public YoutubeServiceV5(ActionLogService actionLogService, YoutubeApiClient youtubeApiClient,
 PlaylistService playlistService, MusicService musicService, OutboxService outboxService) {
 this.actionLogService = actionLogService;
 this.youtubeApiClient = youtubeApiClient;
 this.playlistService = playlistService;
 this.musicService = musicService;
 this.outboxService = outboxService;
 }


 public Map<String, Integer> updatePlaylist(String userId, String countryCode, Playlists playlist) throws IOException {
 String playlistId = playlist.getPlaylistId();
 log.info("[update playlist start: {}]", playlistId);

 // 1. API 검색으로 고객 플레이리스트 목록 불러오기
 List<PlaylistItem> pureApiPlaylistItems;
 try {
 pureApiPlaylistItems = youtubeApiClient.getPlaylistItemListResponse(playlistId, 50L);
 } catch (IOException e) {
 playlistService.removePlaylistsFromDB(userId, Collections.singletonList(playlistId));
 log.info("This playlist has been removed by the owner({})", playlistId);
 throw new IOException(e);
 }

 // 2. 고객 플레이리스트 아이템 담긴 디비 불러오기
 List<Music> pureDbMusicList = musicService.findAllMusicByPlaylistId(playlistId);//musicRepository.findAllMusicByPlaylistId(playlistId);
 // 3. API 에서 video 상태 조회
 List<String> pureApiVideoIds = pureApiPlaylistItems.stream().map(item -> item.getSnippet().getResourceId().getVideoId()).toList();

 VideoFilterResult videoFilterResult = youtubeApiClient.safeFetchVideos(pureApiVideoIds, countryCode);
 // 4-1. 정상 비디오
 List<Video> legalVideos = videoFilterResult.getLegalVideos();
 List<String> legalVideoIds = legalVideos.stream().map(Video::getId).toList();
 // 4-2. unlisted, 국가차단 비디오
 List<String> unlistedCountryVideoIds = videoFilterResult.getUnlistedCountryVideos().stream().map(Video::getId).toList();
 // 4-3. Delete / Private 비디오 (응답 자체가 안 온 videoId)
 List<String> privateDeletedVideoIds = pureApiVideoIds.stream().filter(videoId -> !legalVideoIds.contains(videoId) && !unlistedCountryVideoIds.contains(videoId)).toList();

 log.info("[legal] videos count : {}", legalVideoIds.size());
 log.info("[unlisted/country] video count : {}", unlistedCountryVideoIds.size());
 log.info("[deleted/private] video count : {}", privateDeletedVideoIds.size());

 // 5. 둘의 차이를 비교 → DB 반영
 Map<String, Long> apiCounts = pureApiVideoIds.stream().collect(Collectors.groupingBy(v -> v, Collectors.counting()));
 Map<String, Long> dbCounts = pureDbMusicList.stream().collect(Collectors.groupingBy(Music::getVideoId, Collectors.counting()));

 Set<String> allVideoIds = new HashSet<>(); // 영상 개수가 중요하진 x, 둘을 모두 순회하기 위해 담는 것일 뿐임
 allVideoIds.addAll(apiCounts.keySet());
 allVideoIds.addAll(dbCounts.keySet());

 for (String videoId : allVideoIds) {
 // "vid_A": 2L
 long apiCount = apiCounts.getOrDefault(videoId, 0L); //2
 long dbCount = dbCounts.getOrDefault(videoId, 0L); // 1

 long toInsertCount = apiCount - dbCount; // 1
 long toDeleteCount = dbCount - apiCount; // -1

 if (toInsertCount > 0 && legalVideoIds.contains(videoId)) { // toInsertCount 만큼 DBAddAction 을 반복
 legalVideos.stream().filter(v -> v.getId().equals(videoId)).findFirst()
 .ifPresent(video -> IntStream.range(0, (int) toInsertCount).forEach(i -> musicService.DBAddAction(video, playlist)));
            }

                    if (toDeleteCount > 0 && !unlistedCountryVideoIds.contains(videoId) && !privateDeletedVideoIds.contains(videoId)) { // 삭제할 개수만큼만 제한 후 Music 객체에서 ID만 추출 각 ID를 사용하여 삭제
                    pureDbMusicList.stream().filter(m -> m.getVideoId().equals(videoId)).limit(toDeleteCount)
                    .map(Music::getId).forEach(musicService::deleteById);
                    }
                    }

                    Map<String, Integer> illegalVideoCounts = new HashMap<>();

        for (PlaylistItem item : pureApiPlaylistItems) {
        String videoId = item.getSnippet().getResourceId().getVideoId();
        if (unlistedCountryVideoIds.contains(videoId) || privateDeletedVideoIds.contains(videoId)) {
        illegalVideoCounts.put(videoId, illegalVideoCounts.getOrDefault(videoId, 0) + 1);
        }
        }

        log.info("[update playlist done: {}]", playlistId);
        return illegalVideoCounts;
        }

@Override
public void fileTrackAndRecover(String userId, Playlists playlist, String countryCode, String accessToken) throws IOException {
        // 1. 업데이트 및 비정상 음악 목록 가져오기
        Map<String, Integer> illegalVideoIdWithPositions;
        //Playlists playlist = playlistService.findByPlaylistId(playlistId);
        String playlistId = playlist.getPlaylistId();

        try {
        illegalVideoIdWithPositions = updatePlaylist(userId, countryCode, playlist);
        } catch (IOException e) {
        log.info("[skip this playlist: {}]", playlistId);
        return;
        }

        if (illegalVideoIdWithPositions.isEmpty()) {
        log.info("[nothing to recover]");
        return;
        }
        // 2. 각 비정상 음악 처리
        for (Map.Entry<String, Integer> entry : illegalVideoIdWithPositions.entrySet()) {
        String videoIdToDelete = entry.getKey(); // XzEoBAltBII
        int apiDuplicatedCount = entry.getValue(); // 1~2

        //  3. DB 에서 해당 videoId + playlistId 를 가진 모든 음악 조회
        List<Music> backupMusicListFromDb = musicService.getMusicListFromDBThruMusicId(videoIdToDelete, playlistId);//musicRepository.getMusicListFromDBThruMusicId(videoIdToDelete, playlistId); // wtjro7_R3-4

        if (backupMusicListFromDb.isEmpty()) {
        // 백업이 없으면 그냥 중복 개수만큼 삭제
        for (int i = 0; i < apiDuplicatedCount; i++) {
        //youtubeApiClient.deleteFromActualPlaylist(accessToken, playlistId, videoIdToDelete);
        log.info("Not backed up - delete '{}' (count {}/{})", videoIdToDelete, i + 1, apiDuplicatedCount);
        outboxService.outboxInsert(Outbox.ActionType.DELETE, accessToken, userId, playlistId, videoIdToDelete);
        }
        continue;
        }

        Music backupMusic = backupMusicListFromDb.get(0); // = videoIdToDelete

        Optional<ActionLog> recentLogOpt = actionLogService.findTodayRecoverLog(ActionLog.ActionType.RECOVER, backupMusic.getVideoId());
        Music replacementMusic;
        Video replacementVideo;

        if (recentLogOpt.isPresent()) {
        log.info("[Reuse Replacement Video]: {}", recentLogOpt.get().getSourceVideoId());
        replacementVideo = youtubeApiClient.getSingleVideo(recentLogOpt.get().getSourceVideoId());
        } else {
        replacementVideo = musicService.searchVideoToReplace(backupMusic, playlist);
        }
        replacementMusic = musicService.makeVideoToMusic(replacementVideo, playlist);

        actionLogService.actionLogSave(userId, playlistId, ActionLog.ActionType.RECOVER, backupMusic, replacementMusic);

        // 복구 횟수만큼 추가 & 삭제
        for (int i = 0; i < Math.min(backupMusicListFromDb.size(), apiDuplicatedCount); i++) { // 1:1 매칭 가능한 만큼 복구
        long pk = backupMusicListFromDb.get(i).getId();
        // DB 교체 처리
        musicService.dBTrackAndRecoverPosition(videoIdToDelete, replacementMusic, pk);
        // 실제 플레이리스트 반영
        outboxService.outboxInsert(Outbox.ActionType.ADD, accessToken, userId, playlistId, replacementMusic.getVideoId());
        outboxService.outboxInsert(Outbox.ActionType.DELETE, accessToken, userId, playlistId, videoIdToDelete);

        log.info("Recovered [{}] -> [{}] at pos {}", videoIdToDelete, replacementMusic.getVideoId(), pk);
        }

        // 엣지케이스 1
        if (backupMusicListFromDb.size() > apiDuplicatedCount) { // 4(DB) > 2(API)
        for (int i = apiDuplicatedCount; i < backupMusicListFromDb.size(); i++) {
        long rowPk = backupMusicListFromDb.get(i).getId();
        musicService.deleteById(rowPk);//musicRepository.deleteById(rowPk);
        log.info("Delete extra duplicated video on DB : {}", rowPk);
        }
        }

        // 엣지케이스 2
        if(backupMusicListFromDb.size() < apiDuplicatedCount) { // 2(DB) < 4(API)
        for (int i = backupMusicListFromDb.size(); i < apiDuplicatedCount; i++) { // 남은 횟수만큼 더 업데이트 해줘야한다.
        musicService.addUpdatePlaylist(replacementMusic);//musicRepository.addUpdatePlaylist(replacementMusic);
        outboxService.outboxInsert(Outbox.ActionType.ADD, accessToken, userId, playlistId, replacementMusic.getVideoId());
        outboxService.outboxInsert(Outbox.ActionType.DELETE, accessToken, userId, playlistId, videoIdToDelete);

        log.info("Add extra duplicated video on DB : [{}]", replacementMusic.getVideoId());
        log.info("Add extra replacement video on the Playlist : [{}]", replacementMusic.getVideoId());
        }
        }

        // if(userId != null) throw new RuntimeException("Intentioned Runtime Exception"); // 고의적 예외 던짐
        }
        }

@Override
public void lazyTest(Playlists playlistParam) {

        String userId = "101758050105729632425";
        Playlists playlist = playlistService.getPlaylistsByUserId(userId).get(0);

        log.info("[LAZY TEST START]");
        log.info("1. playlist.getPlaylistTitle : {}", playlist.getPlaylistTitle());

        Users user = playlist.getUser();
        log.info("2. playlist.getUser : User");

        log.info("3. playlist.getUser.user.getUserNmae : {}", user.getUserName()); // playlistParam 으로 하면 여기서 터짐

        log.info("4. playlist.getUser.getUserName : {}", playlist.getUser().getUserName());
        log.info("[LAZY TEST DONE]");

        }

        }

 */