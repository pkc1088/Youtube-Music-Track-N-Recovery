package youtube.youtubeService.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import youtube.youtubeService.domain.ActionLog;

public interface ActionLogRepository extends JpaRepository<ActionLog, Long> {

}
