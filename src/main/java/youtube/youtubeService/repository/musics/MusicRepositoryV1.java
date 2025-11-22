package youtube.youtubeService.repository.musics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import youtube.youtubeService.domain.Music;
import youtube.youtubeService.domain.Playlists;
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
    public List<Music> getMusicListFromDBThruMusicId(String videoId, String playlistId) {
        return repository.findAllByVideoIdAndPlaylistId(videoId, playlistId);
    }

    public void updateMusicWithReplacement(long pk, Music replacementMusic) {
        repository.updateMusicWithReplacement(pk, replacementMusic);
    }
}

//    @Override
//    public List<Music> findAllMusicByPlaylistId(String playlistId) {
//        return repository.findByPlaylist_PlaylistId(playlistId); 밑으로 대체
//    }
//    @Override
//    public List<Music> findAllWithPlaylistByPlaylistIn(List<Playlists> playListsSet) {
//        log.info("[Fetch Join Called]");
//        return repository.findAllWithPlaylistByPlaylistIn(playListsSet); 이것도 밑으로 대체됨
//    }
//    public Optional<Music> findById(long id) {
//        return repository.findById(id);
//    }
//
//    public void saveAll(List<Music> musicsToSave) {
//        repository.saveAll(musicsToSave); // repository.saveAllAndFlush(musicsToSave);
//    }