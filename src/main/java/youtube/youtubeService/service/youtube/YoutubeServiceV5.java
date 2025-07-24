package youtube.youtubeService.service.youtube;

import com.google.api.services.youtube.model.*;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import youtube.youtubeService.api.YoutubeApiClient;
import youtube.youtubeService.domain.ActionLog;
import youtube.youtubeService.domain.Music;
import youtube.youtubeService.dto.VideoFilterResult;
import youtube.youtubeService.policy.SearchPolicy;
import youtube.youtubeService.repository.ActionLogRepository;
import youtube.youtubeService.repository.musics.MusicRepository;
import youtube.youtubeService.repository.playlists.PlaylistRepository;

import java.io.IOException;
import java.util.*;

@Slf4j
@Service
@Transactional
public class YoutubeServiceV5 implements YoutubeService {

    private final MusicRepository musicRepository;
    private final PlaylistRepository playlistRepository;
    private final SearchPolicy searchPolicy; // Map<String, SearchPolicy> searchPolicyMap; 테스트 해보기
    private final ActionLogRepository actionLogRepository;
    private final YoutubeApiClient youtubeApiClient;

    public YoutubeServiceV5(PlaylistRepository playlistRepository, MusicRepository musicRepository,
                            @Qualifier("geminiSearchQuery") SearchPolicy searchPolicy,
                            ActionLogRepository actionLogRepository, YoutubeApiClient youtubeApiClient) {
        this.playlistRepository = playlistRepository;
        this.musicRepository = musicRepository;
        this.searchPolicy = searchPolicy;
        this.actionLogRepository = actionLogRepository;
        this.youtubeApiClient = youtubeApiClient;
    }

    public void DBAddAction(Video video, String playlistId) {
        Music music = new Music();
        music.setVideoId(video.getId());
        music.setVideoTitle(video.getSnippet().getTitle());
        music.setVideoUploader(video.getSnippet().getChannelTitle());
        music.setVideoDescription(video.getSnippet().getDescription());
        List<String> tags = video.getSnippet().getTags();
        String joinedTags = (tags != null) ? String.join(",", tags) : null;
        music.setVideoTags(joinedTags);

        music.setPlaylist(playlistRepository.findByPlaylistId(playlistId));
        musicRepository.addUpdatePlaylist(playlistId, music);
    }

    public Map<String, List<Long>> updatePlaylist(String playlistId) throws IOException {
        log.info("update playlist start ... {}", playlistId);

        // 1. API 검색으로 고객 플레이리스트 목록 불러오기
        List<PlaylistItem> pureApiPlaylistItems;
        try {
            pureApiPlaylistItems = youtubeApiClient.getPlaylistItemListResponse(playlistId, 50L);
        } catch (IOException e) {
            log.info("This playlist has been removed by the owner {}", playlistId);
            throw new IOException(playlistId);
        }

        // 2. 고객 플레이리스트 아이템 담긴 디비 불러오기
        List<Music> pureDbMusicList = musicRepository.findAllMusicByPlaylistId(playlistId);
        List<String> pureDbMusicIds = pureDbMusicList.stream().map(Music::getVideoId).toList();

        // 3. API 에서 video 상태 조회
        List<String> pureApiVideoIds = pureApiPlaylistItems.stream().map(item -> item.getSnippet().getResourceId().getVideoId()).toList();

        VideoFilterResult videoFilterResult = youtubeApiClient.safeFetchVideos(pureApiVideoIds);
        List<Video> legalVideos = videoFilterResult.getLegalVideos(); // 정상은 잘 담음
        List<Video> unlistedCountryVideos = videoFilterResult.getIllegalVideos(); // unlisted, 국가차단만 담음
        List<String> legalVideoIds = legalVideos.stream().map(Video::getId).toList();
        List<String> unlistedCountryVideoIds = unlistedCountryVideos.stream().map(Video::getId).toList();

        log.info("[legal] videos count : {}", legalVideoIds.size());
        log.info("[unlisted/country] video count : {}", unlistedCountryVideoIds.size());

        // 4. 응답 자체가 안 온 videoId = Delete/Private
        List<String> privateDeletedVideoIds = pureApiVideoIds.stream()
                .filter(videoId -> !legalVideoIds.contains(videoId)
                                && !unlistedCountryVideoIds.contains(videoId)).toList();
        log.info("[deleted/private] video count : {}", privateDeletedVideoIds.size()); // 증복이면 이게 1일듯

        // 5. 둘의 차이를 비교 → DB 반영
        // 5-1. DB 에 추가할 음악 (API 에 있지만 DB에 없는 legal 음악)
        legalVideos.stream()
                .filter(video -> !pureDbMusicIds.contains(video.getId()))
                .forEach(video -> DBAddAction(video, playlistId));

        // 5-2. DB 에서 제거할 음악 (DB에 있지만 API 에 없는 음악)
        pureDbMusicList.stream()
                .filter(video -> !pureApiVideoIds.contains(video.getVideoId()))
                .forEach(video -> musicRepository.deleteUpdatePlaylist(playlistId, video.getVideoId()));

        Map<String, List<Long>> illegalVideoPositions = new HashMap<>();
        for (PlaylistItem item : pureApiPlaylistItems) {
            String videoId = item.getSnippet().getResourceId().getVideoId();
            long position = item.getSnippet().getPosition();
            if (unlistedCountryVideoIds.contains(videoId) || privateDeletedVideoIds.contains(videoId)) {
                illegalVideoPositions.computeIfAbsent(videoId, k -> new ArrayList<>()).add(position);
            }
        }

        log.info("update playlist done ... {}", playlistId);
        return illegalVideoPositions;
    }

    @Override
    public void fileTrackAndRecover(String userId, String playlistId, String accessToken) throws IOException {
        // 1. 업데이트 및 비정상 음악 목록 가져오기
        Map<String, List<Long>> illegalVideoIdWithPositions = updatePlaylist(playlistId);

        if (illegalVideoIdWithPositions.isEmpty()) {
            log.info("nothing to recover");
            return;
        }
        // 2. 각 비정상 음악 처리
        for (Map.Entry<String, List<Long>> entry : illegalVideoIdWithPositions.entrySet()) {
            String videoIdToDelete = entry.getKey();
            List<Long> positions = entry.getValue();

            //  3. DB 에서 해당 videoId + playlistId 를 가진 모든 음악 조회
            List<Music> backupMusicListFromDb = musicRepository.getMusicListFromDBThruMusicId(videoIdToDelete, playlistId);

            if (backupMusicListFromDb.isEmpty()) {
                // 백업이 없으면 그냥 중복 개수만큼 삭제
                for (Long position : positions) {
                    youtubeApiClient.deleteFromActualPlaylist(accessToken, playlistId, videoIdToDelete);
                    log.info("Not backed up - deleted only: {} at pos {}", videoIdToDelete, position);
                }
                continue;
            }

            Music backupMusic = backupMusicListFromDb.get(0);
            Music replacementMusic = searchVideoToReplace(backupMusic, playlistId); // 복구할 대체 영상은 1번만 찾음
            actionLogRecord(userId, playlistId, "recovery", backupMusic, replacementMusic);

            // 복구 횟수만큼 추가 & 삭제 // for (int i = 0; i < backupMusicListFromDb.size(); i++) {
            for (int i = 0; i < Math.min(backupMusicListFromDb.size(), positions.size()); i++) { // 1:1 매칭 가능한 만큼 복구
                long pk = backupMusicListFromDb.get(i).getId();
                long position = positions.get(i);
                // DB 교체 처리
                musicRepository.dBTrackAndRecoverPosition(videoIdToDelete, replacementMusic, playlistId, pk);
                // 실제 플레이리스트 반영
                youtubeApiClient.addVideoToActualPlaylist(accessToken, playlistId, replacementMusic.getVideoId(), position);
                youtubeApiClient.deleteFromActualPlaylist(accessToken, playlistId, videoIdToDelete);
                log.info("Recovered [{}] -> [{}] at pos {}", videoIdToDelete, replacementMusic.getVideoId(), pk);
            }

            // 엣지케이스 1
            if (backupMusicListFromDb.size() > positions.size()) { // 4(DB) > 2(API)
                for (int i = positions.size(); i < backupMusicListFromDb.size(); i++) {
                    long rowPk = backupMusicListFromDb.get(i).getId();
                    musicRepository.deleteById(rowPk);
                    log.info("Delete extra duplicated video on DB : {}", rowPk);
                }
            }

            // 엣지케이스 2
            if(backupMusicListFromDb.size() < positions.size()) { // 2(DB) < 4(API)
                for (int i = backupMusicListFromDb.size(); i < positions.size(); i++) { // 남은 횟수만큼 더 업데이트 해줘야한다.
                    long position = positions.get(i);
                    musicRepository.addUpdatePlaylist(playlistId, replacementMusic);
                    youtubeApiClient.addVideoToActualPlaylist(accessToken, playlistId, replacementMusic.getVideoId(), position);
                    youtubeApiClient.deleteFromActualPlaylist(accessToken, playlistId, videoIdToDelete);
                    log.info("Add extra duplicated video on DB : [{}]", replacementMusic.getVideoId());
                    log.info("Add extra replacement video on the Playlist : [{}]", replacementMusic.getVideoId());
                }

            }

        }
    }

    public void actionLogRecord(String userId, String playlistId, String actionType, Music trgVid, Music srcVid) {
        // 영상 제목 및 업로드자도 저장하는게 보기 좋을 듯
        ActionLog log = new ActionLog();
        log.setUserId(userId);
        log.setPlaylistId(playlistId);
        log.setActionType(actionType);
        log.setTargetVideoId(trgVid.getVideoId());
        log.setTargetVideoTitle(trgVid.getVideoTitle());
        log.setSourceVideoId(srcVid.getVideoId());
        log.setSourceVideoTitle(srcVid.getVideoTitle());
        actionLogRepository.save(log);
    }

    public Music searchVideoToReplace(Music musicToSearch, String playlistId) throws IOException {
        // Gemini Policy 사용
        String query = searchPolicy.search(musicToSearch);
        // String query = musicToSearch.getVideoTitle().concat("-").concat(musicToSearch.getVideoUploader());
        log.info("searched with : {}", query);
        SearchResult searchResult = youtubeApiClient.searchFromYoutube(query);
        /*
        검색이 안될때 예외처리 해주긴 해야함
         */
        Music music = new Music();
        music.setVideoId(searchResult.getId().getVideoId());
        music.setVideoTitle(searchResult.getSnippet().getTitle());
        music.setVideoUploader(searchResult.getSnippet().getChannelTitle());
        music.setVideoTags(musicToSearch.getVideoTags());
        music.setVideoDescription(searchResult.getSnippet().getDescription());
        // search 결과로는 tags 얻을 수 없음. 그렇다고 또 video Id로 검색하긴 귀찮음. 그냥 놔두자.
        // 그냥 놔둘거면 기존 tags 도 얻어와야함.
        music.setPlaylist(playlistRepository.findByPlaylistId(playlistId));
        log.info("Found a music to replace : {}, {}", music.getVideoTitle(), music.getVideoUploader());

        return music;
    }

}

/** OG Code

//@Slf4j
//@Service
//@Transactional
public class YoutubeServiceV5 implements YoutubeService {

    private final MusicRepository musicRepository;
    private final PlaylistRepository playlistRepository;
    private final SearchPolicy searchPolicy; // Map<String, SearchPolicy> searchPolicyMap; 테스트 해보기
    private final ActionLogRepository actionLogRepository;
    private final YoutubeApiClient youtubeApiClient;

    public YoutubeServiceV5(PlaylistRepository playlistRepository, MusicRepository musicRepository,
                            @Qualifier("geminiSearchQuery") SearchPolicy searchPolicy,
                            ActionLogRepository actionLogRepository, YoutubeApiClient youtubeApiClient) {
        this.playlistRepository = playlistRepository;
        this.musicRepository = musicRepository;
        this.searchPolicy = searchPolicy;
        this.actionLogRepository = actionLogRepository;
        this.youtubeApiClient = youtubeApiClient;
    }

    public Video checkBrokenVideo(String videoId) {
        Video video = null;
        try {
            video = youtubeApiClient.getVideoDetailResponseWithFilter(videoId);
        } catch (RuntimeException | IOException e) {
            log.info("A broken video is caught");
            return null;
        }
        return video;
    }

    public void DBAddAction(Video video, String playlistId) {
        Music music = new Music();
        music.setVideoId(video.getId());
        music.setVideoTitle(video.getSnippet().getTitle());
        music.setVideoUploader(video.getSnippet().getChannelTitle());
        music.setVideoDescription(video.getSnippet().getDescription());
        List<String> tags = video.getSnippet().getTags();
        String joinedTags = (tags != null) ? String.join(",", tags) : null;
        music.setVideoTags(joinedTags);
        // log.info("joinedTags : {}", joinedTags);

        music.setPlaylist(playlistRepository.findByPlaylistId(playlistId));
        musicRepository.addUpdatePlaylist(playlistId, music);
    }


    public List<PlaylistItem> updatePlaylist(String playlistId) throws IOException {
        // log.info("updatePlaylist txActive : {} : {}", TransactionSynchronizationManager.isActualTransactionActive(), TransactionSynchronizationManager.getCurrentTransactionName());
        log.info("update playlist start ... {}", playlistId);
        // 1. 고객 플레이리스트 담긴 디비 불러오기
        List<Music> musicDBList = musicRepository.findAllMusicByPlaylistId(playlistId);
        Set<String> musicDBSet = musicDBList.stream().map(Music::getVideoId).collect(Collectors.toSet());
        // 2. API 검색으로 고객 플레이리스트 목록 불러오기
        List<PlaylistItem> response;
        try {
            response = youtubeApiClient.getPlaylistItemListResponse(playlistId, 50L);
        } catch (IOException e) {
            //고객이 마음대로 플리 삭제한거 예외 처리
            log.info("This playlist has been removed by the owner {}", playlistId);
            // playlistRepository.deletePlaylistByPlaylistId(playlistId); // 하고 return; 하면 안되나 <- 안됨
            throw new IOException(playlistId);
        }

        List<String> apiMusicList = new ArrayList<>();
        for (PlaylistItem item : response) {
            String videoId = item.getSnippet().getResourceId().getVideoId();
            apiMusicList.add(videoId);
        }
        Set<String> apiMusicSet = new HashSet<>(apiMusicList);
        // 3. 둘의 차이를 비교
        // 3-1. API 에는 있지만 DB 에는 없는 음악
        Set<String> addedMusicSet = new HashSet<>(apiMusicSet);
        addedMusicSet.removeAll(musicDBSet);
        // 3-2. DB 에는 있지만 API 에는 없는 음악
        Set<String> removedMusicSet = new HashSet<>(musicDBSet);
        removedMusicSet.removeAll(apiMusicSet);
        // 4. 고객이 추가 혹은 제거한 영상을 DB 에 반영
        if (!addedMusicSet.isEmpty()) {
            for (String videoId : addedMusicSet) {
                Video video = checkBrokenVideo(videoId);
                if (video == null) {
                    log.info("it turned out a broken video : edge case");
                    continue;
                }
                DBAddAction(video, playlistId);
                // DBAddAction(videoId, playlistId); // 얘는 예외 터질일이 없긴 함 <- 있음 (엣지 케이스 : 하루 만에 비정상된 케이스)
            }
        } else {
            log.info("nothing to add");
        }

        if (!removedMusicSet.isEmpty()) {
            for (String videoId : removedMusicSet) {
                musicRepository.deleteUpdatePlaylist(playlistId, videoId);
            }
        } else {
            log.info("nothing to remove");
        }

        log.info("update playlist done ... {}", playlistId);
        return response;
    }

    @Override
    public void fileTrackAndRecover(String userId, String playlistId, String accessToken) throws IOException {
        // log.info("fileTrackAndRecover txActive : {} : {}", TransactionSynchronizationManager.isActualTransactionActive(), TransactionSynchronizationManager.getCurrentTransactionName());
        List<PlaylistItem> response = null;
        try {
            response = updatePlaylist(playlistId); // update, 그후 순수 api 플리 그대로 받아옴 (중복 api 호출 방지)
        } catch (IOException e) {
            log.info("playlist update is cancelled");
            // 여기서 playlistRepository.deletePlaylistByPlaylistId(playlistId); 하고 return; 해버리면 안되나 <- 안됨
            throw e;
        }
        // 2. 비정상적인 파일 추적
        Map<String, Long> illegalVideos = getIllegalPlaylistItemList(response); // videoId, Position 뽑기
        if(illegalVideos.isEmpty()) {
            log.info("There's no music to recover");
            return;
        }
        // 3. 복구 시스템 가동
        for (String videoIdToDelete : illegalVideos.keySet()) { // illegal video 가 여러개일 수 있으니
            long videoPosition = illegalVideos.get(videoIdToDelete);
            log.info("Tracked Illegal Music ({}) at index {}", videoIdToDelete, videoPosition);
        // 4. DB 에서 videoId로 검색해서 백업된 Music 객체를 가져옴
            Optional<Music> optionalBackUpMusic = musicRepository.getMusicFromDBThruMusicId(videoIdToDelete, playlistId);
            Music backupMusic = optionalBackUpMusic.orElse(null);
        // 5. 그 Music 으로 유튜브에 검색을 함 search 해서 return 받음
            if(backupMusic == null) {
        // 6. backupMusic 이 null 이면 백업 안된 영상. 즉 사용자가 최근에 추가했지만 빠르게 삭제된 영상
                youtubeApiClient.deleteFromActualPlaylist(accessToken, playlistId, videoIdToDelete);
                // updatePlaylist 행위때 그런 음악은 디비에 저장 안했으니 디비 수정할 이유는 없다
                continue;
            }
        // 7. backupMusic 이 null 이 아니면 백업된 영상임
            Music videoForRecovery = searchVideoToReplace(backupMusic, playlistId);
        // 10. log 기록 먼저 해야함 (musicRepository 는 trg 나 src 이나 공유하니까)
            actionLogRecord(userId, playlistId, "recovery", backupMusic, videoForRecovery);
        // 8. DB를 업데이트한다 CRUD 동작은 service 가 아니라 repository 가 맡아서 한다.
            musicRepository.dBTrackAndRecover(videoIdToDelete, videoForRecovery, playlistId);
        // 9. 실제 유튜브 플레이리스트에도 add 와 delete
            youtubeApiClient.addVideoToActualPlaylist(accessToken, playlistId, videoForRecovery.getVideoId(), videoPosition);
            youtubeApiClient.deleteFromActualPlaylist(accessToken, playlistId, videoIdToDelete);
        }
    }

    public void actionLogRecord(String userId, String playlistId, String actionType, Music trgVid, Music srcVid) {
        // 영상 제목 및 업로드자도 저장하는게 보기 좋을 듯
        ActionLog log = new ActionLog();
        log.setUserId(userId);
        log.setPlaylistId(playlistId);
        log.setActionType(actionType);
        log.setTargetVideoId(trgVid.getVideoId());
        log.setTargetVideoTitle(trgVid.getVideoTitle());
        log.setSourceVideoId(srcVid.getVideoId());
        log.setSourceVideoTitle(srcVid.getVideoTitle());
        actionLogRepository.save(log);
    }

    // page 로 읽어들여야함
    public Map<String, Long> getIllegalPlaylistItemList(List<PlaylistItem> response) {
        // log.info("getIllegalPlaylistItemList txActive : {} : {}", TransactionSynchronizationManager.isActualTransactionActive(), TransactionSynchronizationManager.getCurrentTransactionName());
        Map<String, Long> videos = new HashMap<>();

        for (PlaylistItem item : response) {
            String videoId = item.getSnippet().getResourceId().getVideoId();
            String videoTitle = item.getSnippet().getTitle();
            String videoPrivacyStatus = item.getStatus().getPrivacyStatus();
            long pos = item.getSnippet().getPosition();

            if(!videoPrivacyStatus.equals("public")) {
                log.info("Unplayable video({} : {}) is detected at position {}", videoTitle, videoId, pos);
                videos.put(videoId, pos);
            }
        }
        return videos;
    }

    public Music searchVideoToReplace(Music musicToSearch, String playlistId) throws IOException {
        // Gemini Policy 사용
        String query = searchPolicy.search(musicToSearch);
        // String query = musicToSearch.getVideoTitle().concat("-").concat(musicToSearch.getVideoUploader());
        log.info("searched with : {}", query);
        SearchResult searchResult = youtubeApiClient.searchFromYoutube(query);
        // 검색이 안될때 예외처리 해주긴 해야함
        Music music = new Music();
        music.setVideoId(searchResult.getId().getVideoId());
        music.setVideoTitle(searchResult.getSnippet().getTitle());
        music.setVideoUploader(searchResult.getSnippet().getChannelTitle());
        music.setVideoTags(musicToSearch.getVideoTags());
        music.setVideoDescription(searchResult.getSnippet().getDescription());
        // search 결과로는 tags 얻을 수 없음. 그렇다고 또 video Id로 검색하긴 귀찮음. 그냥 놔두자.
        // 그냥 놔둘거면 기존 tags 도 얻어와야함.
        music.setPlaylist(playlistRepository.findByPlaylistId(playlistId));
        log.info("Found a music to replace : {}, {}", music.getVideoTitle(), music.getVideoUploader());

        return music;
        }
    }

 */