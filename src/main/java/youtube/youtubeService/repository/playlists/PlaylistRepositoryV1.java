package youtube.youtubeService.repository.playlists;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import youtube.youtubeService.domain.Playlists;
import youtube.youtubeService.domain.Users;

import java.util.List;

@Repository
//@Transactional
@Slf4j
@RequiredArgsConstructor
public class PlaylistRepositoryV1 implements PlaylistRepository {

    private final SdjPlaylistRepository repository;


    @Override
    public void save(Playlists playlist) {
        repository.save(playlist);
    }

    @Override
    public Playlists findByPlaylistId(String playlistId) {
        return repository.findByPlaylistId(playlistId);
    }

    @Override
    public List<Playlists> findAllPlaylistsByUserId(String userId) {
        return repository.findByUser_UserId(userId);
    }

    @Override
    public void deletePlaylistByPlaylistId(String playlistId) {
//        log.info("deletePlaylistByPlaylistId txActive : {} : {}", TransactionSynchronizationManager.isActualTransactionActive(), TransactionSynchronizationManager.getCurrentTransactionName());
        repository.deleteById(playlistId);
    }
}
