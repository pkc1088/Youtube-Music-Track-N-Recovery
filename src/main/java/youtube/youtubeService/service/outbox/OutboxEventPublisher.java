package youtube.youtubeService.service.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import youtube.youtubeService.domain.Outbox;
import youtube.youtubeService.dto.OutboxCreatedEventDto;

// 서비스 계층의 발행 책임
@Component
@RequiredArgsConstructor
public class OutboxEventPublisher {

    private final ApplicationEventPublisher publisher;

    public void publish(Outbox outbox) {
        publisher.publishEvent(new OutboxCreatedEventDto(outbox.getId()));
    }
}
