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
                apiOperatedCheck = youtubeApiClient.deleteFromActualPlaylist(outbox.getAccessToken(), outbox.getPlaylistItemId());
            } else if (outbox.getActionType() == Outbox.ActionType.ADD) {
                log.info("Processing ADD for Outbox ID: {}", outbox.getId());
                apiOperatedCheck = youtubeApiClient.addVideoToActualPlaylist(outbox.getAccessToken(), outbox.getPlaylistId(), outbox.getVideoId());
            }
        } catch (Exception e) {
            log.warn("API call failed for Outbox ID: {} - {}", outbox.getId(), e.getMessage());
        }

        return apiOperatedCheck;
    }
}
