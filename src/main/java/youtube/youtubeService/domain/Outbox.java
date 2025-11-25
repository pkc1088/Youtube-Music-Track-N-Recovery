package youtube.youtubeService.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
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
    private Status status;

    private int failCount;
    private LocalDateTime lastUpdatedAt;
    private LocalDateTime createdAt;

    public enum ActionType { ADD, DELETE }
    public enum Status { PENDING, SUCCESS, FAILED, DEAD }

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }

    public Outbox(ActionType actionType, String accessToken, String userId, String playlistId, String videoId, String playlistItemId) {
        this.actionType = actionType;
        this.accessToken = accessToken;
        this.userId = userId;
        this.playlistId = playlistId;
        this.videoId = videoId;
        this.playlistItemId = playlistItemId;

        this.status = Status.PENDING;
        this.failCount = 0;
    }

    public void updateStatus(Status newStatus) {
        if (newStatus == Status.FAILED || newStatus == Status.DEAD) {
            this.failCount++;
        }
        this.status = newStatus;
        this.lastUpdatedAt = LocalDateTime.now();
    }
}
