package youtube.youtubeService.service.playlists;

import youtube.youtubeService.domain.Playlists;
import youtube.youtubeService.dto.IllegalVideosAndPaginationDto;
import youtube.youtubeService.dto.PlaylistRegisterRequestDto;
import youtube.youtubeService.dto.PlaylistRegistrationResultDto;
import youtube.youtubeService.dto.UserRegisterPlaylistsResponseDto;

import java.io.IOException;
import java.util.List;

public interface PlaylistService {

    List<Playlists> findAllPlaylistsByUserId(String userId);

    IllegalVideosAndPaginationDto updatePlaylist(String userId, String countryCode, Playlists playlist) throws IOException;
    void removePlaylistsFromDB(String userId, List<String> deselectedPlaylistIds);

//    void registerPlaylists(PlaylistRegisterRequestDto request);
    PlaylistRegistrationResultDto registerPlaylists(PlaylistRegisterRequestDto request);

    UserRegisterPlaylistsResponseDto userRegisterPlaylists(String userId) throws IOException;
}
