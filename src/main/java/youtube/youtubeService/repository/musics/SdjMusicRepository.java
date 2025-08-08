package youtube.youtubeService.repository.musics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import youtube.youtubeService.domain.Music;

import java.util.List;
import java.util.Optional;

public interface SdjMusicRepository extends JpaRepository<Music, Long> {

    List<Music> findByPlaylist_PlaylistId(String playlistId);

    @Query("SELECT m FROM Music m WHERE m.videoId = :videoId AND m.playlist.playlistId = :playlistId")
    List<Music> findAllByVideoIdAndPlaylistId(@Param("videoId") String videoId, @Param("playlistId") String playlistId);

}
