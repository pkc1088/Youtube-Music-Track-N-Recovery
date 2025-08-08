package youtube.youtubeService.repository.playlists;

import youtube.youtubeService.domain.Playlists;

import java.util.List;

public interface PlaylistRepository {

    void save(Playlists playlist);

    Playlists findByPlaylistId(String playlistId);

    List<Playlists> findAllPlaylistsByUserId(String userId);

    void deletePlaylistByPlaylistId(String playlistId);
}
