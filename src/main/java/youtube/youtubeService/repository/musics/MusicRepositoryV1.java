package youtube.youtubeService.repository.musics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import youtube.youtubeService.domain.Music;
import youtube.youtubeService.domain.Playlists;
import youtube.youtubeService.dto.internal.MusicDetailsDto;
import youtube.youtubeService.dto.internal.MusicSummaryDto;

import java.util.List;

@Slf4j
@Repository
@RequiredArgsConstructor
public class MusicRepositoryV1 implements MusicRepository{

    private final SdjMusicRepository repository;

    @Override
    public List<MusicSummaryDto> findAllMusicSummaryByPlaylistIds(List<Playlists> playListsSet) {
        return repository.findAllMusicSummaryByPlaylistIds(playListsSet);
    }

    @Override
    public void upsertMusic(Music music) {
        repository.save(music);
    }

    @Override
    public void deleteById(Long pk) {
        repository.deleteById(pk);
    }

    @Override
    public void deleteAllByIdInBatch(List<Long> pks) {
        repository.deleteAllByIdInBatch(pks);
    }

    @Override
    public List<MusicDetailsDto> getMusicListFromDBThruMusicId(String videoId, String playlistId) {
        return repository.findAllByVideoIdAndPlaylistId(videoId, playlistId);
    }

    @Override
    public void updateMusicWithReplacement(long pk, Music replacementMusic) {
        repository.updateMusicWithReplacement(pk, replacementMusic);
    }
}