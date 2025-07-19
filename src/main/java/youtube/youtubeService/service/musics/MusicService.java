package youtube.youtubeService.service.musics;

import com.google.api.services.youtube.model.PlaylistItem;

import java.io.IOException;
import java.util.List;

public interface MusicService {

    void initiallyAddVideoDetails(String playlistId) throws IOException;
    List<PlaylistItem> updatePlaylist(String playlistId) throws IOException;
}
