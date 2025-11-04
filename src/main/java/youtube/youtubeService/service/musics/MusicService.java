package youtube.youtubeService.service.musics;

import com.google.api.services.youtube.model.Video;
import youtube.youtubeService.domain.Music;
import youtube.youtubeService.domain.Playlists;

import java.util.List;

public interface MusicService {

    List<Music> findAllMusicByPlaylistId(String playlistId);

    void deleteById(Long pk);

    List<Music> getMusicListFromDBThruMusicId(String videoIdToDelete, String playlistId);

    void updateMusicWithReplacement(String videoIdToDelete, Music replacementMusic, long pk);

    void upsertMusic(Music music);

    void saveSingleVideo(Video video, Playlists playlist);

    void saveAllVideos(List<Video> legalVideos, Playlists playlist);

//    Music searchVideoToReplace(Music musicToSearch, Playlists playlist);
    Video searchVideoToReplace(Music musicToSearch, Playlists playlist);

    Music makeVideoToMusic(Video replacementVideo, Playlists playlist);
}
