package youtube.youtubeService.repository.musics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import youtube.youtubeService.domain.Music;
import youtube.youtubeService.domain.Playlists;
import youtube.youtubeService.dto.internal.MusicSummaryDto;

import java.util.List;

public interface SdjMusicRepository extends JpaRepository<Music, Long> {


    @Query("""
        SELECT new youtube.youtubeService.dto.internal.MusicSummaryDto(
            m.id, m.videoId, m.videoTitle, m.videoUploader, m.playlist.playlistId
        )
        FROM Music m
        WHERE m.playlist IN :playlists
    """)
    List<MusicSummaryDto> findAllMusicSummaryByPlaylistIds(@Param("playlists") List<Playlists> playlists);

    @Query("SELECT m FROM Music m WHERE m.videoId = :videoId AND m.playlist.playlistId = :playlistId")
    List<Music> findAllByVideoIdAndPlaylistId(@Param("videoId") String videoId, @Param("playlistId") String playlistId);

    @Modifying // (필수) 이 쿼리가 DB 상태를 변경함을 알림
    @Query("UPDATE Music m SET " +
            "m.videoId = :#{#music.videoId}, " +
            "m.videoTitle = :#{#music.videoTitle}, " +
            "m.videoUploader = :#{#music.videoUploader}, " +
            "m.videoDescription = :#{#music.videoDescription}, " +
            "m.videoTags = :#{#music.videoTags} " +
            "WHERE m.id = :pk")
    void updateMusicWithReplacement(@Param("pk") Long pk, @Param("music") Music music);
}

// List<Music> findByPlaylist_PlaylistId(String playlistId); 밑으로 대체

// @Query("SELECT m FROM Music m JOIN FETCH m.playlist WHERE m.playlist IN :playlists")
// List<Music> findAllWithPlaylistByPlaylistIn(@Param("playlists") List<Playlists> playlists); 이것도 대체됨
