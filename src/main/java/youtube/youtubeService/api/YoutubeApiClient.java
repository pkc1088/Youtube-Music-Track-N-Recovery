package youtube.youtubeService.api;

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
import youtube.youtubeService.dto.VideoFilterResult;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
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


    public String getChannelIdByUserId(String accessToken) throws IOException, GeneralSecurityException {
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
            throw new IOException("Unauthorized 'youtube.force-ssl' or No channel found.. so revoked");// throw new RuntimeException("Unauthorized 'youtube.force-ssl' or No channel found.. so revoked");
        }

        Channel channel = response.getItems().get(0);
        return channel.getId();
    }

    public List<Playlist> getApiPlaylists(String channelId) throws IOException {
        // 2. channelId로 api 호출 통해 playlist 다 받아오기
        YouTube.Playlists.List request = youtube.playlists().list(Collections.singletonList("snippet, id, contentDetails"));
        request.setKey(apiKey);
        request.setChannelId(channelId);
        request.setMaxResults(50L);

        List<Playlist> allPlaylists = new ArrayList<>();
        String nextPageToken = null;
        do {
            request.setPageToken(nextPageToken); // 다음 페이지 토큰 설정
            PlaylistListResponse response = request.execute();
            allPlaylists.addAll(response.getItems());
            nextPageToken = response.getNextPageToken();
        } while (nextPageToken != null); // 더 이상 페이지가 없을 때까지 반복

        return allPlaylists;
    }

    public VideoFilterResult safeFetchVideos(List<String> videoIds) { // List<Video>
        List<Video> legal = new ArrayList<>();
        List<Video> illegal = new ArrayList<>();
        if (videoIds.isEmpty()) return null; //legal;

        int batchSize = 50;
        int total = videoIds.size();
        int batchCount = (int) Math.ceil((double) total / batchSize);

        for (int i = 0; i < batchCount; i++) {
            int fromIndex = i * batchSize;
            int toIndex = Math.min(fromIndex + batchSize, total);
            List<String> batch = videoIds.subList(fromIndex, toIndex);

            try {
                YouTube.Videos.List request = youtube.videos().list(Collections.singletonList("snippet, id, status, contentDetails"));
                request.setKey(apiKey);
                request.setId(batch);
                request.setMaxResults((long) batch.size());

                List<Video> items = request.execute().getItems();
                log.info("----------Batch {}: Requested {}, Received {}----------", i + 1, batch.size(), items.size());

                for (Video video : items) {
                    if ("public".equals(video.getStatus().getPrivacyStatus()) && "processed".equals(video.getStatus().getUploadStatus())) {

                        if (video.getContentDetails().getRegionRestriction() != null
                                && video.getContentDetails().getRegionRestriction().getAllowed() != null
                                && !video.getContentDetails().getRegionRestriction().getAllowed().contains("KR")) {
                            log.info("Illegal Video Filtered (KR not allowed) : {} ({})", video.getSnippet().getTitle(), video.getId());
                            illegal.add(video);
                            continue;
                        }

                        if (video.getContentDetails().getRegionRestriction() != null
                                && video.getContentDetails().getRegionRestriction().getBlocked() != null
                                && video.getContentDetails().getRegionRestriction().getBlocked().contains("KR")) {
                            log.info("Illegal Video Filtered (KR blocked) : {} ({})", video.getSnippet().getTitle(), video.getId());
                            illegal.add(video);
                            continue;
                        }
                        // log.info("Legal Video : {} ({})", video.getSnippet().getTitle(), video.getId());
                        legal.add(video);
                    } else {
                        log.info("Illegal Video Filtered (Unlisted): {} ({})", video.getSnippet().getTitle(), video.getId());
                        illegal.add(video);
                    }
                }

            } catch (Exception ex) {
                log.info("Batch {} 실패: {}", i + 1, ex.getMessage());
            }
        }
        return new VideoFilterResult(legal, illegal);
    }

    public List<PlaylistItem> getPlaylistItemListResponse(String playlistId, Long maxResults) throws IOException {
        YouTube.PlaylistItems.List request = youtube.playlistItems().list(Collections.singletonList("snippet, id, status"));
        request.setKey(apiKey);
        request.setPlaylistId(playlistId);
        request.setMaxResults(maxResults);

        List<PlaylistItem> allPlaylists = new ArrayList<>();
        String nextPageToken = null;
        try {
            do {
                request.setPageToken(nextPageToken); // 다음 페이지 토큰 설정
                PlaylistItemListResponse response = request.execute();
                allPlaylists.addAll(response.getItems());
                nextPageToken = response.getNextPageToken();
            } while (nextPageToken != null); // 더 이상 페이지가 없을 때까지 반복
        } catch (GoogleJsonResponseException e) { //
            log.warn("Google API error while retrieving playlists for user : {}", e.getDetails().getMessage());
            if (e.getStatusCode() == 403 || e.getStatusCode() == 404) {
                log.warn("Playlist fetch forbidden or not found. Possibly deleted or private.");
            }
            throw new IOException("the playlist is deleted");//            throw e;
        }

        return allPlaylists;
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
            e.printStackTrace();
            // throw new RuntimeException(e);
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

    public SearchResult searchFromYoutube(String query) throws IOException {
        YouTube.Search.List search = youtube.search().list(Collections.singletonList("id, snippet"));
        search.setKey(apiKey);
        search.setQ(query);

        SearchListResponse searchResponse = search.execute();                   // 검색 요청 실행 및 응답 받아오기
        List<SearchResult> searchResultList = searchResponse.getItems();        // 검색 결과에서 동영상 목록 가져오기
        return searchResultList.get(0); // 검색 결과 중 첫 번째 동영상 정보 가져오기
    }

}

/** OG Code

//@Slf4j
//@Component
public class YoutubeApiClient {
    @Value("${youtube.api.key}")
    private String apiKey;
    private final YouTube youtube; // static

    public YoutubeApiClient() {
        youtube = new YouTube.Builder(new NetHttpTransport(), new GsonFactory(), request -> {}).setApplicationName("youtube").build();
    }

    public List<Playlist> getApiPlaylists(String channelId) throws IOException {
        // 2. channelId로 api 호출 통해 playlist 다 받아오기
        YouTube.Playlists.List request = youtube.playlists().list(Collections.singletonList("snippet, id, contentDetails"));
        request.setKey(apiKey);
        request.setChannelId(channelId);
        request.setMaxResults(50L);

        List<Playlist> allPlaylists = new ArrayList<>();
        String nextPageToken = null;
        do {
            request.setPageToken(nextPageToken); // 다음 페이지 토큰 설정
            PlaylistListResponse response = request.execute();
            allPlaylists.addAll(response.getItems());
            nextPageToken = response.getNextPageToken();
        } while (nextPageToken != null); // 더 이상 페이지가 없을 때까지 반복

        return allPlaylists;
    }

    public Video getVideoDetailResponseWithFilter(String videoId) throws IOException {
        YouTube.Videos.List request = youtube.videos().list(Collections.singletonList("snippet, id, status"));
        request.setKey(apiKey);
        request.setId(Collections.singletonList(videoId));

        Video video = null;
        try {
            video = request.execute().getItems().get(0);
            if(video.getStatus().getPrivacyStatus().equals("public") && video.getStatus().getUploadStatus().equals("processed")) {
                log.info("Legal Video : {}", video.getSnippet().getTitle()); // added
            }
        } catch(RuntimeException e) {
            throw new IOException("Delete/Private/Not a Public Video");
        }
        return video;
    }

    public List<PlaylistItem> getPlaylistItemListResponse(String playlistId, Long maxResults) throws IOException {
        YouTube.PlaylistItems.List request = youtube.playlistItems().list(Collections.singletonList("snippet, id, status"));
        request.setKey(apiKey);
        request.setPlaylistId(playlistId);
        request.setMaxResults(maxResults);

        List<PlaylistItem> allPlaylists = new ArrayList<>();
        String nextPageToken = null;
        try {
            do {
                request.setPageToken(nextPageToken); // 다음 페이지 토큰 설정
                PlaylistItemListResponse response = request.execute();
                allPlaylists.addAll(response.getItems());
                nextPageToken = response.getNextPageToken();
            } while (nextPageToken != null); // 더 이상 페이지가 없을 때까지 반복
        } catch (GoogleJsonResponseException e) { //
            log.warn("Google API error while retrieving playlists for user : {}", e.getDetails().getMessage());
            if (e.getStatusCode() == 403 || e.getStatusCode() == 404) {
                log.warn("Playlist fetch forbidden or not found. Possibly deleted or private.");
            }
            throw new IOException("the playlist is deleted");//            throw e;
        }

        return allPlaylists;
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