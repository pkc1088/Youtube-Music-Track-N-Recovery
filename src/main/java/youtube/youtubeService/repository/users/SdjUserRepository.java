package youtube.youtubeService.repository.users;

import org.springframework.data.jpa.repository.JpaRepository;
import youtube.youtubeService.domain.Users;

public interface SdjUserRepository extends JpaRepository<Users, String> {

    Users findByUserId(String userId);

}
//    Users findByUserEmail(String userEmail);
//    Optional<User> findByUserId(String userId);
//    Users findByAccessToken(String accessToken);
//    Optional<Music> findByVideoTitle(String videoTitle);