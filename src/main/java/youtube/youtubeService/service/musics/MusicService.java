package youtube.youtubeService.service.musics;

import com.google.api.services.youtube.model.Video;
import youtube.youtubeService.domain.Music;
import youtube.youtubeService.domain.Playlists;
import youtube.youtubeService.dto.MusicSummaryDto;

import java.util.List;

public interface MusicService {

    List<MusicSummaryDto> findAllMusicSummaryByPlaylistIds(List<Playlists> playListsSet);
    void deleteById(Long pk);

    void deleteAllByIdInBatch(List<Long> ids);

    List<Music> getMusicListFromDBThruMusicId(String videoIdToDelete, String playlistId);

    void updateMusicWithReplacement(long pk, Music replacementMusic);

    void upsertMusic(Music music);

    void saveAllVideos(List<Video> legalVideos, Playlists playlist);

    Video searchVideoToReplace(Music musicToSearch);

    Music makeVideoToMusic(Video replacementVideo, Playlists playlist);
}

//    List<Music> findAllMusicByPlaylistId(String playlistId); 밑으로 대체
//    List<Music> findAllWithPlaylistByPlaylistIn(List<Playlists> playListsSet); 이것도 밑으로 대체됨
//    void saveSingleVideo(Video video, Playlists playlist);
//    Music searchVideoToReplace(Music musicToSearch, Playlists playlist);