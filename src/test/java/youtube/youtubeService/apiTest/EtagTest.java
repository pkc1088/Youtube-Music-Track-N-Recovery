package youtube.youtubeService.apiTest;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.youtube.YouTube;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import youtube.youtubeService.api.YoutubeApiClient;
import youtube.youtubeService.config.AuthenticatedYouTubeFactory;
import youtube.youtubeService.config.YouTubeConfig;

import java.io.IOException;
import java.util.Collections;

@Slf4j
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { YoutubeApiClient.class, YouTubeConfig.class, AuthenticatedYouTubeFactory.class })
@TestPropertySource("classpath:application.properties")
public class EtagTest {

    @Autowired
    YouTube youtube;
    @Autowired
    private YoutubeApiClient youtubeApiClient;
    @Value("${youtube.api.key}")
    private String apiKey;

    @Test
    void etagTest() {
        String playlistId = "PLucanKTmEQwgpIx6xG3UyOJoyoLiTYYSU";
        String knownEtag = "n-JgAnhw5WuXTZ1BqYNwurjbdkg";
        String newEtag = fetchEtag(playlistId, knownEtag);

        System.out.println("newEtag = " + newEtag);
    }


    public String fetchEtag(String playlistId, String knownEtag) {
        try {
            YouTube.PlaylistItems.List request = youtube.playlistItems().list(Collections.singletonList("snippet, id"));
            request.setKey(apiKey);
            request.setPlaylistId(playlistId);
            request.setMaxResults(50L);

            return request.execute().getEtag();
        } catch (GoogleJsonResponseException e) {
            if (e.getStatusCode() == 403 || e.getStatusCode() == 404) {
                log.warn("Playlist fetch forbidden or not found. Possibly deleted or private.");
            }
            throw new RuntimeException();
        } catch (IOException e) {
            throw new RuntimeException();
        }
    }

}
