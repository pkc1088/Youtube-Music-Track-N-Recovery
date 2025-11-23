package youtube.youtubeService.repository.users;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import youtube.youtubeService.domain.Users;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class UserRepositoryV1 implements UserRepository {

    private final SdjUserRepository repository;


    @Override
    public Users findByUserId(String userId) {
        return repository.findByUserId(userId);
    }

    public void saveUser(Users user) {
        repository.save(user);
    }

    public void deleteUser(Users user) {
        repository.delete(user);
    }

    public List<Users> findAllUsers() {
        return repository.findAll(); // 정렬 없음
    }

    public void deleteById(String userId) {
        repository.deleteById(userId);
    }
}
