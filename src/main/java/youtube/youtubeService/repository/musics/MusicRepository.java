package youtube.youtubeService.repository.musics;

import youtube.youtubeService.domain.Music;

import java.util.List;

public interface MusicRepository {

    List<Music> findAllMusicByPlaylistId(String playlistId);

    void upsertMusic(Music music);

    void deleteById(Long pk);

    List<Music> getMusicListFromDBThruMusicId(String videoId, String playlistId);

    void dBTrackAndRecoverPosition(String videoIdToDelete, Music videoToRecover, Long rowPk);

    void saveAll(List<Music> musicsToSave);

}
