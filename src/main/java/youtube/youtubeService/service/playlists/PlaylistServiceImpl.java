package youtube.youtubeService.service.playlists;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import youtube.youtubeService.domain.Playlists;
import youtube.youtubeService.repository.playlists.PlaylistRepository;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlaylistServiceImpl implements PlaylistService {

    private final PlaylistRepository playlistRepository;


    @Override
    public List<Playlists> findAllByUserIdWithUserFetch(String userId) {
        return playlistRepository.findAllByUserIdWithUserFetch(userId);
    }

    @Override
    public List<Playlists> findAllPlaylistsByUserIdsOrderByLastChecked(List<String> userIds) {
        return playlistRepository.findAllPlaylistsByUserIdsOrderByLastChecked(userIds);
    }

    @Override
    @Transactional
    public void updateLastCheckedAt(String playlistId) {
        playlistRepository.updateLastCheckedAt(playlistId, LocalDateTime.now());
    }
}
