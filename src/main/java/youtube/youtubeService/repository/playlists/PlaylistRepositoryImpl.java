package youtube.youtubeService.repository.playlists;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import youtube.youtubeService.domain.Playlists;
import java.time.LocalDateTime;
import java.util.List;

@Repository
@Slf4j
@RequiredArgsConstructor
public class PlaylistRepositoryImpl implements PlaylistRepository {

    private final SdjPlaylistRepository repository;


    @Override
    public void deleteAllByPlaylistIdsIn(List<String> deselectedPlaylistIds) {
        repository.deleteAllByIdInBatch(deselectedPlaylistIds);
    }

    @Override
    public List<Playlists> findAllPlaylistsByUserIdsOrderByLastChecked(List<String> userIds) {
        return repository.findAllPlaylistsByUserIdsOrderByLastChecked(userIds);
    }

    @Override
    public List<Playlists> findAllByUserIdWithUserFetch(String userId) {
        return repository.findAllByUserIdWithUserFetch(userId);
    }

    @Override
    public void updateLastCheckedAt(String playlistId, LocalDateTime now) {
        repository.updateLastCheckedAt(playlistId, now);
    }
}
