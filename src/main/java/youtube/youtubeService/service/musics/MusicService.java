package youtube.youtubeService.service.musics;

import com.google.api.services.youtube.model.Video;
import youtube.youtubeService.domain.Music;
import youtube.youtubeService.domain.Playlists;
import youtube.youtubeService.dto.MusicSummaryDto;

import java.util.List;

public interface MusicService {

    //List<Music> findAllMusicByPlaylistId(String playlistId); 밑으로 대체
    //List<Music> findAllWithPlaylistByPlaylistIn(List<Playlists> playListsSet); 이것도 밑으로 대체됨
    List<MusicSummaryDto> findAllMusicSummaryByPlaylistIds(List<Playlists> playListsSet);
    void deleteById(Long pk);

    List<Music> getMusicListFromDBThruMusicId(String videoIdToDelete, String playlistId);

    void updateMusicWithReplacement(long pk, Music replacementMusic);

    void upsertMusic(Music music);

    void saveSingleVideo(Video video, Playlists playlist);

    void saveAllVideos(List<Video> legalVideos, Playlists playlist);

//    Music searchVideoToReplace(Music musicToSearch, Playlists playlist);
    Video searchVideoToReplace(Music musicToSearch);

    Music makeVideoToMusic(Video replacementVideo, Playlists playlist);
}
