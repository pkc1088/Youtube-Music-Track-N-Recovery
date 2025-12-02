package youtube.youtubeService.dto.internal;

public record MusicSummaryDto(
        Long id,
        String videoId,
        String playlistId
) {}