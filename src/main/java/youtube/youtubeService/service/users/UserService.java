package youtube.youtubeService.service.users;

import youtube.youtubeService.domain.Users;

import java.util.List;
import java.util.Optional;

public interface UserService {

    Optional<Users> getUserByUserId(String userId);

    void saveUser(Users user);

    String getNewAccessTokenByUserId(String userId);

    List<Users> findAllUsers();

    void deleteUserAccount(String userId);

    void revokeUser(String refreshToken);
//    Users findByUserId(String userId);
//    void deleteUser(Users user);

}
