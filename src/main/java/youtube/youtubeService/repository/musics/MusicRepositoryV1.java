package youtube.youtubeService.repository.musics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import youtube.youtubeService.domain.Music;

import java.util.List;
import java.util.Optional;

@Slf4j
@Repository
@RequiredArgsConstructor
public class MusicRepositoryV1 implements MusicRepository{

    private final SdjMusicRepository repository;

    @Override
    public List<Music> findAllMusicByPlaylistId(String playlistId) {
        return repository.findByPlaylist_PlaylistId(playlistId);
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
    public List<Music> getMusicListFromDBThruMusicId(String videoId, String playlistId) {
        return repository.findAllByVideoIdAndPlaylistId(videoId, playlistId);
    }

    @Override
    public void dBTrackAndRecoverPosition(String videoIdToDelete, Music videoToRecover, Long pk) {

        Optional<Music> optionalMusic = repository.findById(pk);
        if (optionalMusic.isPresent()) {
            Music musicToUpdate = optionalMusic.get();
            log.info("Illegal videoId : {} at {}", musicToUpdate.getVideoId(), pk);
            musicToUpdate.setVideoId(videoToRecover.getVideoId());// videoToRecover 정보로 엔티티 업데이트
            musicToUpdate.setVideoTitle(videoToRecover.getVideoTitle());
            musicToUpdate.setVideoUploader(videoToRecover.getVideoUploader());
            musicToUpdate.setVideoDescription(videoToRecover.getVideoDescription());
            musicToUpdate.setVideoTags(videoToRecover.getVideoTags());
            log.info("DB update completed");
        } else {
            throw new RuntimeException("DB update error: " + videoIdToDelete);
        }
    }

    public void saveAll(List<Music> musicsToSave) {
        repository.saveAll(musicsToSave);
    }
}