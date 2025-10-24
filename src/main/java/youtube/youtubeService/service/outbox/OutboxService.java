package youtube.youtubeService.service.outbox;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import youtube.youtubeService.domain.Outbox;
import youtube.youtubeService.repository.OutboxRepository;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxRepository outboxRepository;
    private final OutboxEventPublisher outboxEventPublisher;

    public void outboxInsert(Outbox.ActionType actionType, String accessToken, String userId, String playlistId, String videoId, String playlistItemIdToDelete) {
        Outbox outbox = new Outbox();
        outbox.setActionType(actionType);
        outbox.setAccessToken(accessToken);
        outbox.setUserId(userId);
        outbox.setPlaylistId(playlistId);
        outbox.setVideoId(videoId);
        outbox.setPlaylistItemId(playlistItemIdToDelete);
        outboxRepository.save(outbox);
        outboxEventPublisher.publish(outbox);
    }
}
