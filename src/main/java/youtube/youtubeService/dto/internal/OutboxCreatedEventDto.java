package youtube.youtubeService.dto.internal;

// 	발행-구독 메시지 DTO
public record OutboxCreatedEventDto(Long outboxId, String userId, String playlistId) {
}
