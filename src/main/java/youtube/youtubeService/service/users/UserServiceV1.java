package youtube.youtubeService.service.users;

import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import youtube.youtubeService.domain.Users;
import youtube.youtubeService.exception.UserQuitException;
import youtube.youtubeService.repository.users.UserRepository;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceV1 implements UserService {

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String clientId;
    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String clientSecret;
    private final UserRepository userRepository;

    @Override
    public List<Users> findAllUsers() {
        return userRepository.findAllUsers();
    }

    @Override
    public Optional<Users> getUserByUserId(String userId) {
        return Optional.ofNullable(userRepository.findByUserId(userId));
    }

    @Override
    @Transactional
    public void saveUser(Users user) {
        userRepository.saveUser(user);
    }

    @Override
    @Transactional
    public void deleteAndRevokeUserAccount(String userId) {
        Users user = getUserByUserId(userId).orElseThrow(() -> new IllegalArgumentException("[No User Found]"));
        String refreshToken = user.getRefreshToken();
        revokeUser(refreshToken);
        deleteByUserIdRaw(userId);
    }

    @Override
    public void revokeUser(String refreshToken) {
        RestTemplate restTemplate = new RestTemplate();
        if (refreshToken != null && !refreshToken.isBlank()) {
            String revokeUrl = "https://oauth2.googleapis.com/revoke?token=" + refreshToken;
            try {
                restTemplate.postForEntity(revokeUrl, null, String.class);
                log.info("User has been revoked from the service");
            } catch (Exception e) {
                log.warn("Failed to revoke Google token for user", e);
            }
        }
    }

    @Override
    @Transactional
    public void deleteByUserIdRaw(String userId) {
        userRepository.deleteByUserIdRaw(userId);
        log.info("[User [{}] has been deleted from DB]", userId);
    }

    @Override
    public String getNewAccessTokenByUserId(String userId, String refreshToken) {
        log.info("[once a day : accessToken <- refreshToken]");
        try {
            return refreshAccessToken(refreshToken);
        } catch (IOException e) {
            throw new UserQuitException(e.getMessage());
        }
    }

    private String refreshAccessToken(String refreshToken) throws IOException {
        try {
            GoogleRefreshTokenRequest refreshTokenRequest = new GoogleRefreshTokenRequest(
                    new NetHttpTransport(),
                    new GsonFactory(),
                    refreshToken,
                    clientId,
                    clientSecret
            );
            TokenResponse tokenResponse = refreshTokenRequest.execute();
            return tokenResponse.getAccessToken();
        } catch (IOException e) {
            throw new IOException("[user quit thru security page -> gotta delete users from my service]");
        }
    }

}
