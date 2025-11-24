package youtube.youtubeService.repository.users;

import youtube.youtubeService.domain.Users;

import java.util.List;

public interface UserRepository {

    Users findByUserId(String userId);

    void saveUser(Users user);

    List<Users> findAllUsers();

    void deleteByUserIdRaw(String userId);
}
