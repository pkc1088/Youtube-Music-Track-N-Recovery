package youtube.youtubeService.service.youtube;

import java.io.IOException;

public interface YoutubeService {

    void fileTrackAndRecover(String userId, String playlistId, String accessToken) throws IOException; // service version 1

}

