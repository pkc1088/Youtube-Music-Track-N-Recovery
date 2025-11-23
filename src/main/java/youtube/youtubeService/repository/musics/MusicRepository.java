package youtube.youtubeService.repository.musics;

import youtube.youtubeService.domain.Music;
import youtube.youtubeService.domain.Playlists;
import youtube.youtubeService.dto.internal.MusicDetailsDto;
import youtube.youtubeService.dto.internal.MusicSummaryDto;

import java.util.List;

public interface MusicRepository {

    List<MusicSummaryDto> findAllMusicSummaryByPlaylistIds(List<Playlists> playListsSet);

    void upsertMusic(Music music);

    void deleteById(Long pk);

    void deleteAllByIdInBatch(List<Long> pks);

    List<MusicDetailsDto> getMusicListFromDBThruMusicId(String videoId, String playlistId);

    void updateMusicWithReplacement(long pk, Music replacementMusic);

}