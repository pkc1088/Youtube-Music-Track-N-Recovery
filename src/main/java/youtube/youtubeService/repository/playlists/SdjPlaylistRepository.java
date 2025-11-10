package youtube.youtubeService.repository.playlists;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import youtube.youtubeService.domain.Playlists;

import java.util.List;

public interface SdjPlaylistRepository extends JpaRepository<Playlists, String> {

    Playlists findByPlaylistId(String playlistId);

    // 'IN' 절로 여러 유저의 플레이리스트를 조회하되, p.user.userId로 그룹화할 때 N+1이 발생하지 않도록 User 객체도 함께 JOIN FETCH
    @Query("SELECT p FROM Playlists p JOIN FETCH p.user WHERE p.user.userId IN :userIds")
    List<Playlists> findAllByUserIdsWithUser(@Param("userIds") List<String> userIds);

    @Modifying
    @Query("DELETE FROM Playlists p WHERE p.playlistId IN :playlistIds")
    void deleteAllByPlaylistIdsIn(@Param("playlistIds") List<String> playlistIds);

    @Query("SELECT p FROM Playlists p LEFT JOIN FETCH p.user u WHERE u.userId = :userId")
    List<Playlists> findAllByUserIdWithUserFetch(@Param("userId") String userId);
}

// List<Playlists> findByUser_UserId(String userId);
