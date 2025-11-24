package youtube.youtubeService.repository.users;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import youtube.youtubeService.domain.Users;

public interface SdjUserRepository extends JpaRepository<Users, String> {

    Users findByUserId(String userId);

    @Modifying
    @Query("DELETE FROM Users u WHERE u.userId = :userId")
    void deleteByUserIdRaw(@Param("userId") String userId);

}
