package youtube.youtubeService.dto.internal;

public record MusicDetailsDto (
        Long id,
        String videoId,
        String videoTitle,
        String videoUploader,
        String videoDescription,
        String videoTags,
        String playlistId
) {}