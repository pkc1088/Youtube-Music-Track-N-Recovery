package youtube.youtubeService.dto;

// 	발행-구독 메시지 DTO
public class OutboxCreatedEventDto {
    private final Long outboxId;
    private final String userId;
    private final String playlistId;

    public OutboxCreatedEventDto(Long outboxId, String userId, String playlistId) {
        this.outboxId = outboxId;
        this.userId = userId;
        this.playlistId = playlistId;
    }

    public Long getOutboxId() {
        return outboxId;
    }

    public String getUserId() { return userId; }

    public String getPlaylistId() { return playlistId; }
}
