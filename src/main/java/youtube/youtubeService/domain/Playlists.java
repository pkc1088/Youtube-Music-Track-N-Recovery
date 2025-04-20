package youtube.youtubeService.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
    private String serviceType;

    @ManyToOne
    @JoinColumn(name = "userId", nullable = false) // Users.userId를 FK로 가짐
    private Users user;

//    public Playlists(String playlistId, String playlistTitle, String serviceType, Users user) {
//        this.playlistId = playlistId;
//        this.playlistTitle = playlistTitle;
//        this.serviceType = serviceType;
//        this.user = user;
//    }
}

//    없어도 되는 듯?
//    @OneToMany(mappedBy = "playlist", cascade = CascadeType.ALL, orphanRemoval = true)
//    private Set<Music> playlistItems;