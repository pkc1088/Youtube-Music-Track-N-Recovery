package youtube.youtubeService.apiTest;

import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.SearchListResponse;
import com.google.api.services.youtube.model.SearchResult;
import com.google.api.services.youtube.model.Video;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import youtube.youtubeService.api.YoutubeApiClient;
import youtube.youtubeService.config.AuthenticatedYouTubeFactory;
import youtube.youtubeService.config.YouTubeConfig;
import youtube.youtubeService.domain.Music;
import youtube.youtubeService.domain.Playlists;
import youtube.youtubeService.dto.internal.MusicDetailsDto;
import youtube.youtubeService.dto.internal.VideoFilterResultPageDto;
import youtube.youtubeService.service.musics.MusicConverterHelper;
import youtube.youtubeService.service.musics.VideoRecommender;

import java.math.BigInteger;
import java.util.*;
import java.util.stream.*;


@Slf4j
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { YoutubeApiClient.class, YouTubeConfig.class, AuthenticatedYouTubeFactory.class, VideoRecommender.class })
@TestPropertySource("classpath:application.properties")
public class ApiSearchTest {

    @Autowired
    YouTube youtube;
    @Autowired
    private YoutubeApiClient youtubeApiClient;
    @Value("${youtube.api.key}")
    private String apiKey;
    @Autowired
    VideoRecommender videoRecommender;

    static String query;
    static String videoId;
    static String countryCode;
    static String videoTitle;
    static String videoUploader;
    static MusicDetailsDto original;

    @BeforeAll
    static void setUp() {
        query = "Rainbow Eyes-Rainbow";
        videoId = "WO7TYTt0smY";
        videoTitle = "Rainbow Eyes";
        videoUploader = "Ritchie Balckmore's Rainbow";
        countryCode = "KR";
        original = new MusicDetailsDto(123L, videoId, videoTitle, videoUploader, 442, "", "", "playlistId");
    }

    public List<SearchResult> searchMultipleFromYoutube(String query, String countryCode, long maxResult) {
        try {
            YouTube.Search.List search = youtube.search().list(Collections.singletonList("id, snippet"));
            search.setKey(apiKey);
            search.setQ(query);
            search.setRegionCode(countryCode);
            search.setMaxResults(maxResult);
            search.setType(Collections.singletonList("video"));


            log.info("Request URL: {}", search.buildHttpRequestUrl().toString());

            SearchListResponse searchResponse = search.execute();

            log.info("searchResponse.getItems().size(): {}", searchResponse.getItems().size());
            log.info("searchResponse.getPageInfo().getResultsPerPage(): {}", searchResponse.getPageInfo().getResultsPerPage());

            if (searchResponse != null && searchResponse.getItems() != null && !searchResponse.getItems().isEmpty()) {
                return searchResponse.getItems();
            }
            log.warn("YouTube API Search for query '{}' yielded no results. Returning placeholder.", query);
            return null;

        } catch (Exception e) {
            log.warn("YouTube API Search for query '{}' failed: {}. Returning placeholder.", query, e.getMessage());
            return null;
        }
    }


    @Test
    public void searchResultTest() {

        long maxResult = 10L;
        List<SearchResult> searchResults = searchMultipleFromYoutube(query, countryCode, maxResult);
        log.info("searchResults : {}", searchResults.size());

        List<String> videoIdsToSearch = searchResults.stream().map(result -> result.getId().getVideoId()).toList();
        log.info("videoIdsToSearch : {}", videoIdsToSearch.size());


        VideoFilterResultPageDto videoDetails = youtubeApiClient.fetchVideoPage(videoIdsToSearch, countryCode);
        List<Video> legalVideos = videoDetails.legalVideos();
        List<Video> unlistedCountryVideos = videoDetails.unlistedCountryVideos();
        log.info("legalVideos : {}, unlistedCountryVideos : {}", legalVideos.size(), unlistedCountryVideos.size());

        for (Video video : legalVideos) {
            printVideoDetails(video);
            log.info("==========================");
        }

        for (Video video : unlistedCountryVideos) {
            printVideoDetails(video);
            log.info("==========================");
        }


        Video bestVideo = videoRecommender.recommendBestMatch(original, legalVideos);
        System.out.println("[Best Video Id]: " + bestVideo.getId());

    }

    @Test
    public void fetchSingleVideoTest() {

        Video video = youtubeApiClient.fetchSingleVideo(videoId);
        printVideoDetails(video);

    }

    private void printVideoDetails(Video video) {

        String id = video.getId();
        String title = video.getSnippet().getTitle();
        String channelId = video.getSnippet().getChannelId();
        String channelTitle = video.getSnippet().getChannelTitle();
        String uploadStatus = video.getStatus().getUploadStatus();
        String privacyStatus = video.getStatus().getPrivacyStatus();
        String allowedCountry = null;
        String blockedCountry = null;
        String duration = video.getContentDetails().getDuration(); // PT#M#S    PT#H#M#S
        String viewCount = String.valueOf(video.getStatistics().getViewCount());
        String likeCount = String.valueOf(video.getStatistics().getLikeCount());
        String commentCount = String.valueOf(video.getStatistics().getCommentCount());

        if (video.getContentDetails().getRegionRestriction() != null && video.getContentDetails().getRegionRestriction().getAllowed() != null) {
            allowedCountry = String.valueOf(video.getContentDetails().getRegionRestriction().getAllowed());
        }
        if (video.getContentDetails().getRegionRestriction() != null && video.getContentDetails().getRegionRestriction().getBlocked() != null) {
            blockedCountry = String.valueOf(video.getContentDetails().getRegionRestriction().getBlocked());
        }

        log.info("id: {}", id);
        log.info("title: {}", title);
        log.info("channelId: {}", channelId);
        log.info("channelTitle: {}", channelTitle);
        log.info("uploadStatus: {}", uploadStatus);
        log.info("privacyStatus: {}", privacyStatus);
        log.info("allowedCountry: {}", allowedCountry);
        log.info("blockedCountry: {}", blockedCountry);
        log.info("duration: {}", duration);
        log.info("viewCount: {}", viewCount);
        log.info("likeCount: {}", likeCount);
        log.info("commentCount: {}", commentCount);

    }

}
