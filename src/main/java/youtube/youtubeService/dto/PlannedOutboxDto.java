package youtube.youtubeService.dto;

import youtube.youtubeService.domain.Outbox;


public record PlannedOutboxDto(String userId, String playlistId, String accessToken, Outbox.ActionType actionType,
                               String videoId, String playlistItemIdsToDelete) {
}
