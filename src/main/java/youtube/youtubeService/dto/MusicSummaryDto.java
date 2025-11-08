package youtube.youtubeService.dto;

public record MusicSummaryDto(
        Long id,
        String videoId,
        String videoTitle,
        String videoUploader,
        String playlistId
) {}