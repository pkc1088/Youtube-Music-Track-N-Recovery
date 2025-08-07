package youtube.youtubeService.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

//@Data
@Entity
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class Playlists {

    @Id
    private String playlistId;
    private String playlistTitle;

    @Enumerated(EnumType.STRING)
    private ServiceType serviceType = ServiceType.RECOVER;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "userId", nullable = false)
    private Users user;

    public enum ServiceType { RECOVER, NOTIFY }
}

