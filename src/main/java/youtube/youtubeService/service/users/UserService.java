package youtube.youtubeService.service.users;

import youtube.youtubeService.domain.Users;

import java.util.List;
import java.util.Optional;

public interface UserService {

    Optional<Users> getUserByUserId(String userId);

    void saveUser(Users user);

    List<Users> findAllUsers();

    void deleteByUserIdRaw(String userId);
}
