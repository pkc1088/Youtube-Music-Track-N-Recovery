package youtube.youtubeService.service.playlists;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import youtube.youtubeService.domain.Playlists;
import youtube.youtubeService.repository.playlists.PlaylistRepository;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlaylistServiceImpl implements PlaylistService {

    private final PlaylistRepository playlistRepository;


    @Override
    public List<Playlists> findAllPlaylistsByUserIds(List<String> userIds) {
        return playlistRepository.findAllPlaylistsByUserIds(userIds);
    }

}
