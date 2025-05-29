package youtube.youtubeService.service.users;

import youtube.youtubeService.domain.Users;

public interface UserService {

    Users getUserByUserId(String userId);
    void saveUser(Users user);
    String getNewAccessTokenByUserId(String userId);
    void deleteUser(Users user);
}
