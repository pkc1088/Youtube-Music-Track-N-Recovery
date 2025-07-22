package youtube.youtubeService.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import youtube.youtubeService.api.YoutubeApiClient;
import youtube.youtubeService.policy.SearchPolicy;
import youtube.youtubeService.repository.ActionLogRepository;
import youtube.youtubeService.repository.musics.MusicRepository;
import youtube.youtubeService.repository.musics.MusicRepositoryV1;
import youtube.youtubeService.repository.musics.SdjMusicRepository;
import youtube.youtubeService.repository.playlists.PlaylistRepository;
import youtube.youtubeService.repository.playlists.PlaylistRepositoryV1;
import youtube.youtubeService.repository.playlists.SdjPlaylistRepository;
import youtube.youtubeService.repository.users.SdjUserRepository;
import youtube.youtubeService.repository.users.UserRepository;
import youtube.youtubeService.repository.users.UserRepositoryV1;
import youtube.youtubeService.service.playlists.PlaylistService;
import youtube.youtubeService.service.playlists.PlaylistServiceV1;
import youtube.youtubeService.service.users.UserService;
import youtube.youtubeService.service.users.UserServiceV1;
import youtube.youtubeService.service.youtube.YoutubeService;
import youtube.youtubeService.service.youtube.YoutubeServiceV5;

//@RequiredArgsConstructor
@Configuration
public class SpringDataJpaConfig {

    private final SdjUserRepository sdjUserRepository;
    private final SdjPlaylistRepository sdjPlaylistRepository;
    private final SdjMusicRepository sdjMusicRepository;
    private final SearchPolicy searchPolicy;
    private final ActionLogRepository actionLogRepository;
    private final YoutubeApiClient youtubeApiClient;

    @Autowired
    public SpringDataJpaConfig(
            SdjUserRepository sdjUserRepository, SdjPlaylistRepository sdjPlaylistRepository, SdjMusicRepository sdjMusicRepository,
            @Qualifier("geminiSearchQuery") SearchPolicy searchPolicy, ActionLogRepository actionLogRepository, YoutubeApiClient youtubeApiClient) {
        this.sdjUserRepository = sdjUserRepository;
        this.sdjPlaylistRepository = sdjPlaylistRepository;
        this.sdjMusicRepository = sdjMusicRepository;
        this.searchPolicy = searchPolicy;
        this.actionLogRepository = actionLogRepository;
        this.youtubeApiClient = youtubeApiClient;
    }

    @Bean
    public YoutubeService youtubeService() {
        return new YoutubeServiceV5(playlistRepository(), musicRepository(), searchPolicy, actionLogRepository, youtubeApiClient);
    }

    @Bean
    public UserService userService() {
        return new UserServiceV1(userRepository(), actionLogRepository);
    }

    @Bean
    public PlaylistService playlistService() {
        return new PlaylistServiceV1(userRepository(), playlistRepository(), musicRepository(), youtubeApiClient);
    }

//    @Bean
//    public MusicService musicService() {
//        return new MusicServiceV1(playlistRepository(), musicRepository(), youtubeApiClient);
//    }

    @Bean
    public UserRepository userRepository() {
        return new UserRepositoryV1(sdjUserRepository);
    }

    @Bean
    public PlaylistRepository playlistRepository() {
        return new PlaylistRepositoryV1(sdjPlaylistRepository);
    }

    @Bean
    public MusicRepository musicRepository() {
        return new MusicRepositoryV1(sdjMusicRepository);
    }

}