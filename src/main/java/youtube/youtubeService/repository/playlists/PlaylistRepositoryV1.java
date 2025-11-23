package youtube.youtubeService.repository.playlists;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import youtube.youtubeService.domain.Playlists;
import youtube.youtubeService.domain.Users;

import java.util.List;

@Repository
@Slf4j
@RequiredArgsConstructor
public class PlaylistRepositoryV1 implements PlaylistRepository {

    private final SdjPlaylistRepository repository;

    @Override
    public Playlists findByPlaylistId(String playlistId) {
        return repository.findByPlaylistId(playlistId);
    }

    @Override
    public void deleteAllByPlaylistIdsIn(List<String> deselectedPlaylistIds) {
        repository.deleteAllByIdInBatch(deselectedPlaylistIds); // repository.deleteAllByPlaylistIdsIn(deselectedPlaylistIds);
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
