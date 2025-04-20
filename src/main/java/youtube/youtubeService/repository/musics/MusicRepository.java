package youtube.youtubeService.repository.musics;

import youtube.youtubeService.domain.Music;

import java.util.List;
import java.util.Optional;

public interface MusicRepository {
    List<Music> findAllMusicByPlaylistId(String playlistId);
    Optional<Music> getMusicFromDBThruMusicId(String videoIdToDelete, String playlistId);
    void addUpdatePlaylist(String playlistId, Music music);
    void deleteUpdatePlaylist(String playlistId, String videoId);
    void dBTrackAndRecover(String videoIdToDelete, Music videoToRecover, String playlistId);
}
