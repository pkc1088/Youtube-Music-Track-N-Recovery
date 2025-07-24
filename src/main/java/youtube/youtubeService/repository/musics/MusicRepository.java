package youtube.youtubeService.repository.musics;

import youtube.youtubeService.domain.Music;

import java.util.List;
import java.util.Optional;

public interface MusicRepository {
    List<Music> findAllMusicByPlaylistId(String playlistId);
    void addUpdatePlaylist(String playlistId, Music music);
    void deleteUpdatePlaylist(String playlistId, String videoId);
    void deleteById(Long pk);
    List<Music> getMusicListFromDBThruMusicId(String videoId, String playlistId);
    void dBTrackAndRecoverPosition(String videoIdToDelete, Music videoToRecover, String playlistId, Long rowPk);

    // void dBTrackAndRecover(String videoIdToDelete, Music videoToRecover, String playlistId);
    // Optional<Music> getMusicFromDBThruMusicId(String videoIdToDelete, String playlistId);
}
