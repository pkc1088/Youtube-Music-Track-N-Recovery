package youtube.youtubeService.exceptionTest;

import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoListResponse;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;
import youtube.youtubeService.api.YoutubeApiClient;
import youtube.youtubeService.config.AuthenticatedYouTubeFactory;

import java.lang.reflect.Field;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Slf4j
@ExtendWith(MockitoExtension.class)
public class NoFetchVideoExceptionTest {

    private YoutubeApiClient youtubeApiClient;
    private AuthenticatedYouTubeFactory youTubeFactory;

    @Mock
    private YouTube youtubeMock;
    @Mock
    private YouTube.Videos videosMock;
    @Mock
    private YouTube.Videos.List videosListMock;
    @Mock
    private VideoListResponse videoListResponseMock;

    @BeforeEach
    void setUp() throws Exception {
        youtubeApiClient = new YoutubeApiClient(youtubeMock, youTubeFactory);
        Field youtubeField = YoutubeApiClient.class.getDeclaredField("youtube");
        youtubeField.setAccessible(true);
        youtubeField.set(youtubeApiClient, youtubeMock);

        when(youtubeMock.videos()).thenReturn(videosMock);
        when(videosMock.list(anyList())).thenReturn(videosListMock);

        // 핵심 부분
        when(videosListMock.execute()).thenReturn(videoListResponseMock);
//        when(videoListResponseMock.getItems()).thenReturn(null);
    }

    @Test
    void testExecuteReturnsNull() throws Exception {

        // 3) execute() 결과 null 반환하도록 설정
        when(videosListMock.execute()).thenReturn(null);

        // 실행
        Video result = youtubeApiClient.fetchSingleVideo("abc123");

        // 정상적으로 null fallback video 생성되는지 검증
        assertNotNull(result);
        assertTrue(result.getSnippet().getTitle().contains("FixMyPlaylist"));
    }

}
