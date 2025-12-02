package youtube.youtubeService.service.musics;

import com.google.api.services.youtube.model.Video;
import youtube.youtubeService.domain.Music;
import youtube.youtubeService.domain.Playlists;
import youtube.youtubeService.dto.internal.MusicDetailsDto;
import youtube.youtubeService.dto.internal.MusicSummaryDto;

import java.util.List;

public interface MusicService {

    List<MusicSummaryDto> findAllMusicSummaryByPlaylistIds(List<Playlists> playListsSet);

    void deleteAllByIdInBatch(List<Long> ids);

    List<MusicDetailsDto> getMusicListFromDBThruMusicId(String videoIdToDelete, String playlistId);

    void updateMusicWithReplacement(long pk, Music replacementMusic);

    void upsertMusic(Music music);

    void saveAllVideos(List<Video> legalVideos, Playlists playlist);

    void bulkInsertMusic(List<Music> musics);

    Video searchVideoToReplace(MusicDetailsDto musicToSearch, String countryCode);

}
