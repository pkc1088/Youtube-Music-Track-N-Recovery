package youtube.youtubeService.service.users;

import youtube.youtubeService.domain.Users;

import java.util.List;

public interface UserService {

    Users getUserByUserId(String userId);

    void saveUser(Users user);

    String getNewAccessTokenByUserId(String userId);

    void deleteUser(Users user);

    List<Users> findAllUsers();

    Users findByUserId(String userId);
}
