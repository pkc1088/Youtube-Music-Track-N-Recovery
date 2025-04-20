package youtube.youtubeService.repository.playlists;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import youtube.youtubeService.domain.Playlists;

import java.util.List;

@Repository
@Transactional
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
        repository.deleteById(playlistId);
    }
}
