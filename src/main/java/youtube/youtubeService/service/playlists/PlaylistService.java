package youtube.youtubeService.service.playlists;

import youtube.youtubeService.domain.Playlists;
import youtube.youtubeService.dto.request.PlaylistRegisterRequestDto;
import youtube.youtubeService.dto.response.PlaylistRegisterResponseDto;
import youtube.youtubeService.dto.response.UserRegisterPlaylistsResponseDto;

import java.io.IOException;
import java.util.List;

public interface PlaylistService {

    List<Playlists> findAllPlaylistsByUserIds(List<String> userIds);

    void removePlaylistsFromDB(List<String> deselectedPlaylistIds);

    PlaylistRegisterResponseDto registerPlaylists(PlaylistRegisterRequestDto request);

    UserRegisterPlaylistsResponseDto userRegisterPlaylists(String userId) throws IOException;
}
