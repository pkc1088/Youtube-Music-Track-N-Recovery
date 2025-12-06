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
import youtube.youtubeService.dto.internal.PlaylistCacheDto;
import youtube.youtubeService.dto.request.PlaylistRegisterRequestDto;
import youtube.youtubeService.dto.response.ActionLogResponseDto;
import youtube.youtubeService.dto.response.PlaylistRegisterResponseDto;
import youtube.youtubeService.dto.response.UserRegisterPlaylistsResponseDto;
import youtube.youtubeService.handler.CustomOAuth2User;
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

    @GetMapping("/welcome")
    public String welcomePage(@AuthenticationPrincipal CustomOAuth2User principal, Model model) {
        List<Playlists> playlists = playlistService.findAllByUserIdWithUserFetch(principal.getName()); //playlistService.findAllPlaylistsByUserIdsOrderByLastChecked(Collections.singletonList(principal.getName()));
        model.addAttribute("dto", playlists);
        return "welcome";
    }

    @GetMapping("/playlist")
    public String redirectToUserPlaylist(@AuthenticationPrincipal CustomOAuth2User principal) {
        return "redirect:/playlist/" + principal.getName();
    }

    @GetMapping("/playlist/{userId}")
    @PreAuthorize("#userId == authentication.principal.name")
    public String userRegisterPlaylists(@PathVariable String userId, @AuthenticationPrincipal CustomOAuth2User principal, Model model) {
        UserRegisterPlaylistsResponseDto dto = playlistRegistrationOrchestratorService.processPlaylistSelection(userId, principal.getChannelId());
        model.addAttribute("dto", dto);
        return "playlist_selection";
    }

    @PostMapping("/playlist/register")
    public String registerPlaylists(@ModelAttribute PlaylistRegisterRequestDto request, @AuthenticationPrincipal CustomOAuth2User principal, RedirectAttributes redirectAttributes) {
        PlaylistRegisterResponseDto dto = playlistRegistrationOrchestratorService.processPlaylistRegistration(request, principal.getCountryCode());
        redirectAttributes.addFlashAttribute("playlistResult", dto);
        return "redirect:/welcome";
    }

    @GetMapping("/recovery")
    public String redirectToRecoveryHistory(@AuthenticationPrincipal CustomOAuth2User principal) {
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
    public String deleteAccount(@AuthenticationPrincipal CustomOAuth2User principal, HttpServletRequest request, HttpServletResponse response) {

        userTokenService.withdrawUser(principal.getName());

        SecurityContextHolder.clearContext();
        request.getSession().invalidate();
        Cookie cookie = new Cookie("JSESSIONID", null);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);

        return "redirect:/";
    }

}