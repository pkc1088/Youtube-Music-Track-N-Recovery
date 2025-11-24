package youtube.youtubeService.config;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.services.youtube.YouTube;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthenticatedYouTubeFactory {

    private final HttpTransport transport;
    private final JsonFactory jsonFactory;

    /**
     * 쓰기 전용
     */
    public YouTube create(String accessToken) {
        Credential credential = new GoogleCredential().setAccessToken(accessToken);
        return new YouTube.Builder(transport, jsonFactory, credential)
                .setApplicationName("youtube-recovery-service-write")
                .build();
    }
}
