package youtube.youtubeService.controller;

import com.google.api.services.youtube.model.Playlist;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import youtube.youtubeService.domain.ActionLog;
import youtube.youtubeService.domain.Playlists;
import youtube.youtubeService.domain.Users;
import youtube.youtubeService.service.ActionLogService;
import youtube.youtubeService.service.playlists.PlaylistService;
import youtube.youtubeService.service.users.UserService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.security.Principal;
import java.util.List;

@Slf4j
@Controller
@RequiredArgsConstructor
public class YoutubeControllerV5 {

    private final UserService userService;
    private final PlaylistService playlistService;
    private final ActionLogService actionLogService;

    @GetMapping("/denied")
    public String permissionDenied() { // Principal principal
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
                                            @RequestParam(name = "deselectedPlaylistIds", required = false) List<String> deselectedPlaylistIds) {

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
        List<ActionLog> logs = actionLogService.findByUserIdOrderByCreatedAtDesc(userId);
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
        cookie.setPath("/"); // 도메인 루트에 설정된 JSESSIONID 라면
        cookie.setMaxAge(0); // 즉시 만료
        response.addCookie(cookie);

        return "redirect:/";//        return "redirect:/logout";
    }

    @ResponseBody
    @GetMapping("/server-info")
    public String serverInfo(HttpSession session) {
        String serverIp;
        String serverHostName;
        String containerId;
        try {
            InetAddress serverHost = InetAddress.getLocalHost();
            serverIp = serverHost.getHostAddress();
            serverHostName = serverHost.getHostName();
            containerId = System.getenv("HOSTNAME"); // Cloud Run 컨테이너 ID
        } catch (UnknownHostException e) {
            serverIp = "Error getting IP";
            serverHostName = "Error getting Hostname";
            containerId = "Error getting ContainerId";
        }

        return "세션 ID: " + session.getId() + "<br>" +
                "서버 IP: " + serverIp + "<br>" +
                "서버 호스트명: " + serverHostName + "<br>" +
                "컨테이너명: " + containerId;
    }

    @ResponseBody
    @GetMapping("/instance-info")
    public String instanceInfo(HttpSession session) {
        String instanceId = null;
        instanceId = fetchInstanceId();

        return "세션 ID: " + session.getId() + "<br>" +
                "<b>요청 처리 인스턴스 ID: " + instanceId + "</b>";
    }

    private String fetchInstanceId() {
        String metadataUrl = "http://metadata.google.internal/computeMetadata/v1/instance/id";
        try {
            URL url = new URL(metadataUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("Metadata-Flavor", "Google");
            conn.setRequestMethod("GET");

            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    return in.readLine();
                }
            } else {
                return "메타데이터 서버 응답 실패: " + conn.getResponseCode();
            }
        } catch (Exception e) {
            return "인스턴스 ID 조회 실패: " + e.getMessage();
        }
    }

}

