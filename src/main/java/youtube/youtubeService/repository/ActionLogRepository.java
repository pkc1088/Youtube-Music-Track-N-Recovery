package youtube.youtubeService.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import youtube.youtubeService.domain.ActionLog;
import java.util.List;


public interface ActionLogRepository extends JpaRepository<ActionLog, Long> {
    List<ActionLog> findByUserIdOrderByCreatedAtDesc(String userId);

    // @Transactional // ?
    void deleteByUserId(String userId);
}
