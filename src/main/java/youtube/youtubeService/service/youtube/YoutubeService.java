package youtube.youtubeService.service.youtube;

import youtube.youtubeService.domain.Playlists;

import java.io.IOException;

public interface YoutubeService {

    void fileTrackAndRecover(String userId, Playlists playlist, String accessToken) throws IOException; // service version 1

}

