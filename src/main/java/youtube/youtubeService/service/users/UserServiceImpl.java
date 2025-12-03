package youtube.youtubeService.service.users;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import youtube.youtubeService.domain.Users;
import youtube.youtubeService.repository.users.UserRepository;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;


    @Override
    public List<Users> findAllUsers() {
        return userRepository.findAllUsers();
    }

    @Override
    public Optional<Users> getUserByUserId(String userId) {
        return Optional.ofNullable(userRepository.findByUserId(userId));
    }

    @Override
    @Transactional
    public void saveUser(Users user) {
        userRepository.saveUser(user);
    }

    @Override
    @Transactional
    public void deleteByUserIdRaw(String userId) {
        userRepository.deleteByUserIdRaw(userId);
        log.info("[User [{}] has been deleted from DB]", userId);
    }


}
