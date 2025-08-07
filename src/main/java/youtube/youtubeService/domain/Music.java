package youtube.youtubeService.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class Music {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String videoId;
    private String videoTitle;
    private String videoUploader;
    @Column(length = 5010)
    private String videoDescription;
    @Column(length = 510)
    private String videoTags;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "playlistId", nullable = false)
    private Playlists playlist;
}


