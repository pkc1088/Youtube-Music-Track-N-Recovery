package youtube.youtubeService.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    @Enumerated(EnumType.STRING)
    private UserRole userRole = UserRole.USER;

    private String userName;
    private String userChannelId;
    private String userEmail;
    private String countryCode = "KR";
    private String refreshToken;

    public enum UserRole { ADMIN, USER }

}

