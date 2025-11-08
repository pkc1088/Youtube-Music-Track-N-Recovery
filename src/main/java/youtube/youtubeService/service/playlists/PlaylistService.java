package youtube.youtubeService.service.playlists;

import youtube.youtubeService.domain.Music;
import youtube.youtubeService.domain.Playlists;
import youtube.youtubeService.dto.*;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface PlaylistService {

    List<Playlists> findAllPlaylistsByUserId(String userId);

    List<Playlists> findAllPlaylistsByUserIds(List<String> userIds);
    Map<String, List<String>> updatePlaylist(String userId, String countryCode, Playlists playlist, List<MusicSummaryDto> pureDbMusicList) throws IOException;

    void removePlaylistsFromDB(String userId, List<String> deselectedPlaylistIds);

//    void registerPlaylists(PlaylistRegisterRequestDto request);
    PlaylistRegistrationResultDto registerPlaylists(PlaylistRegisterRequestDto request);

    UserRegisterPlaylistsResponseDto userRegisterPlaylists(String userId) throws IOException;
}
