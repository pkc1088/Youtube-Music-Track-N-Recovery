package youtube.youtubeService.dto;

import youtube.youtubeService.domain.Music;

public record PlannedReplacementDto(long pk, Music replacementMusic, Music backupMusic) {
}
