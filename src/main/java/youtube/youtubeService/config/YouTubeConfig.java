package youtube.youtubeService.config;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.youtube.YouTube;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import java.io.IOException;
import java.security.GeneralSecurityException;

@Slf4j
@Configuration
public class YouTubeConfig {

    @Bean
    public HttpTransport httpTransport() throws GeneralSecurityException, IOException {
        return GoogleNetHttpTransport.newTrustedTransport();
    }

    @Bean
    public JsonFactory jsonFactory() {
        return GsonFactory.getDefaultInstance();
    }

    /**
     * 조회 전용
     * */
    @Bean
    @Primary
    public YouTube readOnlyYouTubeClient(HttpTransport transport, JsonFactory jsonFactory) {
        try {
            return new YouTube.Builder(transport, jsonFactory, request -> {})
                    .setApplicationName("youtube-recovery-service")
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create YouTube client", e);
        }
    }

}