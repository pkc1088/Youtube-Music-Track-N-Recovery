package youtube.youtubeService.dto.internal;

public record MusicSummaryDto(
        Long id,
        String videoId,
        String videoTitle,
        String videoUploader,
        String playlistId
) {}