package youtube.youtubeService.repository.playlists;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import youtube.youtubeService.domain.Playlists;

import java.time.LocalDateTime;
import java.util.List;

public interface SdjPlaylistRepository extends JpaRepository<Playlists, String> {

    // 'IN' 절로 여러 유저의 플레이리스트를 조회하되, p.user.userId로 그룹화할 때 N+1이 발생하지 않도록 User 객체도 함께 JOIN FETCH
    @Query("SELECT p FROM Playlists p JOIN FETCH p.user u WHERE u.userId IN :userIds ORDER BY p.lastCheckedAt ASC NULLS FIRST")
    List<Playlists> findAllPlaylistsByUserIdsOrderByLastChecked(@Param("userIds") List<String> userIds);

    @Query("SELECT p FROM Playlists p JOIN FETCH p.user u WHERE u.userId = :userId")
    List<Playlists> findAllByUserIdWithUserFetch(@Param("userId") String userId);

    @Modifying(clearAutomatically = true) // 1차 캐시 정합성을 위해 clear 권장
    @Query("UPDATE Playlists p SET p.lastCheckedAt = :now WHERE p.playlistId = :playlistId")
    void updateLastCheckedAt(@Param("playlistId") String playlistId, @Param("now") LocalDateTime now);
}
