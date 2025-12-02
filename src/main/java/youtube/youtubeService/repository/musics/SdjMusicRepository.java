package youtube.youtubeService.repository.musics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import youtube.youtubeService.domain.Music;
import youtube.youtubeService.domain.Playlists;
import youtube.youtubeService.dto.internal.MusicDetailsDto;
import youtube.youtubeService.dto.internal.MusicSummaryDto;

import java.util.List;

public interface SdjMusicRepository extends JpaRepository<Music, Long> {

    @Query("""
        SELECT new youtube.youtubeService.dto.internal.MusicSummaryDto(
            m.id, m.videoId, m.playlist.playlistId
        )
        FROM Music m
        WHERE m.playlist IN :playlists
    """)
    List<MusicSummaryDto> findAllMusicSummaryByPlaylistIds(@Param("playlists") List<Playlists> playlists);

    @Query("""
        SELECT new youtube.youtubeService.dto.internal.MusicDetailsDto(
            m.id, m.videoId, m.videoTitle, m.videoUploader, m.videoDuration, m.videoDescription, m.videoTags, m.playlist.playlistId
        )
        FROM Music m
        WHERE m.videoId = :videoId AND m.playlist.playlistId = :playlistId
    """)
    List<MusicDetailsDto> findAllByVideoIdAndPlaylistId(@Param("videoId") String videoId, @Param("playlistId") String playlistId);

    @Modifying
    @Query("UPDATE Music m SET " +
            "m.videoId = :#{#music.videoId}, " +
            "m.videoTitle = :#{#music.videoTitle}, " +
            "m.videoUploader = :#{#music.videoUploader}, " +
            "m.videoDuration = :#{#music.videoDuration}, " +
            "m.videoDescription = :#{#music.videoDescription}, " +
            "m.videoTags = :#{#music.videoTags} " +
            "WHERE m.id = :pk")
    void updateMusicWithReplacement(@Param("pk") Long pk, @Param("music") Music music);
}

