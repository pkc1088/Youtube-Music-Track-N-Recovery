package youtube.youtubeService.scheduler;

import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.PlaylistItem;
import com.google.api.services.youtube.model.PlaylistItemListResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import youtube.youtubeService.api.YoutubeApiClient;
import youtube.youtubeService.domain.Music;
import youtube.youtubeService.domain.Playlists;
import youtube.youtubeService.domain.Users;
import youtube.youtubeService.policy.SearchPolicy;
import youtube.youtubeService.repository.musics.MusicRepository;
import youtube.youtubeService.repository.playlists.PlaylistRepository;
import youtube.youtubeService.repository.users.UserRepository;
import youtube.youtubeService.service.musics.MusicService;
import youtube.youtubeService.service.outbox.OutboxEventHandler;
import youtube.youtubeService.service.playlists.PlaylistService;
import youtube.youtubeService.service.users.UserService;
import youtube.youtubeService.service.youtube.RecoverOrchestrationService;
import youtube.youtubeService.service.youtube.YoutubeService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class ManagementScheduler {

    @Value("${youtube.api.key}")
    private String apiKey;
    private final PlaylistService playlistService;
    private final YoutubeService youtubeService;
    private final UserService userService;
    private final UserRepository userRepository;
    private final SearchPolicy searchPolicy;
    private final PlaylistRepository playlistRepository;
    private final MusicRepository musicRepository;
    private final YoutubeApiClient youtubeApiClient;
    private final RecoverOrchestrationService recoverOrchestrationService;
    private final OutboxEventHandler outboxEventHandler;
    private final MusicService musicService;
    private final YouTube youtube;

    @Autowired
    public ManagementScheduler(PlaylistService playlistService, YoutubeService youtubeService, UserService userService,
                               UserRepository userRepository, @Qualifier("geminiSearchQuery") SearchPolicy searchPolicy,
                               MusicRepository musicRepository, PlaylistRepository playlistRepository, YoutubeApiClient youtubeApiClient,
                               RecoverOrchestrationService recoverOrchestrationService, OutboxEventHandler outboxEventHandler, MusicService musicService) {
        this.playlistService = playlistService;
        this.youtubeService = youtubeService;
        this.userService = userService;
        this.userRepository = userRepository;
        this.musicRepository = musicRepository;
        this.searchPolicy = searchPolicy;
        this.playlistRepository = playlistRepository;
        this.youtubeApiClient = youtubeApiClient;
        this.recoverOrchestrationService = recoverOrchestrationService;
        this.outboxEventHandler = outboxEventHandler;
        // @Qualifier("simpleSearchQuery")
        this.musicService = musicService;
        youtube = new YouTube.Builder(new NetHttpTransport(), new GsonFactory(), request -> {}).setApplicationName("youtube").build();
    }

//    @Scheduled(fixedRate = 50000, initialDelayString = "1000")
    public void allPlaylistsRecoveryOfAllUsersOutboxOrchestraTest() {
        log.info("auto scheduler activated");

        recoverOrchestrationService.allPlaylistsRecoveryOfAllUsers();
//        recoverOrchestrationService.allPlaylistsRecoveryOfOneParticularUserTest();

        log.info("auto scheduler done");
    }

//    @Scheduled(fixedRate = 50000, initialDelayString = "1000")
    public void LazyTest() {
        String userId = "101758050105729632425";
        Playlists playlistParam = playlistService.findAllPlaylistsByUserId(userId).get(0);

        log.info("auto scheduler activated");

        //youtubeService.lazyTest(playlistParam);

        log.info("auto scheduler done");
    }

    //    @Scheduled(fixedRate = 50000, initialDelayString = "1000")
    public void queryTest() {
        log.info("auto scheduler activated");

        String userId = "112735690496635663877";
        String playlistId = "PLNj4bt23RjfsNN7Id71Zehzs4GRretBru";
        String videoIdToDelete = "wtjro7_R3-4";
        Long pk = 500L;

//        Playlists playlist = playlistService.getPlaylistsByUserId(userId).get(0);
//        Music music = new Music(pk, "XzEoBAltBII", "The Manhattans - Kiss And Say GoodBye", "Whistle_Missile",
//                "The Manhattans,R&B,Soul,7th album", "just a test video", playlist);



//        log.info("[START] findByPlaylistId");
//        playlistRepository.findByPlaylistId(playlistId);
//        log.info("[DONE] findByPlaylistId");


//        log.info("[START] dBTrackAndRecoverPosition");
//        musicRepository.dBTrackAndRecoverPosition(videoIdToDelete, videoToRecover, pk);
//        log.info("[DONE] dBTrackAndRecoverPosition");


//        log.info("[START] findByUserId");
//        userRepository.findByUserId(userId);
//        log.info("[START] findByUserId");

        log.info("[START] playlistService.getPlaylistsByUserId(userId);");
        List<Playlists> playlists = playlistService.findAllPlaylistsByUserId(userId); // findByUser_UserId (query : 2회)
        for (Playlists p : playlists) {
            log.info(p.getPlaylistTitle());
            Users user = p.getUser();
            log.info("ususu");
        }
        log.info("playlists size : {}", playlists.size());
        log.info("[DONE] playlistService.getPlaylistsByUserId(userId);");
//
//        log.info("[START] musicService.findAllMusicByPlaylistId(playlistId)");
//        List<Music> musics = musicService.findAllMusicByPlaylistId(playlistId); // findByPlaylist_PlaylistId (query : 2회)
//        log.info("musics size : {}", musics.size());
//        log.info("[DONE] musicService.findAllMusicByPlaylistId(playlistId)");
//
//        log.info("[START] musicService.getMusicListFromDBThruMusicId(videoIdToDelete, playlistId)");
//        List<Music> duplicatedMusics = musicService.getMusicListFromDBThruMusicId(videoIdToDelete, playlistId); // findAllByVideoIdAndPlaylistId (query : 2회)
//        log.info("duplicatedMusics : {}", duplicatedMusics.size());
//        log.info("[DONE] musicService.getMusicListFromDBThruMusicId(videoIdToDelete, playlistId)");

        log.info("auto scheduler done");
    }

//    @Scheduled(fixedRate = 50000, initialDelayString = "1000")
    public void retryOutbox() {
        //outboxRetryPoller.retryFailedOutboxEvents();
    }

//    @Scheduled(fixedRate = 5000000, initialDelayString = "1000")
    public String getLocaleFromGoogle(String accessToken, Authentication authentication) {

        // 이것만 OAuth2LoginSuccessHandler 에 붙여주면 될 듯? <- ㄴㄴ GEO Service 이용해야함
        OAuth2AuthenticationToken authToken = (OAuth2AuthenticationToken) authentication;
        OAuth2User oAuth2User = authToken.getPrincipal();
        Map<String, Object> attributes = oAuth2User.getAttributes();
        String locale = (String) attributes.get("locale");
        String location = (String) attributes.get("location");

        System.out.println("locale = " + locale);
        System.out.println("location = " + location);
        // https://developers.google.com/people/api/rest/v1/people?hl=ko

        String url = "https://www.googleapis.com/oauth2/v3/userinfo";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        HttpEntity<String> entity = new HttpEntity<>(headers);
        RestTemplate restTemplate = new RestTemplate();

        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
        Map userInfo = response.getBody();
        return (String) userInfo.get("locale");

    }

//    @Scheduled(fixedRate = 5000000, initialDelayString = "1000")
    public void updateTest() throws IOException {
        String playlistId = "PLNj4bt23RjfsNN7Id71Zehzs4GRretBru";
        String userId  = "112735690496635663877";
        YouTube.PlaylistItems.List request = youtube.playlistItems().list(Collections.singletonList("snippet, id, status"));
        request.setKey(apiKey);
        request.setPlaylistId(playlistId);
        request.setMaxResults(50L);
        PlaylistItemListResponse response = request.execute();
        List<PlaylistItem> playlistItems = new ArrayList<>(response.getItems());

        System.out.println("0: " + playlistItems.get(0).getSnippet().getPlaylistId() + ", " + playlistItems.get(0).getId());
        System.out.println("3: " + playlistItems.get(3).getSnippet().getPlaylistId() + ", " + playlistItems.get(3).getId());
        // getId :
        // UExOajRidDIzUmpmc05ON0lkNzFaZWh6czRHUnJldEJydS5ENDU4Q0M4RDExNzM1Mjcy
        // UExOajRidDIzUmpmc05ON0lkNzFaZWh6czRHUnJldEJydS4yMDhBMkNBNjRDMjQxQTg1
        // 지웠다가 다시 추가하면 그 영상 playlist Item Id 도 바뀐다


        log.info("done..........");
    }

//    @Scheduled(fixedRate = 5000000, initialDelayString = "1000")
    public void multiVideoDetailsTest() throws IOException {

//        // 4개 곡 모두 다 videoId 확보 가능. 세부사항은 Video 로 확보할 것
//        String playlistId = "PLNj4bt23Rjfsm0Km4iNM6RSBwXXOEym74";//"PLNj4bt23Rjfs_0BztPAFHeyirxrHzGu5L";//
//        List<PlaylistItem> playlistItems = youtubeApiClient.getPlaylistItemListResponse(playlistId, 100L);
//        // playlist 예외 처리는 YOutubeService에선 알아서 해주고, PlaylistService는 필요하긴 함
//        List<String> videoIds = playlistItems.stream().map(item -> item.getSnippet().getResourceId().getVideoId()).toList();
//
//        log.info("-------------------{}-----------------------", videoIds.size());
//        playlistItems.forEach(item -> {
//            log.info("{}", item.getSnippet().getTitle());
//        });
//        log.info("----------------------------------------------------------");
//
//        List<Video> legalVideos = null; //safeFetchVideos(videoIds);
//
//        log.info("=== 결과({}) ===", legalVideos.size());
//        // legalVideos 널 체크 (DBAddAction 하기 전에)
//        for (Video video : legalVideos) {
//            log.info("정상 영상: {}, {}, {}", video.getSnippet().getTitle(), video.getId(), video.getStatus().getUploadStatus());
//        }
//        log.info("=== 완료 ===");
        /*System.out.println("----------------------------------------------------------");
        for (PlaylistItem video : response2) {
            System.out.println(video.getSnippet().getTitle() + " : " + video.getStatus().getPrivacyStatus());
            System.out.println("----------------------------------------------------------");
        }

        List<Video> legalVideos = new ArrayList<>();
        try {
            List<Video> items = request.execute().getItems();
            for (Video video : items) {
                if ("public".equals(video.getStatus().getPrivacyStatus()) && "processed".equals(video.getStatus().getUploadStatus())) {

                    if(video.getContentDetails().getRegionRestriction() != null
                            && video.getContentDetails().getRegionRestriction().getAllowed() != null
                            && !video.getContentDetails().getRegionRestriction().getAllowed().contains("KR")) {
                        log.info("KR is unavailable for this video (not allowed) : {}", video.getSnippet().getTitle());
                        continue;
                    }

                    if(video.getContentDetails().getRegionRestriction() != null
                            && video.getContentDetails().getRegionRestriction().getBlocked() != null
                            && video.getContentDetails().getRegionRestriction().getBlocked().contains("KR")) {
                        log.info("KR is unavailable for this video (blocked) : {}", video.getSnippet().getTitle());
                        continue;
                    }

                    log.info("Legal Video : {}", video.getSnippet().getTitle());
                    legalVideos.add(video);
                } else {
                    log.info("Illegal Video Filtered: {}", video.getSnippet().getTitle());
                }
            }
        } catch (Exception ex) {
            log.info("Illegal video found in this BATCH -> Fallback for each video");

            legalVideos = new ArrayList<>();
            for (PlaylistItem item : response2) {
                try{
                    String videoId = item.getSnippet().getResourceId().getVideoId();
                    log.info("개별 시도 : {}", videoId);
                    Video video = youtubeApiClient.getVideoDetailResponseWithFilter(videoId);
                    if(video != null) legalVideos.add(video);
                } catch (IOException e) {
                    log.info("A broken video is caught");
                }
            }
            // throw new IOException("Delete/Private/Not a Public Video");
        }

        System.out.println("start...");
        for (Video video : legalVideos) {
            System.out.println("정상 영상 세부사항 : " + video.getSnippet().getTitle() + ", " + video.getId() + ", " + video.getStatus().getUploadStatus());
        }
        System.out.println("done...");*/
    }

//    @Scheduled(fixedRate = 30000, initialDelayString = "1000")
    public void geminiTest() {
        Music musicToSearch = new Music();
        musicToSearch.setVideoTitle("a good music");
        musicToSearch.setVideoUploader("manhattan lover");
        musicToSearch.setVideoDescription("kiss and say good bye is a good music sung by The Manhattans");
        musicToSearch.setVideoTags("R&B, Soul");
        String text = searchPolicy.search(musicToSearch);
        System.out.println(text);
    }

}
