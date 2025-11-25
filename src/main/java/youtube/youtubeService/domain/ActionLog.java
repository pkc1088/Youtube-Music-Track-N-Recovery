package youtube.youtubeService.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
public class ActionLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String userId;
    private String playlistId;

    @Enumerated(EnumType.STRING)
    private ActionType actionType;

    private String targetVideoId;
    private String targetVideoTitle;
    private String sourceVideoId;
    private String sourceVideoTitle;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }

    public enum ActionType { RECOVER, NOTIFY }

    public ActionLog(String userId, String playlistId, ActionType actionType, String targetVideoId, String targetVideoTitle, String sourceVideoId, String sourceVideoTitle) {
        this.userId = userId;
        this.playlistId = playlistId;
        this.actionType = (actionType != null) ? actionType : ActionType.RECOVER;
        this.targetVideoId = targetVideoId;
        this.targetVideoTitle = targetVideoTitle;
        this.sourceVideoId = sourceVideoId;
        this.sourceVideoTitle = sourceVideoTitle;
    }
}