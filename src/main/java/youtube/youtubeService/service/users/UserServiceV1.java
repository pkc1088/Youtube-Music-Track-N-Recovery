package youtube.youtubeService.service.users;

import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import youtube.youtubeService.domain.Users;
import youtube.youtubeService.repository.users.UserRepository;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@Transactional
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
    public void saveUser(Users user) {
        userRepository.saveUser(user);
    }

    @Override
    public void deleteUserAccount(String userId) {
        Users user = getUserByUserId(userId).orElseThrow(() -> new IllegalArgumentException("[No User Found]"));
        String refreshToken = user.getRefreshToken();
        revokeUser(refreshToken);
        userRepository.deleteUser(user);
    }

    @Override
    public void revokeUser(String refreshToken) {
        RestTemplate restTemplate = new RestTemplate();
        if (refreshToken != null && !refreshToken.isBlank()) {
            String revokeUrl = "https://oauth2.googleapis.com/revoke?token=" + refreshToken;
            try {
                restTemplate.postForEntity(revokeUrl, null, String.class);
            } catch (Exception e) {
                log.warn("Failed to revoke Google token for user", e);
            }
        }
    }

    @Override
    public String getNewAccessTokenByUserId(String userId, String refreshToken) {
        log.info("[once a day : accessToken <- refreshToken]");
        //Users user = userRepository.findByUserId(userId);
        //String refreshToken = user.getRefreshToken();
        String accessToken;
        try {
            accessToken = refreshAccessToken(refreshToken);
        } catch (IOException e) {
            log.info("[{}]", e.getMessage());
            userRepository.deleteById(userId);  // deleteBy(entity) 대신 id를 넘겨서 평시에 findByUserId 할 필요 없도록해서 쿼리 줄임
            return "";
        }
        return accessToken;
    }

    public String refreshAccessToken(String refreshToken) throws IOException { // 사실 이게 핵심인듯?
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
        } catch (IOException e) { // 이 메서드도 트잭 걸려있으나 IOException 던지니까 커밋 될 상태인거임
            throw new IOException("[user quit thru security page -> gotta delete users from my service]");
        }
    }

}

/** OGCODE BEFORE 0903
 @Slf4j
 @Service
 @Transactional
 @RequiredArgsConstructor
 public class UserServiceV1 implements UserService {

 @Value("${spring.security.oauth2.client.registration.google.client-id}")
 private String clientId;
 @Value("${spring.security.oauth2.client.registration.google.client-secret}")
 private String clientSecret;
 private final UserRepository userRepository;

 public List<Users> findAllUsers() {
 return userRepository.findAllUsers();
 }

 public Users findByUserId(String userId) {
 return userRepository.findByUserId(userId);
 }

 @Override
 public Optional<Users> getUserByUserId(String userId) {
 return Optional.ofNullable(userRepository.findByUserId(userId));
 }

 @Override
 public void saveUser(Users user) {
 userRepository.saveUser(user);
 }

 @Override
 public void deleteUserAccount(String userId) {
 Users user = getUserByUserId(userId).orElseThrow(() -> new IllegalArgumentException("[No User Found]"));
 String refreshToken = user.getRefreshToken();
 RestTemplate restTemplate = new RestTemplate();
 if (refreshToken != null && !refreshToken.isBlank()) {
 String revokeUrl = "https://oauth2.googleapis.com/revoke?token=" + refreshToken;
 try {
 restTemplate.postForEntity(revokeUrl, null, String.class);
 userRepository.deleteUser(user); // 유저 DB에서 삭제
 } catch (Exception e) {
 log.warn("Failed to revoke Google token for user: {}", user.getUserId(), e);
 }
 }
 }

 @Override
 public String getNewAccessTokenByUserId(String userId) {
 log.info("once a day : accessToken <- refreshToken");
 Users user = userRepository.findByUserId(userId);
 String refreshToken = user.getRefreshToken();
 String accessToken;
 try {
 accessToken = refreshAccessToken(refreshToken);
 } catch (IOException e) {
 log.info("{}", e.getMessage());
 userRepository.deleteUser(user);  // 여기서 예외 잡을때 유저 제거해줘야함 (만약 고객이 보안페이지에서 제거한거라면)
 return "";
 }
 return accessToken;
 }

 public String refreshAccessToken(String refreshToken) throws IOException { // 사실 이게 핵심인듯?
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
 } catch (IOException e) { // 이 메서드도 트잭 걸려있으나 IOException 던지니까 커밋 될 상태인거임
 throw new IOException("[user quit thru security page -> gotta delete users from my service]");
 }
 }

 }
 */