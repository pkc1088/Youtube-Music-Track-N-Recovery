package youtube.youtubeService.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import youtube.youtubeService.domain.Outbox;
import java.util.List;
import java.util.Optional;

public interface OutboxRepository extends JpaRepository<Outbox, Long> {

    List<Outbox> findByUserIdAndStatus(String userId, Outbox.Status status);

    Optional<Outbox> findOutboxById(Long id); // findById (x) 이름 다르게 만들어서 사용자 정의로 씀
}
