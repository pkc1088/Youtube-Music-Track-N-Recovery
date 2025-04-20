package youtube.youtubeService.repository.musics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import youtube.youtubeService.domain.Music;

import java.util.List;
import java.util.Optional;

public interface SdjMusicRepository extends JpaRepository<Music, Long> {

    List<Music> findByPlaylist_PlaylistId(String playlistId);

//    Optional<Music> findByVideoId(String videoId);

    @Query("SELECT m FROM Music m WHERE m.videoId = :videoId AND m.playlist.playlistId = :playlistId")
    Optional<Music> findByVideoIdAndPlaylistId(@Param("videoId") String videoId, @Param("playlistId") String playlistId);

//    Optional<Music> findByVideoIdAndPlaylist(String videoId, String playlistId);

    void deleteByVideoId(String videoId);
}
