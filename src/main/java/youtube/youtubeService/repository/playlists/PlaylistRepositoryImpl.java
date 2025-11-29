package youtube.youtubeService.repository.playlists;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import youtube.youtubeService.domain.Playlists;

import java.util.List;

@Repository
@Slf4j
@RequiredArgsConstructor
public class PlaylistRepositoryImpl implements PlaylistRepository {

    private final SdjPlaylistRepository repository;

    @Override
    public Playlists findByPlaylistId(String playlistId) {
        return repository.findByPlaylistId(playlistId);
    }

    @Override
    public void deleteAllByPlaylistIdsIn(List<String> deselectedPlaylistIds) {
        repository.deleteAllByIdInBatch(deselectedPlaylistIds);
    }

    @Override
    public List<Playlists> findAllPlaylistsByUserIds(List<String> userIds) {
        return repository.findAllByUserIdsWithUser(userIds);
    }
    @Override
    public List<Playlists> findAllByUserIdWithUserFetch(String userId) {
        return repository.findAllByUserIdWithUserFetch(userId);
    }

}
