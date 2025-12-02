package youtube.youtubeService.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
public class Playlists {

    @Id
    private String playlistId;
    private String playlistTitle;

    @Enumerated(EnumType.STRING)
    private ServiceType serviceType;

    private LocalDateTime lastCheckedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "userId", nullable = false)
    private Users user;

    public enum ServiceType { RECOVER, NOTIFY }

    public Playlists(String playlistId, String playlistTitle, ServiceType serviceType, LocalDateTime lastCheckedAt, Users user) {
        this.playlistId = playlistId;
        this.playlistTitle = playlistTitle;
        this.serviceType = (serviceType != null) ? serviceType : ServiceType.RECOVER;
        this.lastCheckedAt = lastCheckedAt;
        this.user = user;
    }
}

