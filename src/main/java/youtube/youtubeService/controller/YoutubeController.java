package youtube.youtubeService.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import youtube.youtubeService.domain.Playlists;
import youtube.youtubeService.dto.request.PlaylistRegisterRequestDto;
import youtube.youtubeService.dto.response.ActionLogResponseDto;
import youtube.youtubeService.dto.response.PlaylistRegisterResponseDto;
import youtube.youtubeService.dto.response.UserRegisterPlaylistsResponseDto;
import youtube.youtubeService.service.ActionLogService;
import youtube.youtubeService.service.playlists.PlaylistRegistrationOrchestratorService;
import youtube.youtubeService.service.playlists.PlaylistService;
import youtube.youtubeService.service.users.UserTokenService;

import java.security.Principal;
import java.util.Collections;
import java.util.List;

@Slf4j
@Controller
@RequiredArgsConstructor
public class YoutubeController {

    private final PlaylistRegistrationOrchestratorService playlistRegistrationOrchestratorService;
    private final ActionLogService actionLogService;
    private final UserTokenService userTokenService;
    private final PlaylistService playlistService;


    @GetMapping("/channelNotFound")
    public String channelNotFound() {
        return "channelNotFound";
    }

    @GetMapping("/checkboxNotActivated")
    public String permissionDenied() {
        return "checkboxNotActivated";
    }

    @GetMapping("/bad-user")
    public String quotaExceeded() {
        log.info("[A Bad User - Quota Thief]");
        return "quota-exceeded";
    }

    @GetMapping("/")
    public String index(Principal principal) {
        return principal != null ? "afterLoginIndex" : "beforeLoginIndex";
    }

//    @GetMapping("/welcome")
//    public String welcomePage() {
//        return "welcome";
//    }

    @GetMapping("/welcome")
    public String welcomePage(@AuthenticationPrincipal OAuth2User principal, Model model) {
        List<Playlists> sortedPlaylists = playlistService.findAllPlaylistsByUserIdsOrderByLastChecked(Collections.singletonList(principal.getName()));
        model.addAttribute("dto", sortedPlaylists);
        return "welcome";
    }

    @GetMapping("/playlist")
    public String redirectToUserPlaylist(@AuthenticationPrincipal OAuth2User principal) {
        return "redirect:/playlist/" + principal.getName();
    }

    @GetMapping("/playlist/{userId}")
    @PreAuthorize("#userId == authentication.principal.name")
    public String userRegisterPlaylists(@PathVariable String userId, Model model) {
        UserRegisterPlaylistsResponseDto dto = playlistRegistrationOrchestratorService.processPlaylistSelection(userId);
        model.addAttribute("dto", dto);
        return "playlist_selection";
    }

    @PostMapping("/playlist/register")
    public String registerPlaylists(@ModelAttribute PlaylistRegisterRequestDto request, RedirectAttributes redirectAttributes) {
        PlaylistRegisterResponseDto dto = playlistRegistrationOrchestratorService.processPlaylistRegistration(request);
        redirectAttributes.addFlashAttribute("playlistResult", dto); // flash attribute 에 담아서 리다이렉트 시 전달
        return "redirect:/welcome";
    }

    @GetMapping("/recovery")
    public String redirectToRecoveryHistory(@AuthenticationPrincipal OAuth2User principal) {
        return "redirect:/recovery/" + principal.getName();
    }

    @GetMapping("/recovery/{userId}")
    @PreAuthorize("#userId == authentication.principal.name")
    public String searchRecoveryHistory(@PathVariable String userId, Model model) {
        ActionLogResponseDto dto = actionLogService.findByUserIdOrderByCreatedAtDesc(userId);
        model.addAttribute("dto", dto);
        return "recovery_history";
    }

    @PostMapping("/delete")
    public String deleteAccount(@AuthenticationPrincipal OAuth2User principal, HttpServletRequest request, HttpServletResponse response) {

        userTokenService.withdrawUser(principal.getName());// == principal.getAttribute("sub"));

        SecurityContextHolder.clearContext();
        request.getSession().invalidate();
        Cookie cookie = new Cookie("JSESSIONID", null);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);

        return "redirect:/";
    }

}