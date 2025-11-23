package youtube.youtubeService.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import youtube.youtubeService.domain.ActionLog;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;


public interface ActionLogRepository extends JpaRepository<ActionLog, Long> {

    List<ActionLog> findByUserIdOrderByCreatedAtDesc(String userId);

    @Query(value = "SELECT * FROM action_log " + "WHERE action_type = :actionType " + "AND target_video_id = :targetVideoId " +
        "AND created_at >= :today " + "ORDER BY created_at DESC LIMIT 1", nativeQuery = true)
    Optional<ActionLog> findTodayRecoverLog(@Param("actionType") ActionLog.ActionType actionType,
                                            @Param("targetVideoId") String targetVideoId, @Param("today") LocalDateTime today);
}
