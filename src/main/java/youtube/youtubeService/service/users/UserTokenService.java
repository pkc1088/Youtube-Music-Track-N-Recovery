package youtube.youtubeService.service.users;

import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import youtube.youtubeService.domain.Users;
import youtube.youtubeService.exception.users.UserQuitException;
import youtube.youtubeService.exception.users.UserRevokeException;

import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserTokenService {

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String clientId;
    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String clientSecret;
    private final UserService userService;


    public void withdrawUser(String userId) {
        Users user = userService.getUserByUserId(userId)
                .orElseThrow(() -> new UserRevokeException("User not found for withdrawal", null));

        revokeUser(getNewAccessTokenByUserId(user.getRefreshToken()));
        userService.deleteByUserIdRaw(userId);
    }

    public void revokeUser(String token) {
        RestTemplate restTemplate = new RestTemplate();
        if (token != null && !token.isBlank()) {
            String revokeUrl = "https://oauth2.googleapis.com/revoke?token=" + token;
            try {
                restTemplate.postForEntity(revokeUrl, null, String.class);
                log.info("User has been revoked from the service");
            } catch (Exception e) {
                log.error("Failed to revoke Google token for user");
                throw new UserRevokeException("Failed to revoke Google token for user", e);
            }
        }
    }

    public String getNewAccessTokenByUserId(String refreshToken) {
        try {
            return refreshAccessToken(refreshToken);
        } catch (IOException e) {
            throw new UserQuitException(e.getMessage());
        }
    }

    private String refreshAccessToken(String refreshToken) throws IOException {
        GoogleRefreshTokenRequest refreshTokenRequest = new GoogleRefreshTokenRequest(
                new NetHttpTransport(),
                new GsonFactory(),
                refreshToken,
                clientId,
                clientSecret
        );

        TokenResponse tokenResponse = refreshTokenRequest.execute();

        return tokenResponse.getAccessToken();
    }
}
