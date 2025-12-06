package youtube.youtubeService.dto.internal;

import java.time.LocalDateTime;

public record PlaylistCacheDto(
   String playlistId,
   String playlistTitle,
   LocalDateTime lastCheckedAt
) {}
