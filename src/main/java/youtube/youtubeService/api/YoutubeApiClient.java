package youtube.youtubeService.api;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import youtube.youtubeService.dto.*;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

@Slf4j
@Component
public class YoutubeApiClient {
    @Value("${youtube.api.key}")
    private String apiKey;
    private final YouTube youtube;

    public YoutubeApiClient() {
        youtube = new YouTube.Builder(new NetHttpTransport(), new GsonFactory(), request -> {}).setApplicationName("youtube").build();
    }

    public Video fetchSingleVideo(String videoId) {

        Video video = null;
        try {
            YouTube.Videos.List request = youtube.videos().list(Collections.singletonList("snippet, id, status, contentDetails"));
            request.setKey(apiKey);
            request.setId(Collections.singletonList(videoId));
            video = request.execute().getItems().get(0);
            //log.info("Found the video with 'Single Video Search'");
        } catch (IOException e) {
            log.info("Cannot find Single(Duplicated) video with this VideoId({})", videoId);
        }

        return video;
    }

    public String fetchChannelId(String accessToken) throws IOException, GeneralSecurityException {
        GoogleCredential credential = new GoogleCredential().setAccessToken(accessToken);
        YouTube youtube = new YouTube.Builder(GoogleNetHttpTransport.newTrustedTransport(), GsonFactory.getDefaultInstance(), credential)
                .setApplicationName("youtube-channel-info")
                .build();

        ChannelListResponse response;
        try {
            response = youtube.channels().list(Collections.singletonList("snippet")).setMine(true).execute();   // 현재 인증된 사용자의 채널 정보 조회
        } catch (GoogleJsonResponseException e) {
            String revokeUrl = "https://oauth2.googleapis.com/revoke?token=" + accessToken;
            RestTemplate restTemplate = new RestTemplate();
            restTemplate.postForEntity(revokeUrl, null, String.class);
            throw new IOException("Unauthorized 'youtube.force-ssl' or No channel found.. so revoked");
        }

        Channel channel = response.getItems().get(0);
        return channel.getId();
    }

    /**
     * 1개의 페이지 단위로 주고 받기.
     */
    public QuotaApiPlaylistsPageDto fetchPlaylistPage(String channelId, String pageToken) throws IOException {
        // 2. channelId로 api 호출 통해 playlist 다 받아오기
        YouTube.Playlists.List request = youtube.playlists().list(Collections.singletonList("snippet, id, contentDetails"));
        request.setKey(apiKey);
        request.setChannelId(channelId);
        request.setMaxResults(50L);
        request.setPageToken(pageToken);
        PlaylistListResponse response = request.execute();

        List<Playlist> PlaylistsBatch = new ArrayList<>(response.getItems());
        String nextPageToken = response.getNextPageToken();
        // log.info("[getApiPlaylistsByPage] - NextPageToken:{}", nextPageToken);

        return new QuotaApiPlaylistsPageDto(PlaylistsBatch, nextPageToken);
    }

    /**
     * 1개의 페이지 단위로 주고 받기.
     */
    public VideoFilterResultPageDto fetchVideoPage(List<String> videoIds, String countryCode) {
        List<Video> legal = new ArrayList<>();
        List<Video> unlistedCountryVideos = new ArrayList<>();

        try {
            YouTube.Videos.List request = youtube.videos().list(Collections.singletonList("snippet, id, status, contentDetails"));
            request.setKey(apiKey);
            request.setId(videoIds);
            request.setMaxResults((long) videoIds.size());

            List<Video> items = request.execute().getItems();
            log.info("Requested {}, Received {}", videoIds.size(), items.size());

            for (Video video : items) {
                if ("public".equals(video.getStatus().getPrivacyStatus()) && "processed".equals(video.getStatus().getUploadStatus())) {

                    if (video.getContentDetails().getRegionRestriction() != null
                            && video.getContentDetails().getRegionRestriction().getAllowed() != null
                            && !video.getContentDetails().getRegionRestriction().getAllowed().contains(countryCode)) {
                        log.info("Illegal Video Filtered (KR not allowed) : {} ({})", video.getSnippet().getTitle(), video.getId());
                        unlistedCountryVideos.add(video);
                        continue;
                    }

                    if (video.getContentDetails().getRegionRestriction() != null
                            && video.getContentDetails().getRegionRestriction().getBlocked() != null
                            && video.getContentDetails().getRegionRestriction().getBlocked().contains(countryCode)) {
                        log.info("Illegal Video Filtered (KR blocked) : {} ({})", video.getSnippet().getTitle(), video.getId());
                        unlistedCountryVideos.add(video);
                        continue;
                    }
                    legal.add(video);
                } else {
                    log.info("Illegal Video Filtered (Unlisted): {} ({})", video.getSnippet().getTitle(), video.getId());
                    unlistedCountryVideos.add(video);
                }
            }

        } catch (Exception ex) {
            log.info("Batch 실패: {}", ex.getMessage());
        }

        return new VideoFilterResultPageDto(legal, unlistedCountryVideos);
    }

    /**
     * 1개의 페이지 단위로 주고 받기.
     */
    public QuotaPlaylistItemPageDto fetchPlaylistItemPage(String playlistId, String pageToken) throws IOException {
        YouTube.PlaylistItems.List request = youtube.playlistItems().list(Collections.singletonList("snippet, id")); // , status
        request.setKey(apiKey);
        request.setPlaylistId(playlistId);
        request.setMaxResults(50L);
        request.setPageToken(pageToken);

        try {
            PlaylistItemListResponse response = request.execute();
            List<PlaylistItem> items = response.getItems();
            String nextPageToken = response.getNextPageToken();
            // log.info("[getPlaylistItemListByPage] - nextPageToken:{}", nextPageToken);

            return new QuotaPlaylistItemPageDto(items, nextPageToken);
        } catch (GoogleJsonResponseException e) {
            log.warn("Google API error while retrieving playlists for user : {}", e.getDetails().getMessage());
            if (e.getStatusCode() == 403 || e.getStatusCode() == 404) {
                log.warn("Playlist fetch forbidden or not found. Possibly deleted or private.");
            }
            throw new IOException("the playlist is deleted");
        }
    }

    public boolean addVideoToActualPlaylist(String accessToken, String playlistId, String videoId) {
        // if(playlistId.length() % 2 == 0) throw new RuntimeException("addVideoToActualPlaylist intended RuntimeException"); // 고의적 예외 던짐

        try {
            YouTube youtube = new YouTube.Builder(GoogleNetHttpTransport.newTrustedTransport(), GsonFactory.getDefaultInstance(), new GoogleCredential().setAccessToken(accessToken))
                                            .setApplicationName("youtube-add-playlist-item").build();

            ResourceId resourceId = new ResourceId();
            resourceId.setKind("youtube#video");
            resourceId.setVideoId(videoId);
            PlaylistItemSnippet playlistItemSnippet = new PlaylistItemSnippet();
            playlistItemSnippet.setPlaylistId(playlistId);
            playlistItemSnippet.setResourceId(resourceId);
            PlaylistItem playlistItem = new PlaylistItem();
            playlistItem.setSnippet(playlistItemSnippet);

            youtube.playlistItems().insert(Collections.singletonList("snippet"), playlistItem).execute();
            log.info("[completely added video({}) to {}]", videoId, playlistId);

        } catch (GoogleJsonResponseException e) {
            int statusCode = e.getStatusCode();
            String reason = e.getDetails().getErrors().get(0).getReason();

            log.warn("YouTube API Error: statusCode={}, reason={}", statusCode, reason);

            if (statusCode == 404 && "playlistNotFound".equals(reason)) {
                log.warn("Playlist item already deleted or not found. Skipping gracefully.");
                return true; // 아웃박스에 담아 추가하려 했는데, 고객이 플리 자체를 실행 직전에 먼저 제거해버린 케이스임
            }

            if (statusCode == 409 && "conflict".equals(reason)) {
                log.warn("Conflict occurred — possibly concurrent addition or duplicate request.");
                return false; // 재시도
            }

            return false; // 그 외의 예외는 실패 처리

        } catch (IOException | GeneralSecurityException e) {
            log.warn("Unexpected exception during video add:", e);
            return false;
        }

        return true;
    }

    public boolean deleteFromActualPlaylist(String accessToken, String playlistItemId) {
        // if(true) throw new RuntimeException("deleteFromActualPlaylist intended RuntimeException"); // 고의적 예외 던짐

        try {
            YouTube youtube = new YouTube.Builder(GoogleNetHttpTransport.newTrustedTransport(), GsonFactory.getDefaultInstance(), new GoogleCredential().setAccessToken(accessToken))
                                            .setApplicationName("youtube-delete-playlist-item").build();

            youtube.playlistItems().delete(playlistItemId).execute();
            log.info("[completely deleted PlaylistItemId]: {}", playlistItemId);

        } catch (GoogleJsonResponseException e) {
            int statusCode = e.getStatusCode();
            String reason = e.getDetails().getErrors().get(0).getReason();

            log.warn("YouTube API Error: statusCode={}, reason={}", statusCode, reason);

            if (statusCode == 404 && "playlistItemNotFound".equals(reason)) {
                log.warn("Playlist item already deleted or not found. Skipping gracefully.");
                return true; // 아웃박스에 담아 제거하려고 했는데, 고객이 아웃박스 실행 직전에 먼저 제거해버린 케이스임
            }

            if (statusCode == 409 && "conflict".equals(reason)) {
                log.warn("Conflict occurred — possibly concurrent deletion or duplicate request.");
                return false; // 재시도
            }

            return false; // 그 외의 예외는 실패 처리

        } catch (IOException | GeneralSecurityException e) {
            log.warn("Unexpected exception during video delete:", e);
            return false;
        }

        return true;
    }

    public SearchResult searchFromYoutube(String query) throws IOException {
        YouTube.Search.List search = youtube.search().list(Collections.singletonList("id, snippet"));
        search.setKey(apiKey);
        search.setQ(query);

        SearchListResponse searchResponse = search.execute();                   // 검색 요청 실행 및 응답 받아오기
        List<SearchResult> searchResultList = searchResponse.getItems();        // 검색 결과에서 동영상 목록 가져오기
        return searchResultList.get(0); // 검색 결과 중 첫 번째 동영상 정보 가져오기
    }

    public void updateVideoPrivacyStatus(String accessToken, String videoId, String changedStatus) {

        try {
            YouTube youtube = new YouTube.Builder(GoogleNetHttpTransport.newTrustedTransport(), GsonFactory.getDefaultInstance(), new GoogleCredential()
                    .setAccessToken(accessToken))
                    .setApplicationName("youtube-add-playlist-item").build();

            YouTube.Videos.List listRequest = youtube.videos()
                    .list(Collections.singletonList("id, status"))
                    .setKey(apiKey)
                    .setId(Collections.singletonList(videoId));

            Video original = listRequest.execute().getItems().get(0);
            log.info("Found video: {} with status {}", original.getId(), original.getStatus().getPrivacyStatus());

            // 2. 업데이트용 Video 객체 구성
            Video updatedVideo = new Video();
            updatedVideo.setId(videoId);
            VideoStatus status = new VideoStatus();
            status.setPrivacyStatus(changedStatus); // "public", "unlisted", or "private"
            updatedVideo.setStatus(status);

            // 3. update 요청
            youtube.videos().update(Collections.singletonList("status"), updatedVideo).execute();
            log.info("Video Status Changed to: {}", changedStatus);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}


/** OGCODE BEFORE 1024
 @Slf4j
 @Component
 public class YoutubeApiClient {
     @Value("${youtube.api.key}")
     private String apiKey;
     private final YouTube youtube;

     public YoutubeApiClient() {
     youtube = new YouTube.Builder(new NetHttpTransport(), new GsonFactory(), request -> {}).setApplicationName("youtube").build();
     }

     public Video fetchSingleVideo(String videoId) {

     Video video = null;
     try {
     YouTube.Videos.List request = youtube.videos().list(Collections.singletonList("snippet, id, status, contentDetails"));
     request.setKey(apiKey);
     request.setId(Collections.singletonList(videoId));
     video = request.execute().getItems().get(0);
     //log.info("Found the video with 'Single Video Search'");
     } catch (IOException e) {
     log.info("Cannot find Single(Duplicated) video with this VideoId({})", videoId);
     }

     return video;
     }

     public String fetchChannelId(String accessToken) throws IOException, GeneralSecurityException {
     GoogleCredential credential = new GoogleCredential().setAccessToken(accessToken);
     YouTube youtube = new YouTube.Builder(GoogleNetHttpTransport.newTrustedTransport(), GsonFactory.getDefaultInstance(), credential)
     .setApplicationName("youtube-channel-info")
     .build();

     ChannelListResponse response;
     try {
     response = youtube.channels().list(Collections.singletonList("snippet")).setMine(true).execute();   // 현재 인증된 사용자의 채널 정보 조회
     } catch (GoogleJsonResponseException e) {
     String revokeUrl = "https://oauth2.googleapis.com/revoke?token=" + accessToken;
     RestTemplate restTemplate = new RestTemplate();
     restTemplate.postForEntity(revokeUrl, null, String.class);
     throw new IOException("Unauthorized 'youtube.force-ssl' or No channel found.. so revoked");
     }

     Channel channel = response.getItems().get(0);
     return channel.getId();
     }

    public QuotaApiPlaylistsPageDto fetchPlaylistPage(String channelId, String pageToken) throws IOException {
        // 2. channelId로 api 호출 통해 playlist 다 받아오기
        YouTube.Playlists.List request = youtube.playlists().list(Collections.singletonList("snippet, id, contentDetails"));
        request.setKey(apiKey);
        request.setChannelId(channelId);
        request.setMaxResults(50L);
        request.setPageToken(pageToken);
        PlaylistListResponse response = request.execute();

        List<Playlist> PlaylistsBatch = new ArrayList<>(response.getItems());
        String nextPageToken = response.getNextPageToken();
        log.info("[getApiPlaylistsByPage] - NextPageToken:{}", nextPageToken);

        return new QuotaApiPlaylistsPageDto(PlaylistsBatch, nextPageToken);
    }


    public VideoFilterResultPageDto fetchVideoPage(List<String> videoIds, String countryCode) {
        List<Video> legal = new ArrayList<>();
        List<Video> unlistedCountryVideos = new ArrayList<>();

        try {
            YouTube.Videos.List request = youtube.videos().list(Collections.singletonList("snippet, id, status, contentDetails"));
            request.setKey(apiKey);
            request.setId(videoIds);
            request.setMaxResults((long) videoIds.size());

            List<Video> items = request.execute().getItems();
            log.info("Requested {}, Received {}", videoIds.size(), items.size());

            for (Video video : items) {
                if ("public".equals(video.getStatus().getPrivacyStatus()) && "processed".equals(video.getStatus().getUploadStatus())) {

                    if (video.getContentDetails().getRegionRestriction() != null
                            && video.getContentDetails().getRegionRestriction().getAllowed() != null
                            && !video.getContentDetails().getRegionRestriction().getAllowed().contains(countryCode)) {
                        log.info("Illegal Video Filtered (KR not allowed) : {} ({})", video.getSnippet().getTitle(), video.getId());
                        unlistedCountryVideos.add(video);
                        continue;
                    }

                    if (video.getContentDetails().getRegionRestriction() != null
                            && video.getContentDetails().getRegionRestriction().getBlocked() != null
                            && video.getContentDetails().getRegionRestriction().getBlocked().contains(countryCode)) {
                        log.info("Illegal Video Filtered (KR blocked) : {} ({})", video.getSnippet().getTitle(), video.getId());
                        unlistedCountryVideos.add(video);
                        continue;
                    }
                    legal.add(video);
                } else {
                    log.info("Illegal Video Filtered (Unlisted): {} ({})", video.getSnippet().getTitle(), video.getId());
                    unlistedCountryVideos.add(video);
                }
            }

        } catch (Exception ex) {
            log.info("Batch 실패: {}", ex.getMessage());
        }

        return new VideoFilterResultPageDto(legal, unlistedCountryVideos);
    }


    public QuotaPlaylistItemPageDto fetchPlaylistItemPage(String playlistId, String pageToken) throws IOException {
        YouTube.PlaylistItems.List request = youtube.playlistItems().list(Collections.singletonList("snippet, id, status"));
        request.setKey(apiKey);
        request.setPlaylistId(playlistId);
        request.setMaxResults(50L);
        request.setPageToken(pageToken);

        try {
            PlaylistItemListResponse response = request.execute();
            List<PlaylistItem> items = response.getItems();
            String nextPageToken = response.getNextPageToken();
            log.info("[getPlaylistItemListByPage] - nextPageToken:{}", nextPageToken);

            return new QuotaPlaylistItemPageDto(items, nextPageToken);
        } catch (GoogleJsonResponseException e) {
            log.warn("Google API error while retrieving playlists for user : {}", e.getDetails().getMessage());
            if (e.getStatusCode() == 403 || e.getStatusCode() == 404) {
                log.warn("Playlist fetch forbidden or not found. Possibly deleted or private.");
            }
            throw new IOException("the playlist is deleted");
        }
    }

    public int addVideoToActualPlaylist(String accessToken, String playlistId, String videoId) {
        //if(videoPosition % 2 == 0) throw new RuntimeException("addVideoToActualPlaylist intended RuntimeException"); // 고의적 예외 던짐

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
            //playlistItemSnippet.setPosition(videoPosition); // added
            PlaylistItem playlistItem = new PlaylistItem();
            playlistItem.setSnippet(playlistItemSnippet);

            YouTube.PlaylistItems.Insert request = youtube.playlistItems().insert(Collections.singletonList("snippet"), playlistItem);
            PlaylistItem response = request.execute();
            log.info("completely added video({}) to {}", videoId, playlistId);

        } catch (IOException e) {
            e.printStackTrace();
        } catch (GeneralSecurityException e) {
            e.printStackTrace();
            // throw new RuntimeException(e);
        }

        return 0;
    }

    public int deleteFromActualPlaylist(String accessToken, String playlistId, String videoId) {
        // if(videoId != null) throw new RuntimeException(); // 고의적 예외 던짐

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("Delete failed for {} : {} : {}", playlistId, videoId, e.getMessage());
        }

        int duplicatedVideoRemovedCount = 0;

        try {
            GoogleCredential credential = new GoogleCredential().setAccessToken(accessToken);
            YouTube youtube = new YouTube.Builder(GoogleNetHttpTransport.newTrustedTransport(), GsonFactory.getDefaultInstance(), credential)
                    .setApplicationName("youtube-delete-playlist-item")
                    .build();

            YouTube.PlaylistItems.List playlistItemsRequest = youtube.playlistItems().list(Collections.singletonList("id, snippet"));
            playlistItemsRequest.setPlaylistId(playlistId);
            playlistItemsRequest.setMaxResults(50L);

            List<PlaylistItem> playlistItems = new ArrayList<>();
            String nextPageToken = null;
            do {
                playlistItemsRequest.setPageToken(nextPageToken);
                PlaylistItemListResponse response = playlistItemsRequest.execute();
                playlistItems.addAll(response.getItems());
                nextPageToken = response.getNextPageToken();
            } while (nextPageToken != null);

            // 영상 ID와 일치하는 재생목록 항목을 찾음
            for (PlaylistItem playlistItem : playlistItems) {
                if (playlistItem.getSnippet().getResourceId().getVideoId().equals(videoId)) {
                    YouTube.PlaylistItems.Delete deleteRequest = youtube.playlistItems().delete(playlistItem.getId());
                    deleteRequest.execute();
                    duplicatedVideoRemovedCount++;
                    log.info("duplicatedVideoCount: {}", duplicatedVideoRemovedCount);
                    // return;
                }
            }

        } catch (IOException | GeneralSecurityException e) {
            log.info("[print stack trace] - probably 409 conflict?");
            e.printStackTrace();
        }

        return duplicatedVideoRemovedCount;
    }

    public SearchResult searchFromYoutube(String query) throws IOException {
        YouTube.Search.List search = youtube.search().list(Collections.singletonList("id, snippet"));
        search.setKey(apiKey);
        search.setQ(query);

        SearchListResponse searchResponse = search.execute();                   // 검색 요청 실행 및 응답 받아오기
        List<SearchResult> searchResultList = searchResponse.getItems();        // 검색 결과에서 동영상 목록 가져오기
        return searchResultList.get(0); // 검색 결과 중 첫 번째 동영상 정보 가져오기
    }

}
 */