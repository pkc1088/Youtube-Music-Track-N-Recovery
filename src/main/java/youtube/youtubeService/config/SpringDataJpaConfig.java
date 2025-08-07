package youtube.youtubeService.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import youtube.youtubeService.api.YoutubeApiClient;
import youtube.youtubeService.policy.SearchPolicy;
import youtube.youtubeService.repository.ActionLogRepository;
import youtube.youtubeService.repository.OutboxRepository;
import youtube.youtubeService.repository.musics.MusicRepository;
import youtube.youtubeService.repository.musics.MusicRepositoryV1;
import youtube.youtubeService.repository.musics.SdjMusicRepository;
import youtube.youtubeService.repository.playlists.PlaylistRepository;
import youtube.youtubeService.repository.playlists.PlaylistRepositoryV1;
import youtube.youtubeService.repository.playlists.SdjPlaylistRepository;
import youtube.youtubeService.repository.users.SdjUserRepository;
import youtube.youtubeService.repository.users.UserRepository;
import youtube.youtubeService.repository.users.UserRepositoryV1;
import youtube.youtubeService.service.ActionLogService;
import youtube.youtubeService.service.musics.MusicService;
import youtube.youtubeService.service.musics.MusicServiceV1;
import youtube.youtubeService.service.outbox.OutboxEventPublisher;
import youtube.youtubeService.service.outbox.OutboxService;
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
    private final ActionLogRepository actionLogRepository;
    private final YoutubeApiClient youtubeApiClient;
    private final OutboxRepository outboxRepository;
    private final OutboxEventPublisher outboxEventPublisher;
    private final SearchPolicy searchPolicy;

    @Autowired
    public SpringDataJpaConfig(
            SdjUserRepository sdjUserRepository, SdjPlaylistRepository sdjPlaylistRepository, SdjMusicRepository sdjMusicRepository,
            ActionLogRepository actionLogRepository, OutboxRepository outboxRepository,
            YoutubeApiClient youtubeApiClient, OutboxEventPublisher outboxEventPublisher,
            @Qualifier("geminiSearchQuery") SearchPolicy searchPolicy) {
        this.sdjUserRepository = sdjUserRepository;
        this.sdjPlaylistRepository = sdjPlaylistRepository;
        this.sdjMusicRepository = sdjMusicRepository;
        this.searchPolicy = searchPolicy;
        this.actionLogRepository = actionLogRepository;
        this.youtubeApiClient = youtubeApiClient;
        this.outboxRepository = outboxRepository;
        this.outboxEventPublisher = outboxEventPublisher;
    }

    @Bean
    public YoutubeService youtubeService() {
        return new YoutubeServiceV5(musicRepository(), searchPolicy, actionLogService(), youtubeApiClient,
                playlistService(), musicService(), outboxService());
    }

    @Bean
    public UserService userService() {
        return new UserServiceV1(userRepository(), actionLogService());
    }

    @Bean
    public PlaylistService playlistService() {
        return new PlaylistServiceV1(userService(), playlistRepository(), youtubeApiClient, musicService());
    }

    @Bean
    public MusicService musicService() {
        return new MusicServiceV1(musicRepository(), searchPolicy, youtubeApiClient);
    }

    @Bean
    public OutboxService outboxService() {
        return new OutboxService(outboxRepository, outboxEventPublisher);
    }

    @Bean
    public ActionLogService actionLogService() {
        return new ActionLogService(actionLogRepository);
    }

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
