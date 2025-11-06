package youtube.youtubeService;

import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;
import youtube.youtubeService.domain.Playlists;
import youtube.youtubeService.service.youtube.YoutubeService;

@Slf4j
@Service
public class RecoverTestHelper {

    @Autowired
    private YoutubeService youtubeService;

    @Transactional
    public long measureOutboxTransactionTimeWithAutoStatusUpdateWith100Times(long i, String accessTokenForRecoverUser, Playlists playlist) {

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        //youtubeService.fileTrackAndRecover("112735690496635663877", playlist, "KR", accessTokenForRecoverUser);
        stopWatch.stop();
        long transactionTime = stopWatch.getTotalTimeMillis();
        log.info("[Test - {}] Transaction Time: {} ms", i, transactionTime);

        return transactionTime;
    }
}
