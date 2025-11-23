package youtube.youtubeService.repository.playlists;

import org.springframework.data.repository.query.Param;
import youtube.youtubeService.domain.Playlists;

import java.util.List;

public interface PlaylistRepository {

    Playlists findByPlaylistId(String playlistId);

    void deleteAllByPlaylistIdsIn(List<String> deselectedPlaylistIds);

    List<Playlists> findAllPlaylistsByUserIds(List<String> userIds);

    List<Playlists> findAllByUserIdWithUserFetch(String userId);
}
