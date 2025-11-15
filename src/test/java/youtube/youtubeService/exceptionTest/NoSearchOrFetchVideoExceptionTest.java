package youtube.youtubeService.exceptionTest;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import youtube.youtubeService.api.YoutubeApiClient;
import youtube.youtubeService.domain.ActionLog;
import youtube.youtubeService.service.youtube.RecoverOrchestrationService;
import java.util.Optional;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@Slf4j
@SpringBootTest
public class NoSearchOrFetchVideoExceptionTest {

    @Autowired
    private RecoverOrchestrationService recoverOrchestrationService;

    @MockitoSpyBean
    private YoutubeApiClient youtubeApiClient;

    @BeforeEach
    void setUp() {

        try {
            doReturn(null).when(youtubeApiClient).searchFromYoutube(anyString());

        } catch (Exception e) {

        }
    }

    @Test
    void testEachRecover() {
        recoverOrchestrationService.allPlaylistsRecoveryOfAllUsers();
    }
}
