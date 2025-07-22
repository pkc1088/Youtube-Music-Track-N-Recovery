package youtube.youtubeService.controller;

import com.google.api.services.youtube.model.Playlist;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import youtube.youtubeService.domain.ActionLog;
import youtube.youtubeService.domain.Playlists;
import youtube.youtubeService.domain.Users;
import youtube.youtubeService.repository.ActionLogRepository;
import youtube.youtubeService.repository.users.UserRepository;
import youtube.youtubeService.scheduler.ManagementScheduler;
import youtube.youtubeService.service.playlists.PlaylistService;
import youtube.youtubeService.service.users.UserService;
import youtube.youtubeService.service.youtube.YoutubeService;
import java.io.IOException;
import java.security.Principal;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequiredArgsConstructor
public class YoutubeControllerV5 {

    private final UserService userService;
    private final PlaylistService playlistService;
    private final ActionLogRepository actionLogRepository;

    @GetMapping("/denied")
    public String permissionDenied(Principal principal) {
        return "retry"; // session 끊는 행위 필요함
    }

    @GetMapping("/")
    public String index(Principal principal) {
        if (principal != null) {
            return "afterLogin"; // 로그인 사용자 전용 화면
        }
        return "login"; // 로그인 안한 사용자 화면
    }

    @GetMapping("/welcome")
    public String welcomePage() {
        return "welcome";
    }

    @GetMapping("/playlist")
    public String redirectToUserPlaylist(@AuthenticationPrincipal OAuth2User principal) {
        return "redirect:/playlist/" + principal.getName();
    }

    @GetMapping("/playlist/{userId}")
    public String userRegisterPlaylists(@PathVariable String userId, Model model) throws IOException {
        // 1. API로 사용자의 모든 플레이리스트를 가져옴
        List<Playlist> playlists = playlistService.getAllPlaylists(userId);
        // 2. DB에서 사용자가 이미 등록한 플레이리스트 목록을 가져옴
        List<Playlists> registeredPlaylistIdFromDB = playlistService.getPlaylistsByUserId(userId);
        List<String> registeredPlaylistIds = registeredPlaylistIdFromDB.stream().map(Playlists::getPlaylistId).toList();

        model.addAttribute("userId", userId);
        model.addAttribute("playlists", playlists);
        model.addAttribute("registeredPlaylistIds", registeredPlaylistIds);
        log.info("userRegisterPlaylists {}", userId);
        return "playlist_selection";
    }

    @PostMapping("/playlist/register")
    public String registerSelectedPlaylists(@RequestParam String userId,
                                            @RequestParam(name = "selectedPlaylistIds", required = false) List<String> selectedPlaylistIds,
                                            @RequestParam(name = "deselectedPlaylistIds", required = false) List<String> deselectedPlaylistIds,
                                            Model model) throws IOException {

        // 1. DB 에서 사용자가 이미 등록한 플레이리스트 목록을 가져옴
        List<Playlists> registeredPlaylistIdFromDB = playlistService.getPlaylistsByUserId(userId);
        List<String> registeredPlaylistIds = registeredPlaylistIdFromDB.stream().map(Playlists::getPlaylistId).toList();
        // 2. 중복된 플레이리스트는 제외하고 등록
        List<String> newlySelectedPlaylistIds = selectedPlaylistIds.stream().filter(playlistId -> !registeredPlaylistIds.contains(playlistId)).toList();

        if (!newlySelectedPlaylistIds.isEmpty()) {
            playlistService.registerPlaylists(userId, newlySelectedPlaylistIds);  // 중복되지 않는 플레이리스트만 등록
        }

        if (deselectedPlaylistIds != null && !deselectedPlaylistIds.isEmpty()) {
            playlistService.removePlaylistsFromDB(userId, deselectedPlaylistIds); // 체크 해제된 플레이리스트 DB에서 삭제
        }

        log.info("재생목록 등록 완료");
        return "redirect:/welcome";
    }

    @GetMapping("/recovery")
    public String redirectToRecoveryHistory(@AuthenticationPrincipal OAuth2User principal) {
        return "redirect:/recovery/" + principal.getName();
    }

    @GetMapping("/recovery/{userId}")
    public String searchRecoveryHistory(@PathVariable String userId, Model model) {
        // 1. userId로 ActionLogRepository 에서 내역 조회
        List<ActionLog> logs = actionLogRepository.findByUserIdOrderByCreatedAtDesc(userId);
        model.addAttribute("logs", logs);
        model.addAttribute("userId", userId);
        return "recovery_history";
    }

    @PostMapping("/delete")
    public String deleteAccount(@AuthenticationPrincipal OAuth2User principal,
                                HttpServletRequest request, HttpServletResponse response) {
        String userId = principal.getName();
        Users user = userService.getUserByUserId(userId);
        userService.deleteUser(user); // 토큰 revoke + DB 삭제
        SecurityContextHolder.clearContext();
        request.getSession().invalidate();
        Cookie cookie = new Cookie("JSESSIONID", null);
        cookie.setPath("/"); // 도메인 루트에 설정된 JSESSIONID라면
        cookie.setMaxAge(0); // 즉시 만료
        response.addCookie(cookie);

        return "redirect:/";//        return "redirect:/logout";
    }

}

//    @GetMapping("/whistleMissile/playlists") // - just for test
//    public String getPlaylists(Model model) throws IOException {
//        List<Playlist> playlists = playlistService.getAllPlaylists("UC6SN0-0k6z1fj5LmhYHd5UA"); // pkc1088
//        model.addAttribute("playlists", playlists);
//        return "playlists";
//    }

//    public List<Playlist> getAllPlaylists() throws IOException {
//        YouTube youtube = new YouTube.Builder(new NetHttpTransport(), new GsonFactory(), request -> {}).setApplicationName("youtube").build();
//        List<Playlist> allPlaylists = new ArrayList<>();
//        String nextPageToken = null;
//        do {
//            YouTube.Playlists.List request = youtube.playlists().list(Collections.singletonList("snippet, contentDetails"));
//
//            request.setKey;
//            request.setChannelId("UCSm9kYU0rHDamSQeoy_LBWg");
//            request.setMaxResults(50L); // API의 최대 허용값 (50)
//            request.setPageToken(nextPageToken); // 다음 페이지 토큰 설정
//            PlaylistListResponse response = request.execute();
//            allPlaylists.addAll(response.getItems());
//
//            nextPageToken = response.getNextPageToken();
//        } while (nextPageToken != null); // 더 이상 페이지가 없을 때까지 반복
//
//        return allPlaylists;
//    }



//    @GetMapping("/mySignup")
//    public String signup() {
//        return "redirect:/oauth2/authorization/google?prompt=consent&access_type=offline";
//    }
//    // 로그인 시 (prompt=none)
//    @GetMapping("/myLogin")
//    public String login() {
//        return "redirect:/oauth2/authorization/google?prompt=none";
//    }
//
//    @GetMapping("/memberRegister")
//    public String memberRegister() {
//        return "memberRegister";
//    }
//
//    @GetMapping("{playlistId}/getVideos") // - just for test
//    public String getVideos(@PathVariable String playlistId, Model model) {
//        try {
//            List<String> videos = youtubeService.getVideosFromPlaylist(playlistId);
//            model.addAttribute("getVideos", videos); // 비디오 목록만 모델에 담는거임
//        } catch (IOException e) {
//            model.addAttribute("error", "Failed to fetch videos from playlist - getvideos() method.");
//            e.printStackTrace();
//        }
//        return "getVideos";
//    }
//    @PostMapping("/TestAddVideoToPlaylist") // remove soon - just for test
//    public String TestAddVideoToPlaylist(@RequestParam String customerEmail, @RequestParam String playlistId, @RequestParam String videoId) {
//
//        Users users = userService.getUserByEmail(customerEmail);
//        System.out.println("TestAddVideoToPlaylist");
//        youtubeService.TestAddVideoToPlaylist(customerEmail, playlistId, videoId); // accesstoken -> customerEmail
//
//        return "redirect:/welcome";
//    }
//    @GetMapping("search") // 필요없음 이건 내부적으로 수행해야함
//    public String searchVideo(@RequestParam String keyword, Model model) throws IOException {
//        String result = youtubeService.searchVideo(keyword);
//        model.addAttribute("result", result);
//        return "search";
//    }
//
//    @PostMapping("/addVideoToPlaylist") // 필요없음 이건 내부적으로 수행해야함
//    public String addVideoToPlaylist(@RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient authorizedClient,
//                                     @RequestParam String playlistId, @RequestParam String videoId) {
//        String result =  youtubeService.addVideoToPlaylist(authorizedClient, playlistId, videoId);
//        return "redirect:/welcome";
//    }
//
//    @PostMapping("/deleteFromPlaylist") // 필요없음 이건 내부적으로 수행해야함
//    public String deleteFromPlaylist(@RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient authorizedClient,
//                                     @RequestParam String playlistId, @RequestParam String videoId) {
//        String result = youtubeService.deleteFromPlaylist(authorizedClient, playlistId, videoId);
//        return "redirect:/welcome";
//    }