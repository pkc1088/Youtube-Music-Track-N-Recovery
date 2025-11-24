package youtube.youtubeService.api;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import youtube.youtubeService.config.AuthenticatedYouTubeFactory;
import youtube.youtubeService.dto.internal.QuotaApiPlaylistsPageDto;
import youtube.youtubeService.dto.internal.QuotaPlaylistItemPageDto;
import youtube.youtubeService.dto.internal.VideoFilterResultPageDto;
import youtube.youtubeService.exception.ChannelNotFoundException;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class YoutubeApiClient {
    @Value("${youtube.api.key}")
    private String apiKey;
    private final YouTube youtube;
    private final AuthenticatedYouTubeFactory youTubeFactory;
    private static final String FALLBACK_VIDEO_ID = "t3M2oxdoWuI";

    public Video fetchSingleVideo(String videoId) {
        try {
            YouTube.Videos.List request = youtube.videos().list(Collections.singletonList("snippet, id, status, contentDetails"));
            request.setKey(apiKey);
            request.setId(Collections.singletonList(videoId));
            VideoListResponse response = request.execute();

            if (response != null && response.getItems() != null && !response.getItems().isEmpty()) {
                return response.getItems().get(0);
            }

            if (response == null || response.getItems() == null) {
                log.warn("VideoListResponse or getItems() was null for {}. Returning fallback.", videoId);
            } else {
                log.warn("Video not found by ID (items list was empty): {}. Returning fallback.", videoId);
            }

        } catch (IOException e) {
            log.warn("Failed to fetch videoId {}. Returning Fall Back Video.", videoId);
        }

        return createPlaceholderVideo("This video was supposed to be [" + videoId + "]");
    }

    public String fetchChannelId(String accessToken) throws IOException, GeneralSecurityException, ChannelNotFoundException {
        try {
            YouTube youtube = youTubeFactory.create(accessToken);

            ChannelListResponse response = youtube.channels().list(Collections.singletonList("snippet")).setMine(true).execute();   // 현재 인증된 사용자의 채널 정보 조회

            List<Channel> channels = response.getItems();

            if (channels == null || channels.isEmpty()) {
                throw new ChannelNotFoundException("[channel not found]");
            }

            return channels.get(0).getId();

        } catch (GoogleJsonResponseException e) {
            log.warn("Google API error while retrieving fetchChannelId : {}", e.getDetails().getMessage());
            throw e;
        }
    }

    /**
     * 1개의 페이지 단위로 주고 받기.
     */
    public QuotaApiPlaylistsPageDto fetchPlaylistPage(String channelId, String pageToken) throws IOException {
        try {
            YouTube.Playlists.List request = youtube.playlists().list(Collections.singletonList("snippet, id, contentDetails"));
            request.setKey(apiKey);
            request.setChannelId(channelId);
            request.setMaxResults(50L);
            request.setPageToken(pageToken);

            PlaylistListResponse response = request.execute();

            List<Playlist> PlaylistsBatch = new ArrayList<>(response.getItems());
            String nextPageToken = response.getNextPageToken();

            return new QuotaApiPlaylistsPageDto(PlaylistsBatch, nextPageToken);
        } catch (GoogleJsonResponseException e) {
            log.warn("Google API error while retrieving fetchPlaylistPage : {}", e.getDetails().getMessage());
            throw e;
        }

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
        try {
            YouTube.PlaylistItems.List request = youtube.playlistItems().list(Collections.singletonList("snippet, id")); // , status
            request.setKey(apiKey);
            request.setPlaylistId(playlistId);
            request.setMaxResults(50L);
            request.setPageToken(pageToken);

            PlaylistItemListResponse response = request.execute();
            List<PlaylistItem> items = response.getItems();
            String nextPageToken = response.getNextPageToken();

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
        try {
            YouTube youtube = youTubeFactory.create(accessToken);

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
            String reason = "Unknown error";
            if (e.getDetails() != null && e.getDetails().getErrors() != null && !e.getDetails().getErrors().isEmpty()) {
                reason = e.getDetails().getErrors().get(0).getReason();
            }

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

        } catch (IOException e) {
            log.warn("Unexpected exception during video add:", e);
            return false;
        }

        return true;
    }

    public boolean deleteFromActualPlaylist(String accessToken, String playlistItemId) {
        try {
            YouTube youtube = youTubeFactory.create(accessToken);

            youtube.playlistItems().delete(playlistItemId).execute();
            log.info("[completely deleted PlaylistItemId]: {}", playlistItemId);

        } catch (GoogleJsonResponseException e) {
            int statusCode = e.getStatusCode();
            String reason = "Unknown error";
            if (e.getDetails() != null && e.getDetails().getErrors() != null && !e.getDetails().getErrors().isEmpty()) {
                reason = e.getDetails().getErrors().get(0).getReason();
            }

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

        } catch (IOException e) {
            log.warn("Unexpected exception during video delete:", e);
            return false;
        }

        return true;
    }

    public SearchResult searchFromYoutube(String query) {
        try {
            YouTube.Search.List search = youtube.search().list(Collections.singletonList("id, snippet"));
            search.setKey(apiKey);
            search.setQ(query);
            search.setType(Collections.singletonList("video")); // 비디오만 검색 (추가사항)

            SearchListResponse searchResponse = search.execute();
            // 1. 리스트가 null 이 아니고 비어있지 않은지 확인
            if (searchResponse != null && searchResponse.getItems() != null && !searchResponse.getItems().isEmpty()) {
                return searchResponse.getItems().get(0);
            }
            // 2. (실패 1) 검색 결과가 없는 경우
            log.warn("YouTube API Search for query '{}' yielded no results. Returning placeholder.", query);
            return null;

        } catch (Exception e) {
            // 3. (실패 2) API 호출 자체를 실패한 경우
            log.warn("YouTube API Search for query '{}' failed: {}. Returning placeholder.", query, e.getMessage());
            return null;
        }
    }

    public Video createPlaceholderVideo(String comment) {
        Video placeholder = new Video();
        placeholder.setId(FALLBACK_VIDEO_ID);
        placeholder.setKind("youtube#video");

        VideoSnippet snippet = new VideoSnippet();
        snippet.setTitle("FixMyPlaylist - 임시 대체 영상");
        snippet.setChannelTitle("pkc1088");
        snippet.setDescription(comment);
        placeholder.setSnippet(snippet);

        return placeholder;
    }

    public void updateVideoPrivacyStatus(String accessToken, String videoId, String changedStatus) {
        try {
            YouTube youtube = youTubeFactory.create(accessToken);

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
