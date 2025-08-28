package youtube.youtubeService.service.playlists;

import com.google.api.services.youtube.model.Playlist;
import youtube.youtubeService.domain.Playlists;
import youtube.youtubeService.dto.PlaylistRegisterRequestDto;
import youtube.youtubeService.dto.UserRegisterPlaylistsResponseDto;

import java.io.IOException;
import java.util.List;

public interface PlaylistService {

    List<Playlists> getPlaylistsByUserId(String userId);

    List<Playlist> getAllPlaylists(String userId) throws IOException;

    void removePlaylistsFromDB(String userId, List<String> deselectedPlaylistIds);

    void registerPlaylists(PlaylistRegisterRequestDto request);

    UserRegisterPlaylistsResponseDto userRegisterPlaylists(String userId) throws IOException;
}
