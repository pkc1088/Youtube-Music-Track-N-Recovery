package youtube.youtubeService.service.outbox;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import youtube.youtubeService.domain.Outbox;
import youtube.youtubeService.repository.OutboxRepository;

@Service
@RequiredArgsConstructor
public class OutboxStatusUpdater {

    private final OutboxRepository outboxRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateOutboxStatus(Long outboxId, Outbox.Status status) {

        Outbox outbox = outboxRepository.findOutboxById(outboxId).orElseThrow();
        outbox.updateStatus(status);
    }
}