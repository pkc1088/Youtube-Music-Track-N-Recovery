package youtube.youtubeService.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class Outbox {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private ActionType actionType;

    private String accessToken;
    private String userId;
    private String playlistId;
    private String videoId;
    @Column(nullable = true)
    private String playlistItemId;

    @Enumerated(EnumType.STRING)
    private Status status = Status.PENDING;

    private int retryCount = 0;
    private LocalDateTime lastAttemptedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public enum ActionType { ADD, DELETE }
    public enum Status { PENDING, SUCCESS, FAILED, DEAD }

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
