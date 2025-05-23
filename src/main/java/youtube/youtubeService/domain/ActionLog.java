package youtube.youtubeService.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class ActionLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String userId;
    private String playlistId;
    private String actionType; // recover/notification
    private String targetVideoId;
    private String targetVideoTitle;
    private String sourceVideoId;
    private String sourceVideoTitle;

    @CreationTimestamp
    private LocalDateTime createdAt;

}