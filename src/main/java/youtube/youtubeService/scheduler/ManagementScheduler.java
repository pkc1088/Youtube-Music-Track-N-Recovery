package youtube.youtubeService.scheduler;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.PlaylistItem;
import com.google.api.services.youtube.model.PlaylistItemSnippet;
import com.google.api.services.youtube.model.ResourceId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import youtube.youtubeService.domain.Music;
import youtube.youtubeService.domain.Playlists;
import youtube.youtubeService.domain.Users;
import youtube.youtubeService.policy.SearchPolicy;
import youtube.youtubeService.repository.users.UserRepository;
import youtube.youtubeService.service.playlists.PlaylistService;
import youtube.youtubeService.service.users.UserService;
import youtube.youtubeService.service.youtube.YoutubeService;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class ManagementScheduler {

    private final PlaylistService playlistService;
    private final YoutubeService youtubeService;
    private final UserService userService;
    private final UserRepository userRepository;
    private final SearchPolicy searchPolicy;

    @Autowired
    public ManagementScheduler(PlaylistService playlistService, YoutubeService youtubeService, UserService userService,
                               UserRepository userRepository, @Qualifier("geminiSearchQuery") SearchPolicy searchPolicy) {
        this.playlistService = playlistService;
        this.youtubeService = youtubeService;
        this.userService = userService;
        this.userRepository = userRepository;
        this.searchPolicy = searchPolicy;
        // @Qualifier("simpleSearchQuery")
    }

//    @Scheduled(fixedRate = 50000, initialDelayString = "1000")  //    @Transactional
    public void allPlaylistsRecoveryOfAllUsersTest() throws IOException {
        log.info("auto scheduler activated");

        // 0. 전체 유저 목록에서 순차적으로 유저를 뽑기
        List<Users> users = userRepository.findAllUsers();

        for (Users user : users) {
            String userId = user.getUserId(); // String userId  = "112735690496635663877";
            log.info("userId : {}", userId);
            // 1. 유저 아이디로 accessToken 발급
            String accessToken = userService.getNewAccessTokenByUserId(userId);
            // 2. 유저 아이디로 조회한 모든 플레이리스트 & 음악을 디비에서 뽑아서 복구 시스템 가동
            List<Playlists> playListsSet = playlistService.getPlaylistsByUserId(userId);
            for (Playlists playlist : playListsSet) {
                log.info("{} start", playlist.getPlaylistTitle());
                // playlist 자체가 제거된 경우 예외처리 필요
                try {
                    youtubeService.fileTrackAndRecover(userId, playlist.getPlaylistId(), accessToken);
                } catch (IOException e) {
                    log.info("scheduler caught and then move to next playlist");
                }
            }

        }

        log.info("auto scheduler done");
    }

//    @Scheduled(fixedRate = 50000, initialDelayString = "1000")
    public void allPlaylistsRecoveryOfOneParticularUserTest() throws IOException {
        log.info("auto scheduler activated");
        // 0. 전체 유저 목록에서 순차적으로 유저를 뽑아 오는 시나리오 있다 치고
        String userId  = "112735690496635663877";
        // 1. 유저 아이디로 accessToken 발급
        String accessToken = userService.getNewAccessTokenByUserId(userId);
        // 2. 유저 아이디로 조회한 모든 플레이리스트 & 음악을 디비에서 뽑아서 복구 시스템 가동
        List<Playlists> playListsSet = playlistService.getPlaylistsByUserId(userId);
        for (Playlists playlist : playListsSet) {
            log.info("{} start", playlist.getPlaylistTitle());
            // playlist 자체가 제거된 경우 예외처리 필요
            try {
                youtubeService.fileTrackAndRecover(userId, playlist.getPlaylistId(), accessToken);
            } catch (IOException e) {
                log.info("scheduler caught and then move to next playlist");
            }
        }

        log.info("auto scheduler done");
    }



    //    @Scheduled(fixedRate = 30000, initialDelayString = "2000")
    public void geminiTest() {
        Music musicToSearch = new Music();
        musicToSearch.setVideoTitle("kiss and say goodbye");
        musicToSearch.setVideoUploader("the manhattans");
        String text = searchPolicy.search(musicToSearch);
        System.out.println(text);
    }

//    @Scheduled(fixedRate = 30000, initialDelayString = "1000")
    public void updateTest() throws IOException {
        String playlistId = "PLkympg9D413fblkvg_kUOlGplar3C1S62"; // cbm google
        String accessToken = userService.getNewAccessTokenByUserId("116727139333472663777");
        String videoId = "o6vCn2sBbBE";
        addTemp(accessToken, playlistId, videoId, 4L); // 포지션 잘 맞춰야함
    }

    public void addTemp(String accessToken, String playlistId, String videoId, long videoPosition) {
        try {
            GoogleCredential credential = new GoogleCredential().setAccessToken(accessToken);
            YouTube youtube = new YouTube.Builder(GoogleNetHttpTransport.newTrustedTransport(), GsonFactory.getDefaultInstance(), credential)
                    .setApplicationName("youtube-add-playlist-item")
                    .build();

            ResourceId resourceId = new ResourceId();
            resourceId.setKind("youtube#video");
            resourceId.setVideoId(videoId);
            PlaylistItemSnippet playlistItemSnippet = new PlaylistItemSnippet();
            playlistItemSnippet.setPlaylistId(playlistId);
            playlistItemSnippet.setResourceId(resourceId);
            playlistItemSnippet.setPosition(videoPosition); // added
            PlaylistItem playlistItem = new PlaylistItem();
            playlistItem.setSnippet(playlistItemSnippet);

            YouTube.PlaylistItems.Insert request = youtube.playlistItems().insert(Collections.singletonList("snippet"), playlistItem);
            PlaylistItem response = request.execute();
            log.info("completely added video({}) to {}", videoId, playlistId);

        } catch (IOException e) {
            e.printStackTrace();
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

}

//    //    @Scheduled(fixedRate = 30000, initialDelayString = "1000")
//    public void allAddScenarioToOnePerson() throws IOException {
//        log.info("auto scheduler activated");
//        // 1. 회원 등록은 이미 했음 (회원가입 시)
//        String userId  = "112735690496635663877";
//        // 2. 플레이리스트 & 음악 모두 등록
////        playlistService.registerPlaylists(userId); selectedPlaylists 로 줘야함 (변경됐음)
//        log.info("auto scheduler done");
//    }