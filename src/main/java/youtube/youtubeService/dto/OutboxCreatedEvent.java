package youtube.youtubeService.dto;

// 	발행-구독 메시지 DTO
public class OutboxCreatedEvent {
    private final Long outboxId;

    public OutboxCreatedEvent(Long outboxId) {
        this.outboxId = outboxId;
    }

    public Long getOutboxId() {
        return outboxId;
    }
}
