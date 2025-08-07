package youtube.youtubeService.service.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import youtube.youtubeService.api.YoutubeApiClient;
import youtube.youtubeService.domain.Outbox;

// 실 API 호출
@Slf4j
//@Component
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