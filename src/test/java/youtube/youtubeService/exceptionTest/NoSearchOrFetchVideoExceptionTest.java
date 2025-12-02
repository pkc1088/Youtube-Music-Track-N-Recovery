package youtube.youtubeService.exceptionTest;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import youtube.youtubeService.api.YoutubeApiClient;
import youtube.youtubeService.service.youtube.RecoveryOrchestratorService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;

@Slf4j
@SpringBootTest
public class NoSearchOrFetchVideoExceptionTest {

    @Autowired
    private RecoveryOrchestratorService recoveryOrchestratorService;

    @MockitoSpyBean
    private YoutubeApiClient youtubeApiClient;

    @BeforeEach
    void setUp() {

        try {
            doReturn(null).when(youtubeApiClient).searchFromYoutube(anyString(), "KR");

        } catch (Exception e) {

        }
    }

    @Test
    void testEachRecover() {
        recoveryOrchestratorService.allPlaylistsRecoveryOfAllUsers();
    }
}
