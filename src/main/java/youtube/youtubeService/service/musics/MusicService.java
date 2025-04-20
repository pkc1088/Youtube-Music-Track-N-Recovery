package youtube.youtubeService.service.musics;

import java.io.IOException;

public interface MusicService {

    void initiallyAddVideoDetails(String playlistId) throws IOException;
    void updatePlaylist(String playlistId) throws IOException;
}
