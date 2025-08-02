package youtube.youtubeService.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import youtube.youtubeService.domain.Outbox;

import java.util.List;
import java.util.Optional;

public interface OutboxRepository extends JpaRepository<Outbox, Long> {

    @Query("""
        SELECT o.id FROM Outbox o WHERE o.userId = :userId AND o.playlistId = :playlistId AND o.videoId = :videoId AND o.status IN :statuses
    """)
    List<Long> findIdsByUserIdAndPlaylistIdAndVideoIdAndStatusIn(
            @Param("userId") String userId,
            @Param("playlistId") String playlistId,
            @Param("videoId") String videoId,
            @Param("statuses") List<Outbox.Status> statuses
    );

    List<Outbox> findByStatus(Outbox.Status status);

    // List<Outbox> findByBatchId(String batchId);
    // Optional<Outbox> findTopByUserIdAndPlaylistIdAndVideoIdAndActionTypeAndStatusIn(String userId, String playlistId, String videoId, Outbox.ActionType actionType, List<Outbox.Status> statuses);
    // List<Outbox> findByUserIdAndPlaylistIdAndVideoId(String userId, String playlistId, String videoId);

}
