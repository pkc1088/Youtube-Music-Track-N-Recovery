package youtube.youtubeService.service.youtube;

import youtube.youtubeService.domain.Music;
import youtube.youtubeService.domain.Playlists;
import youtube.youtubeService.dto.MusicSummaryDto;

import java.util.List;

public interface YoutubeService {

//    void fileTrackAndRecover(String userId, Playlists playlist, String countryCode, String accessToken, List<Music> preFetchedMusicList);
    void fileTrackAndRecover(String userId, Playlists playlist, String countryCode, String accessToken, List<MusicSummaryDto> preFetchedMusicList);

}

