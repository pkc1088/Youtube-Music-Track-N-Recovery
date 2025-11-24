package youtube.youtubeService.service.users;

import youtube.youtubeService.domain.Users;

import java.util.List;
import java.util.Optional;

public interface UserService {

    Optional<Users> getUserByUserId(String userId);

    void saveUser(Users user);

    String getNewAccessTokenByUserId(String userId, String refreshToken);

    List<Users> findAllUsers();

    void deleteAndRevokeUserAccount(String userId);

    void revokeUser(String refreshToken);

    void deleteByUserIdRaw(String userId);
}
