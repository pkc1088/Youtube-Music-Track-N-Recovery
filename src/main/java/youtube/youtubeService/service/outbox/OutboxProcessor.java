package youtube.youtubeService.service.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;
import youtube.youtubeService.api.YoutubeApiClient;
import youtube.youtubeService.domain.Outbox;

// 실 API 호출
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxProcessor {

    private final YoutubeApiClient youtubeApiClient;

    public boolean processOutbox(Outbox outbox) {

        boolean apiOperatedCheck = false;

        try {
            if (outbox.getActionType() == Outbox.ActionType.DELETE) {
                log.info("Processing DELETE for Outbox ID: {}", outbox.getId());
//                StopWatch transactionWatch = new StopWatch(); transactionWatch.start();
                apiOperatedCheck = youtubeApiClient.deleteFromActualPlaylist(outbox.getAccessToken(), outbox.getPlaylistItemId());
//                transactionWatch.stop(); log.info("[Test] deleteFromActualPlaylist Time: {} ms", transactionWatch.getTotalTimeMillis());
            } else if (outbox.getActionType() == Outbox.ActionType.ADD) {
                log.info("Processing ADD for Outbox ID: {}", outbox.getId());
//                StopWatch transactionWatch = new StopWatch(); transactionWatch.start();
                apiOperatedCheck = youtubeApiClient.addVideoToActualPlaylist(outbox.getAccessToken(), outbox.getPlaylistId(), outbox.getVideoId());
//                transactionWatch.stop(); log.info("[Test] addVideoToActualPlaylist Time: {} ms", transactionWatch.getTotalTimeMillis());
            }
        } catch (Exception e) {
            log.warn("API call failed for Outbox ID: {} - {}", outbox.getId(), e.getMessage());
        }

        return apiOperatedCheck;
    }
}

/** OGCODE BEFORE 1024
 @Slf4j
 @Service
 @RequiredArgsConstructor
 public class OutboxProcessor {

 private final YoutubeApiClient youtubeApiClient;

 public int processOutbox(Outbox outbox) {

 int apiOperatedVideoCount = -1;

 try {
 if (outbox.getActionType() == Outbox.ActionType.DELETE) {

 log.info("Processing DELETE for Outbox ID: {}", outbox.getId());
 apiOperatedVideoCount = youtubeApiClient.deleteFromActualPlaylist(outbox.getAccessToken(), outbox.getPlaylistId(), outbox.getVideoId());

 } else if (outbox.getActionType() == Outbox.ActionType.ADD) {

 log.info("Processing ADD for Outbox ID: {}", outbox.getId());
 apiOperatedVideoCount = youtubeApiClient.addVideoToActualPlaylist(outbox.getAccessToken(), outbox.getPlaylistId(), outbox.getVideoId());

 }
 } catch (Exception e) {
 log.warn("API call failed for Outbox ID: {} - {}", outbox.getId(), e.getMessage());
 log.warn("apiOperatedVideoCount: {}", apiOperatedVideoCount);
 }

 return apiOperatedVideoCount; // add 는 0, remove 는 1~n 개
 }
 }

 */