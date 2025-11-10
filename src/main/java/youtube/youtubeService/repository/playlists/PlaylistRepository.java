package youtube.youtubeService.repository.playlists;

import org.springframework.data.repository.query.Param;
import youtube.youtubeService.domain.Playlists;

import java.util.List;

public interface PlaylistRepository {

    // Playlists save(Playlists playlist); //void save(Playlists playlist);

    Playlists findByPlaylistId(String playlistId);

    // List<Playlists> findAllPlaylistsByUserId(String userId);

    // void deletePlaylistByPlaylistId(String playlistId); 밑으로 대체
    void deleteAllByPlaylistIdsIn(List<String> deselectedPlaylistIds);
    List<Playlists> findAllPlaylistsByUserIds(List<String> userIds);

    List<Playlists> findAllByUserIdWithUserFetch(String userId);
}
