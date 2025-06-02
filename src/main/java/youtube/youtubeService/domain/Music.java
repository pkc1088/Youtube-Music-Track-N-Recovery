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

    @ManyToOne
    @JoinColumn(name = "playlistId", nullable = false) // Playlists playlistId 를 FK 로 지정. name은 DB에 저장될 이름임
    private Playlists playlist;
}

//    public Music(String videoId, String videoTitle, Playlists playlist) {
//        this.videoId = videoId;
//        this.videoTitle = videoTitle;
//        this.playlist = playlist;
//    }

//    public Music(String videoId, String videoTitle, String videoUploader, String videoDescription, String videoTags,
//                 /*int videoPlaylistPosition,*/ Playlists playlist) {
//        this.videoId = videoId;
//        this.videoTitle = videoTitle;
//        this.videoUploader = videoUploader;
//        this.videoDescription = videoDescription;
//        this.videoTags = videoTags;
////        this.videoPlaylistPosition = videoPlaylistPosition;
//        this.playlist = playlist;
//    }
