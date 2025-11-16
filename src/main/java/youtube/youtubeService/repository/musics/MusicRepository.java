package youtube.youtubeService.repository.musics;

import youtube.youtubeService.domain.Music;
import youtube.youtubeService.domain.Playlists;
import youtube.youtubeService.dto.MusicSummaryDto;

import java.util.List;
import java.util.Optional;

public interface MusicRepository {

    List<MusicSummaryDto> findAllMusicSummaryByPlaylistIds(List<Playlists> playListsSet);

    void upsertMusic(Music music);

    void deleteById(Long pk);

    void deleteAllByIdInBatch(List<Long> pks);

    List<Music> getMusicListFromDBThruMusicId(String videoId, String playlistId);

    void updateMusicWithReplacement(long pk, Music replacementMusic);
}

// List<Music> findAllMusicByPlaylistId(String playlistId); 밑으로 대체
// List<Music> findAllWithPlaylistByPlaylistIn(List<Playlists> playListsSet); 이것도 밑으로 대체됨
//    void saveAll(List<Music> musicsToSave);
//    Optional<Music> findById(long pk);