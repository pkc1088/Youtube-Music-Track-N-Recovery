package youtube.youtubeService.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class Users {

    @Id
    private String userId;
    private String userName;
    private String userChannelId;
    private String userEmail;
    private String countryCode = "KR";
    private String refreshToken;
}

//    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true) // 이 필드는 Playlists 의 Users user 가 주인임
//    private Set<Playlists> playlists;