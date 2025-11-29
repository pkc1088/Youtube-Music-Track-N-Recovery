package youtube.youtubeService.dto.internal;

import youtube.youtubeService.domain.Music;
import youtube.youtubeService.domain.Playlists;

import java.util.List;

public record FixedDataForRegistrationDto(
        Playlists playlist,
        List<Music> musicList
) {}
