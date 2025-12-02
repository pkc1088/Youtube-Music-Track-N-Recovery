package youtube.youtubeService.repository.playlists;

import youtube.youtubeService.domain.Playlists;

import java.time.LocalDateTime;
import java.util.List;

public interface PlaylistRepository {

    Playlists findByPlaylistId(String playlistId);

    void deleteAllByPlaylistIdsIn(List<String> deselectedPlaylistIds);

    List<Playlists> findAllPlaylistsByUserIdsOrderByLastChecked(List<String> userIds);

    List<Playlists> findAllByUserIdWithUserFetch(String userId);

    void updateLastCheckedAt(String playlistId, LocalDateTime now);
}
