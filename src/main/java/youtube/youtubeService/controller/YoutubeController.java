package youtube.youtubeService.controller;

import com.google.api.services.youtube.model.Playlist;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import youtube.youtubeService.domain.ActionLog;
import youtube.youtubeService.domain.Playlists;
import youtube.youtubeService.domain.Users;
import youtube.youtubeService.dto.ActionLogDto;
import youtube.youtubeService.dto.PlaylistRegisterRequestDto;
import youtube.youtubeService.dto.PlaylistRegistrationResultDto;
import youtube.youtubeService.dto.UserRegisterPlaylistsResponseDto;
import youtube.youtubeService.service.ActionLogService;
import youtube.youtubeService.service.playlists.PlaylistService;
import youtube.youtubeService.service.users.UserService;

import java.io.IOException;
import java.security.Principal;
import java.util.List;
import java.util.Optional;

@Slf4j
@Controller
@RequiredArgsConstructor
public class YoutubeController {

    private final UserService userService;
    private final PlaylistService playlistService;
    private final ActionLogService actionLogService;

    @GetMapping("/denied")
    public String permissionDenied() {
        return "retry"; // session 끊는 행위 필요함
    }

    @GetMapping("/bad-user")
    public String quotaExceeded() { // revoke 는 핸들러에서 이미 처리함. 단순 페이지 제공임
        return "quota-exceeded";
    }

    @GetMapping("/")
    public String index(Principal principal) {
        return principal != null ? "afterLogin" : "login";
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
        UserRegisterPlaylistsResponseDto dto = playlistService.userRegisterPlaylists(userId);
        model.addAttribute("dto", dto);
        return "playlist_selection";
    }

    @PostMapping("/playlist/register")
    public String registerPlaylists(@ModelAttribute PlaylistRegisterRequestDto request, RedirectAttributes redirectAttributes) {
        PlaylistRegistrationResultDto dto = playlistService.registerPlaylists(request);
        redirectAttributes.addFlashAttribute("playlistResult", dto); // flash attribute에 담아서 리다이렉트 시 전달
        return "redirect:/welcome";
    }

    @GetMapping("/recovery")
    public String redirectToRecoveryHistory(@AuthenticationPrincipal OAuth2User principal) {
        return "redirect:/recovery/" + principal.getName();
    }

    @GetMapping("/recovery/{userId}")
    public String searchRecoveryHistory(@PathVariable String userId, Model model) {
        ActionLogDto dto = actionLogService.findByUserIdOrderByCreatedAtDesc(userId);
        model.addAttribute("dto", dto);
        return "recovery_history";
    }

    @PostMapping("/delete")
    public String deleteAccount(@AuthenticationPrincipal OAuth2User principal, HttpServletRequest request, HttpServletResponse response) {
        userService.deleteUserAccount(principal.getName());

        SecurityContextHolder.clearContext();
        request.getSession().invalidate();
        Cookie cookie = new Cookie("JSESSIONID", null);
        cookie.setPath("/"); // 도메인 루트에 설정된 JSESSIONID 라면
        cookie.setMaxAge(0); // 즉시 만료
        response.addCookie(cookie);

        return "redirect:/";
    }

}

/** OGCODE BEFORE 0903
 @Slf4j
 @Controller
 @RequiredArgsConstructor
 public class YoutubeController {

 private final UserService userService;
 private final PlaylistService playlistService;
 private final ActionLogService actionLogService;

 @GetMapping("/denied")
 public String permissionDenied() {
 return "retry"; // session 끊는 행위 필요함
 }

 @GetMapping("/")
 public String index(Principal principal) {
 return principal != null ? "afterLogin" : "login";
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
 UserRegisterPlaylistsResponseDto dto = playlistService.userRegisterPlaylists(userId);
 model.addAttribute("dto", dto);
 return "playlist_selection";
 }

 @PostMapping("/playlist/register")
 public String registerPlaylists(@ModelAttribute PlaylistRegisterRequestDto request) {
 playlistService.registerPlaylists(request);
 return "redirect:/welcome";
 }

 @GetMapping("/recovery")
 public String redirectToRecoveryHistory(@AuthenticationPrincipal OAuth2User principal) {
 return "redirect:/recovery/" + principal.getName();
 }

 @GetMapping("/recovery/{userId}")
 public String searchRecoveryHistory(@PathVariable String userId, Model model) {
 ActionLogDto dto = actionLogService.findByUserIdOrderByCreatedAtDesc(userId);
 model.addAttribute("dto", dto);
 return "recovery_history";
 }

 @PostMapping("/delete")
 public String deleteAccount(@AuthenticationPrincipal OAuth2User principal, HttpServletRequest request, HttpServletResponse response) {
 userService.deleteUserAccount(principal.getName());

 SecurityContextHolder.clearContext();
 request.getSession().invalidate();
 Cookie cookie = new Cookie("JSESSIONID", null);
 cookie.setPath("/"); // 도메인 루트에 설정된 JSESSIONID 라면
 cookie.setMaxAge(0); // 즉시 만료
 response.addCookie(cookie);

 return "redirect:/";
 }

 }

 */