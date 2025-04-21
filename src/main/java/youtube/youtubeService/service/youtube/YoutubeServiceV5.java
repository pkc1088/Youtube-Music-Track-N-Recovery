package youtube.youtubeService.service.youtube;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import youtube.youtubeService.domain.Music;
import youtube.youtubeService.policy.SearchPolicy;
import youtube.youtubeService.repository.musics.MusicRepository;
import youtube.youtubeService.repository.playlists.PlaylistRepository;
import youtube.youtubeService.service.musics.MusicService;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.*;

@Slf4j
@Service
public class YoutubeServiceV5 implements YoutubeService {

    @Value("${youtube.api.key}")
    private String apiKey;

    private static YouTube youtube;
    private final MusicRepository musicRepository;
    private final PlaylistRepository playlistRepository;
    private final MusicService musicService;
    private final SearchPolicy searchPolicy; // Map<String, SearchPolicy> searchPolicyMap; 테스트 해보기

    public YoutubeServiceV5(PlaylistRepository playlistRepository, MusicRepository musicRepository,
                            MusicService musicService, @Qualifier("geminiSearchQuery") SearchPolicy searchPolicy) {
        this.playlistRepository = playlistRepository;
        this.musicRepository = musicRepository;
        this.musicService = musicService;
        this.searchPolicy = searchPolicy;
        youtube = new YouTube.Builder(new NetHttpTransport(), new GsonFactory(), request -> {}).setApplicationName("youtube").build();
    }

    public List<PlaylistItem> getPlaylistItemListResponse(String playlistId, Long maxResults) throws IOException {
        YouTube.PlaylistItems.List request = youtube.playlistItems().list(Collections.singletonList("snippet, id, status"));
        request.setKey(apiKey);
        request.setPlaylistId(playlistId);
        request.setMaxResults(maxResults);
        // page
        List<PlaylistItem> allPlaylists = new ArrayList<>();
        String nextPageToken = null;
        do {
            request.setPageToken(nextPageToken); // 다음 페이지 토큰 설정
            PlaylistItemListResponse response = request.execute();
            allPlaylists.addAll(response.getItems());
            nextPageToken = response.getNextPageToken();
        } while (nextPageToken != null); // 더 이상 페이지가 없을 때까지 반복

        return allPlaylists;
    }

    @Override
    public void fileTrackAndRecover(String userId, String playlistId, String accessToken) throws IOException {
        // 0. userId로 먼저 조회 후 거기에 딸린 많은 playlistId 추출해서 반복문 돌릴 수 있음
        // 1. 사용자의 업데이트된 목록 최신화 로직 <- Music 으로 편입
        musicService.updatePlaylist(playlistId); // 주입 받아야함
        // 2. 비정상적인 파일 추적
        Map<String, Long> illegalVideos = getIllegalPlaylistItemList(playlistId); // videoId, Position 뽑기
        if(illegalVideos.isEmpty()) {
            log.info("There's no music to recover");
            return;
        }
        // 3. 복구 시스템 가동
        for (String videoIdToDelete : illegalVideos.keySet()) {
            // illegal video 가 여러개일 수 있으니
            long videoPosition = illegalVideos.get(videoIdToDelete);
            log.info("Tracked Illegal Music ({}) at index {}", videoIdToDelete, videoPosition);
        // 4. DB 에서 videoId로 검색해서 백업된 Music 객체를 가져옴
            Optional<Music> optionalBackUpMusic = musicRepository.getMusicFromDBThruMusicId(videoIdToDelete, playlistId);
            Music backupMusic = optionalBackUpMusic.orElse(null);
        // 5. 그 Music 으로 유튜브에 검색을 함 search 해서 return 받음
            if(backupMusic == null) {
        // 6. backupMusic 이 null 이면 백업 안된 영상. 즉 사용자가 최근에 추가했지만 빠르게 삭제된 영상
                deleteFromActualPlaylist(accessToken, playlistId, videoIdToDelete);
                // updatePlaylist 행위때 그런 음악은 디비에 저장 안했으니 디비 수정할 이유는 없다
                continue;
            }
        // 7. backupMusic 이 null 이 아니면 백업된 영상임 (search 알고리즘 강화 필요)
            Music videoForRecovery = searchVideoToReplace(backupMusic, playlistId);
        // 8. DB를 업데이트한다 CRUD 동작은 service 가 아니라 repository 가 맡아서 한다.
            musicRepository.dBTrackAndRecover(videoIdToDelete, videoForRecovery, playlistId);
        // 9. 실제 유튜브 플레이리스트에도 add 와 delete
            addVideoToActualPlaylist(accessToken, playlistId, videoForRecovery.getVideoId(), videoPosition);
            deleteFromActualPlaylist(accessToken, playlistId, videoIdToDelete);
        }
    }

    // page 로 읽어들여야함
    public Map<String, Long> getIllegalPlaylistItemList(String playlistId) throws IOException {

//        PlaylistItemListResponse response = getPlaylistItemListResponse(playlistId, 50L);
        List<PlaylistItem> response = getPlaylistItemListResponse(playlistId, 50L);

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

        YouTube.Search.List search = youtube.search().list(Collections.singletonList("id, snippet"));
        search.setKey(apiKey);
        search.setQ(query);

        SearchListResponse searchResponse = search.execute();                   // 검색 요청 실행 및 응답 받아오기
        List<SearchResult> searchResultList = searchResponse.getItems();        // 검색 결과에서 동영상 목록 가져오기
        SearchResult searchResult = searchResultList.get(0);                    // 검색 결과 중 첫 번째 동영상 정보 가져오기

        Music music = new Music();
        music.setVideoId(searchResult.getId().getVideoId());
        music.setVideoTitle(searchResult.getSnippet().getTitle());
        music.setVideoUploader(searchResult.getSnippet().getChannelTitle());
        music.setVideoDescription("someDescription"); // searchResult.getSnippet().getDescription();
        music.setVideoTags("someTags"); // String videoTags = not available here
        // music.setVideoPlaylistPosition(5);  // 굳이 필요한지 판단
        music.setPlaylist(playlistRepository.findByPlaylistId(playlistId));
        log.info("Found a music to replace : {}, {}", music.getVideoTitle(), music.getVideoUploader());

        return music;
    }

    // insert 할 떄 요구되는 속성 다시 점검
    public void addVideoToActualPlaylist(String accessToken, String playlistId, String videoId, long videoPosition) {
        try {
            GoogleCredential credential = new GoogleCredential().setAccessToken(accessToken);
            YouTube youtube = new YouTube.Builder(GoogleNetHttpTransport.newTrustedTransport(), GsonFactory.getDefaultInstance(), credential)
                    .setApplicationName("youtube-add-playlist-item")
                    .build();

            ResourceId resourceId = new ResourceId();
            resourceId.setKind("youtube#video");
            resourceId.setVideoId(videoId);
            PlaylistItemSnippet playlistItemSnippet = new PlaylistItemSnippet();
            playlistItemSnippet.setPlaylistId(playlistId);
            playlistItemSnippet.setResourceId(resourceId);
            playlistItemSnippet.setPosition(videoPosition); // added
            PlaylistItem playlistItem = new PlaylistItem();
            playlistItem.setSnippet(playlistItemSnippet);

            YouTube.PlaylistItems.Insert request = youtube.playlistItems().insert(Collections.singletonList("snippet"), playlistItem);
            PlaylistItem response = request.execute();
            log.info("completely added video({}) to {}", videoId, playlistId);

        } catch (IOException e) {
            e.printStackTrace();
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    // 불필요한 동작 수정 필요
    public void deleteFromActualPlaylist(String accessToken, String playlistId, String videoId) {
        try {
            GoogleCredential credential = new GoogleCredential().setAccessToken(accessToken);
            YouTube youtube = new YouTube.Builder(GoogleNetHttpTransport.newTrustedTransport(), GsonFactory.getDefaultInstance(), credential)
                    .setApplicationName("youtube-delete-playlist-item")
                    .build();

            // 재생목록에서 영상을 찾기 위해 playlistItems.list 호출
            YouTube.PlaylistItems.List playlistItemsRequest = youtube.playlistItems().list(Collections.singletonList("id,snippet"));
            playlistItemsRequest.setPlaylistId(playlistId);
            playlistItemsRequest.setMaxResults(50L);

//            PlaylistItemListResponse playlistItemsResponse = playlistItemsRequest.execute();
//            List<PlaylistItem> playlistItems = playlistItemsResponse.getItems();

            // page
            List<PlaylistItem> playlistItems = new ArrayList<>();
            String nextPageToken = null;
            do {
                playlistItemsRequest.setPageToken(nextPageToken); // 다음 페이지 토큰 설정
                PlaylistItemListResponse response = playlistItemsRequest.execute();
                playlistItems.addAll(response.getItems());
                nextPageToken = response.getNextPageToken();
            } while (nextPageToken != null); // 더 이상 페이지가 없을 때까지 반복

            // 영상 ID와 일치하는 재생목록 항목을 찾음
            for (PlaylistItem playlistItem : playlistItems) {
                if (playlistItem.getSnippet().getResourceId().getVideoId().equals(videoId)) {
                    YouTube.PlaylistItems.Delete deleteRequest = youtube.playlistItems().delete(playlistItem.getId());
                    deleteRequest.execute();
                    // 조건에 맞으면 여러개 한번에 삭제할 수 있음
                    return;
                }
            }

        } catch (IOException | GeneralSecurityException e) {
            e.printStackTrace();
        }
    }

}

//지금 이 코드 문제가 플레이리스별로 토큰을 새로 발급하고 있음
//플레이리스트 기준이 아니라 고객 별로 발급 받아도 충분하다. 이거 수정해야함
// 3. 토큰 획득 1일 1회 (accessToken <- refreshToken)
//        log.info("once a day : accessToken <- refreshToken");
//        Users user = userRepository.findByUserId(userId);
//        String refreshToken = user.getRefreshToken();
//        String accessToken = refreshAccessToken(refreshToken);
//    public String refreshAccessToken(String refreshToken) { // 사실 이게 핵심인듯?
//        try {
//            GoogleRefreshTokenRequest refreshTokenRequest = new GoogleRefreshTokenRequest(
//                    new NetHttpTransport(),
//                    new GsonFactory(),
//                    refreshToken,
//                    clientId,
//                    clientSecret
//            );
//            TokenResponse tokenResponse = refreshTokenRequest.execute();
//            return tokenResponse.getAccessToken();
//        } catch (IOException e) {
//            e.printStackTrace();
//            throw new RuntimeException("Failed to refresh access token");
//        }
//    }
/*
//
//    // musicService 로 편입 필요
//    public Video getVideoDetailResponseWithFilter(String videoId) throws IOException {
//        YouTube.Videos.List request = youtube.videos().list(Collections.singletonList("snippet, id, status"));
//        request.setKey(apiKey);
//        request.setId(Collections.singletonList(videoId));
//        VideoListResponse response = request.execute();
//
//        Video video = response.getItems().get(0);
//        if(video.getStatus().getPrivacyStatus().equals("public") && video.getStatus().getUploadStatus().equals("processed")) {
//            return video;
//        } else if (video.isEmpty()) {
//            throw new IndexOutOfBoundsException("Deleted/Private Video");
//        } else {
//            throw new RuntimeException("Not A Public Video");
//        }
//    }
//
//    // musicService 로 편입 필요 ?
//    @Override
//    public void initiallyAddVideoDetails(String playlistId) throws IOException {
//        PlaylistItemListResponse response = getPlaylistItemListResponse(playlistId, 50L);
//
//        for (PlaylistItem item : response.getItems()) {
//            String videoId = item.getSnippet().getResourceId().getVideoId();
//            DBAddAction(videoId, playlistId);
//        }
//    }
//
//    public void DBAddAction(String videoId, String playlistId) throws IOException {
//        try {
//            Video video = getVideoDetailResponseWithFilter(videoId);
//            Music music = new Music();
//
//            music.setVideoId(videoId);
//            music.setVideoTitle(video.getSnippet().getTitle());
//            music.setVideoUploader(video.getSnippet().getChannelTitle());
//            music.setVideoDescription("someDescription"); // video.getSnippet().getDescription();
//            music.setVideoTags("someTags"); // video.getSnippet().getTags();
//            music.setVideoPlaylistPosition(5);  // 굳이? 필요한지 판단
//            music.setPlaylistId(playlistRepository.findByPlaylistId(playlistId));
//            youtubeRepository.addUpdatePlaylist(playlistId, music);
//
//        } catch (RuntimeException e) {
//            System.err.println("Inaccessible Video Adding -> Aborted From DBAddAction");
//        }
//    }
//
//    @Override
//    public void updatePlaylist(String playlistId) throws IOException { // 나중에 user 기반으로 디비 조회하도록 RDB 설계해야함 email 불필요
//        System.err.println("update playlist start ...");
//
////        playlistRepository.findByPlaylistId(playlistId).getPlaylistItems();
//        // 1. 고객 플레이리스트 담긴 디비 불러오기
//        List<Music> musicDBList = youtubeRepository.findAllMusicByPlaylistId(playlistId);
//        Set<String> musicDBSet = musicDBList.stream().map(Music::getVideoId).collect(Collectors.toSet());
//
//        // 2. API 검색으로 고객 플레이리스트 목록 불러오기
//        PlaylistItemListResponse response = getPlaylistItemListResponse(playlistId, 50L);
//        List<String> apiMusicList = new ArrayList<>();
//        for (PlaylistItem item : response.getItems()) {
//            String videoId = item.getSnippet().getResourceId().getVideoId();
//            apiMusicList.add(videoId);
//        }
//        Set<String> apiMusicSet = new HashSet<>(apiMusicList);
//
//        // 3. 둘의 차이를 비교
//        // 3-1. API 에는 있지만 DB 에는 없는 음악
//        Set<String> addedMusicSet = new HashSet<>(apiMusicSet);
//        addedMusicSet.removeAll(musicDBSet);
//        // 3-2. DB 에는 있지만 API 에는 없는 음악
//        Set<String> removedMusicSet = new HashSet<>(musicDBSet);
//        removedMusicSet.removeAll(apiMusicSet);
//
//        // 4. 고객이 추가 혹은 제거한 영상을 DB 에 반영
//        if(!addedMusicSet.isEmpty())
//            for(String videoId : addedMusicSet) {
//                DBAddAction(videoId, playlistId);
//            } else {System.out.println("nothing to add");}
//
//        if(!removedMusicSet.isEmpty())
//            for(String videoId : removedMusicSet) {
//                youtubeRepository.deleteUpdatePlaylist(playlistId, videoId);
//            } else {System.out.println("nothing to remove");}
//
//        // 5. 고려 사항
//        // 5-1. 플레이리스트 자체가 없을 수가 있다. (고객이 제거해서 -> 2번에서 catch 로 잡아야 할 듯)
//        // 5-2. 플레이리스트 이름이 변경 됐을 수 있다.
//        // 5-3. 고려하지 않아도 될 사항 : 플리 자체가 추가 됐다면 그건 고객이 다시 등록을 해야한다.
//        System.err.println("update playlist done ...");
//    }

 */