package youtube.youtubeService.repository.users;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import youtube.youtubeService.domain.Users;

@Repository
@Transactional
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
}

//    @Override
//    public Users findByUserEmail(String email) {
//        return repository.findByUserEmail(email);
//    }
//    @Override
//    public void updateRefreshTokenByLogin(String email, String refreshToken) {
//        Optional<Users> optionalUsers = Optional.ofNullable(repository.findByUserEmail(email));
//        if(optionalUsers.isPresent()) {
//            Users user = optionalUsers.get();
//            user.setRefreshToken(refreshToken);
//        }
//    }

//    public Users findByAccessToken(String email) {
//        return repository.findByAccessToken(email);
//    }

//    @Override
//    public void updateAccessTokenByRefreshToken(String refreshToken, String accessToken) {
//        Optional<Users> optionalUsers = Optional.ofNullable(repository.findByAccessToken(accessToken));
//        if(optionalUsers.isPresent()) {
//            Users user = optionalUsers.get();
////            user.setAccessToken(accessToken);
//            System.err.println("what?");
//            //user.setRefreshToken(refreshToken);
//        }
//    }