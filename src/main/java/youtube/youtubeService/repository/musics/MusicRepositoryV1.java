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
//@Transactional
@RequiredArgsConstructor
public class MusicRepositoryV1 implements MusicRepository{

    private final SdjMusicRepository repository;


    @Override
    public List<Music> findAllMusicByPlaylistId(String playlistId) {
        return repository.findByPlaylist_PlaylistId(playlistId);
    }

    @Override
    public void addUpdatePlaylist(String playlistId, Music music) {
        repository.save(music);
    }

    @Override
    public void deleteUpdatePlaylist(String playlistId, String videoId) {
        repository.deleteByVideoId(videoId);
    }

    @Override
    public Optional<Music> getMusicFromDBThruMusicId(String videoId, String playlistId) {
        return repository.findByVideoIdAndPlaylistId(videoId, playlistId);
        //        return repository.findByVideoId(videoId);
    }

    @Override
    public void dBTrackAndRecover(String videoIdToDelete, Music videoToRecover, String playlistId) {
        Optional<Music> optionalMusic = repository.findByVideoIdAndPlaylistId(videoIdToDelete, playlistId);
//        Optional<Music> optionalMusic = repository.findByVideoId(videoIdToDelete);
        if (optionalMusic.isPresent()) {
            Music musicToUpdate = optionalMusic.get();
            log.info("Illegal videoId : {}", musicToUpdate.getVideoId());
//            musicToUpdate.setId(videoToRecover.getId());
            musicToUpdate.setVideoId(videoToRecover.getVideoId());// videoToRecover의 정보로 엔티티 업데이트
            musicToUpdate.setVideoTitle(videoToRecover.getVideoTitle());
            musicToUpdate.setVideoUploader(videoToRecover.getVideoUploader());
            musicToUpdate.setVideoDescription(videoToRecover.getVideoDescription());
            musicToUpdate.setVideoTags(videoToRecover.getVideoTags());
            //getVideoPlaylistPosition/Id/UserId() 기존 그대로 유지할거니 건들 필요 없음
            log.info("DB update completed");
        } else {
            throw new RuntimeException("DB update error: " + videoIdToDelete);
        }
    }
}
