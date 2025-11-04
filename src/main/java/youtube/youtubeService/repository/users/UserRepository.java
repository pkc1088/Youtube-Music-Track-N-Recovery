package youtube.youtubeService.repository.users;

import youtube.youtubeService.domain.Users;

import java.util.List;

public interface UserRepository {

    Users findByUserId(String userId);

    void saveUser(Users user);

    void deleteUser(Users user);

    List<Users> findAllUsers();

    void deleteById(String userId);
}
