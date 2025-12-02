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
import youtube.youtubeService.exception.users.UserRevokeException;
import youtube.youtubeService.exception.users.UserQuitException;
import youtube.youtubeService.repository.users.UserRepository;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

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
    public void deleteAndRevokeUserAccount(String userId, String accessToken) {
        revokeUser(accessToken);
        deleteByUserIdRaw(userId);
    }

    @Override
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
