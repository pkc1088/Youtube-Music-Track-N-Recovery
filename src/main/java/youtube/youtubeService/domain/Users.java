package youtube.youtubeService.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor
public class Users {

    @Id
    private String userId;

    @Enumerated(EnumType.STRING)
    private UserRole userRole;

    private String userName;
    private String userChannelId;
    private String userEmail;
    private String countryCode;
    private String refreshToken;

    public enum UserRole { ADMIN, USER }


    public Users(String userId, UserRole userRole, String userName, String userChannelId, String userEmail, String countryCode, String refreshToken) {
        this.userId = userId;
        this.userRole = (userRole != null) ? userRole : UserRole.USER;;
        this.userName = userName;
        this.userChannelId = userChannelId;
        this.userEmail = userEmail;
        this.countryCode = (countryCode != null) ? countryCode : "KR";
        this.refreshToken = refreshToken;
    }

    public void updateRefreshToken(String newRefreshToken) {
        this.refreshToken = newRefreshToken;
    }
}

