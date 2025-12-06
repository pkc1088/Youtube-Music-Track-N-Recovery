package youtube.youtubeService.handler;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;
import youtube.youtubeService.api.YoutubeApiClient;
import youtube.youtubeService.domain.Users;
import youtube.youtubeService.domain.enums.QuotaType;
import youtube.youtubeService.exception.youtube.ChannelNotFoundException;
import youtube.youtubeService.exception.youtube.YoutubeApiException;
import youtube.youtubeService.exception.youtube.YoutubeNetworkException;
import youtube.youtubeService.service.GeoIpService;
import youtube.youtubeService.service.QuotaService;
import youtube.youtubeService.service.users.UserService;
import youtube.youtubeService.service.users.UserTokenService;

import java.io.IOException;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final Set<String> ADMIN_USER_IDS = Set.of("112735690496635663877", "107155055893692546350");
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final UserTokenService userTokenService;
    private final YoutubeApiClient youtubeApiClient;
    private final GeoIpService geoIpService;
    private final QuotaService quotaService;
    private final UserService userService;


    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        String channelId;
        String countryCode;

        if (authentication instanceof OAuth2AuthenticationToken oauthToken) {

            OAuth2AuthorizedClient authorizedClient = authorizedClientService.loadAuthorizedClient(oauthToken.getAuthorizedClientRegistrationId(), oauthToken.getName());

            String accessToken = authorizedClient.getAccessToken().getTokenValue();

            Set<String> allScopes = authorizedClient.getAccessToken().getScopes();
            if (!allScopes.contains("https://www.googleapis.com/auth/youtube.force-ssl")) {
                log.info("[Insufficient Permission: youtube.force-ssl]");
                revokeAndInvalidate(request, response, accessToken, "/checkboxNotActivated");
                return;
            }

            String userId = oauthToken.getPrincipal().getName();
            Users.UserRole userRole = ADMIN_USER_IDS.contains(userId) ? Users.UserRole.ADMIN : Users.UserRole.USER;
            Users user = alreadyMember(userId).orElse(null);

            if (user != null) {
                // 등록된 유저 (보안에서 제거 후 재가입 시, 배포와 로컬 간 일관성 유지 시, 브라우저 캐시 삭제 후 시도 시)
                channelId = user.getUserChannelId();
                countryCode = user.getCountryCode();

                log.info("[DB RefreshToken]: {}", user.getRefreshToken());

                OAuth2RefreshToken refreshTokenObj = authorizedClient.getRefreshToken();
                String refreshTokenValue = (refreshTokenObj != null) ? refreshTokenObj.getTokenValue() : null;

                if(refreshTokenValue != null) {
                    log.info("[New RefreshToken]: {}", refreshTokenValue);

                    if(user.getRefreshToken() == null || !user.getRefreshToken().equals(refreshTokenValue)) {
                        saveUpdatedRefreshToken(user, refreshTokenValue);
                        log.info("[RefreshToken Updated]");
                    }
                }

            } else {
                // 신규 등록 유저
                String fullName = ((OidcUser) oauthToken.getPrincipal()).getFullName(); // pkc1088, whistle_missile 등

                try {
                    // 악질 재가입 사보타지 고객 방지
                    if(!quotaService.checkAndConsumeLua(userId, QuotaType.SINGLE_SEARCH.getCost())) {
                        revokeAndInvalidate(request, response, accessToken, "/bad-user");
                        return;
                    }

                    channelId = youtubeApiClient.fetchChannelId(accessToken);

                } catch (ChannelNotFoundException | YoutubeApiException | YoutubeNetworkException e) {
                    log.info("{}", e.getMessage());
                    revokeAndInvalidate(request, response, accessToken, "/channelNotFound");
                    return;
                }

                String email = ((OidcUser) oauthToken.getPrincipal()).getEmail();
                if (isTemporaryEmail(email)) email = getRealEmail(email);

                String refreshToken = authorizedClient.getRefreshToken() != null ? authorizedClient.getRefreshToken().getTokenValue() : null;

                countryCode = geoIpService.getClientCountryCode(request);

                saveUsersToDatabase(userId, userRole, fullName, channelId, email, countryCode, refreshToken);
            }

            // Authorities 생성
            List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + userRole));
            // 커스텀 OAuth2User 추가함 (countryCode, channelId 추가)
            CustomOAuth2User customPrincipal = new CustomOAuth2User(oauthToken.getPrincipal(), countryCode, channelId);
            // 새로운 Authentication 생성
            OAuth2AuthenticationToken newAuth = new OAuth2AuthenticationToken(customPrincipal, authorities, oauthToken.getAuthorizedClientRegistrationId());
            // SecurityContext 에 저장
            SecurityContextHolder.getContext().setAuthentication(newAuth);
            // 세션에 SecurityContext 강제 저장 — 서버가 세션 재생성/fixation 처리해도 안전
            HttpSession session = request.getSession();
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, SecurityContextHolder.getContext());
        }

        response.sendRedirect("/");
    }

    private void revokeAndInvalidate(HttpServletRequest request, HttpServletResponse response, String accessToken, String redirectUrl) throws IOException {

        userTokenService.revokeUser(accessToken);

        SecurityContextHolder.clearContext();
        request.getSession().invalidate();
        Cookie cookie = new Cookie("JSESSIONID", null);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);

        response.sendRedirect(redirectUrl);
    }

    private void saveUpdatedRefreshToken(Users user, String updatedRefreshToken) {
        user.updateRefreshToken(updatedRefreshToken);
        userService.saveUser(user);
    }

    private Optional<Users> alreadyMember(String userId) {
        Optional<Users> user = userService.getUserByUserId(userId);
        if(user.isPresent()) log.info("[Registered Member]");
        else log.info("[New Member]");

        return user;
    }

    private void saveUsersToDatabase(String id, Users.UserRole userRole, String fullName, String channelId, String email, String countryCode, String refreshToken) {
        log.info("[New Member Id]: {}", id);
        log.info("[New Member Role]: {}", userRole);
        log.info("[New Member Name]: {}", fullName);
        log.info("[New Member Email]: {}", email);
        log.info("[New Member ChannelId]: {}", channelId);
        log.info("[New Member CountryCode]: {}", countryCode);
        log.info("[New Member RefreshToken ]: {}", refreshToken);
        userService.saveUser(new Users(id, userRole, fullName, channelId, email, countryCode, refreshToken));
    }

    private boolean isTemporaryEmail(String email) {
        return email != null && email.endsWith("@pages.plusgoogle.com");
    }

    private String getRealEmail(String email) {
        StringTokenizer st = new StringTokenizer(email, "-");
        return st.nextToken() + "@gmail.com";
    }

}

