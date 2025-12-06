package youtube.youtubeService.service.playlists;

import youtube.youtubeService.domain.Playlists;
import java.util.List;

public interface PlaylistService {

    List<Playlists> findAllPlaylistsByUserIdsOrderByLastChecked(List<String> userIds);

    void updateLastCheckedAt(String playlistId);

    List<Playlists> findAllByUserIdWithUserFetch(String userId);
}
