package youtube.youtubeService.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor
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


    public Music(String videoId, String videoTitle, String videoUploader, String videoDescription, String videoTags, Playlists playlist) {
        this.videoId = videoId;
        this.videoTitle = videoTitle;
        this.videoUploader = videoUploader;
        this.videoDescription = videoDescription;
        this.videoTags = videoTags;
        this.playlist = playlist;
    }
}


