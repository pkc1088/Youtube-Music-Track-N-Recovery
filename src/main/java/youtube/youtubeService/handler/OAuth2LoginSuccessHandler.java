package youtube.youtubeService.handler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;
import youtube.youtubeService.api.YoutubeApiClient;
import youtube.youtubeService.domain.Users;
import youtube.youtubeService.domain.enums.QuotaType;
import youtube.youtubeService.service.GeoIpService;
import youtube.youtubeService.service.QuotaService;
import youtube.youtubeService.service.users.UserService;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.*;

@Slf4j
@Component
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final QuotaService quotaService;
    private final UserService userService;
    private final GeoIpService geoIpService;
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final YoutubeApiClient youtubeApiClient;
    private static final Set<String> ADMIN_USER_IDS = Set.of("112735690496635663877", "107155055893692546350");

    public OAuth2LoginSuccessHandler(QuotaService quotaService, UserService userService, GeoIpService geoIpService, OAuth2AuthorizedClientService authorizedClientService, YoutubeApiClient youtubeApiClient) {
        this.quotaService = quotaService;
        this.userService = userService;
        this.geoIpService = geoIpService;
        this.authorizedClientService = authorizedClientService;
        this.youtubeApiClient = youtubeApiClient;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {

        if (authentication instanceof OAuth2AuthenticationToken oauthToken) {

            OAuth2AuthorizedClient authorizedClient = authorizedClientService.loadAuthorizedClient(oauthToken.getAuthorizedClientRegistrationId(), oauthToken.getName());

            String accessToken = authorizedClient.getAccessToken().getTokenValue();
            String userId = oauthToken.getPrincipal().getName();
            Users.UserRole userRole = ADMIN_USER_IDS.contains(userId) ? Users.UserRole.ADMIN : Users.UserRole.USER;
            Users user = alreadyMember(userId).orElse(null); //Optional<Users> OptUser = alreadyMember(userId);

            if (user != null) {
                // 보안에서 제거 후 재가입 시, 배포와 로컬 간 일관성 유지 시, 브라우저 캐시 삭제 후 시도 시
                if(authorizedClient.getRefreshToken() != null) {
                    String updatedRefreshToken = authorizedClient.getRefreshToken().getTokenValue();
                    log.info("[New RefreshToken]: {}", updatedRefreshToken);
                    log.info("[DB RefreshToken]: {}", user.getRefreshToken());
                    if(!user.getRefreshToken().equals(updatedRefreshToken)) {
                        saveUpdatedRefreshToken(user, updatedRefreshToken);
                        log.info("[RefreshToken Updated]");
                    }
                } else {
                    log.info("[No New RefreshToken]");
                    log.info("[DB RefreshToken]: {}", user.getRefreshToken());
                }
            } else {
                String fullName = ((OidcUser) oauthToken.getPrincipal()).getFullName(); // pkc1088, whistle_missile 등
                String channelId;
                try {
                    // 악질 재가입 사보타지 고객 방지
                    if(!quotaService.checkAndConsumeLua(userId, QuotaType.SINGLE_SEARCH.getCost())) {
                        userService.revokeUser(accessToken); // 구글 보안 페이지에서 제거 필요함 (체크박스는 활성화 했을테니)
                        response.sendRedirect("/bad-user");
                        return;
                    }

                    channelId = youtubeApiClient.fetchChannelId(accessToken);

                } catch (IOException | GeneralSecurityException e) {
                    log.info("{}", e.getMessage());
                    log.info("[Will send you /denied]");
                    response.sendRedirect("/denied");
                    return;
                }

                String email = ((OidcUser) oauthToken.getPrincipal()).getEmail();

                if (isTemporaryEmail(email)) email = getRealEmail(email);

                String refreshToken = authorizedClient.getRefreshToken() != null ? authorizedClient.getRefreshToken().getTokenValue() : null;
                String countryCode = geoIpService.getClientCountryCode(request);

                saveUsersToDatabase(userId, userRole, fullName, channelId, email, refreshToken, countryCode);
            }
//
            // 1) Authorities 생성
            List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + userRole));
            // 2) 새로운 Authentication 생성
            OAuth2AuthenticationToken newAuth = new OAuth2AuthenticationToken(oauthToken.getPrincipal(), authorities, oauthToken.getAuthorizedClientRegistrationId());
            // 3) SecurityContext 에 저장
            SecurityContextHolder.getContext().setAuthentication(newAuth);
            // 5) (권장) 세션에 SecurityContext 강제 저장 — 서버가 세션 재생성/fixation 처리해도 안전하게
            HttpSession session = request.getSession();
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, SecurityContextHolder.getContext());
//
        }

        response.sendRedirect("/");// super.onAuthenticationSuccess(request, response, authentication);>simpleUrlAuthenticationSuccessHandler>AbstractAuthenticationTargetUrlRequestHandler 타고 들어가보면 기본 defaultTargetUrl = "/"; 이렇게 셋팅 되어서 에러 뜬거임.
    }

    private void saveUpdatedRefreshToken(Users user, String updatedRefreshToken) {
        user.setRefreshToken(updatedRefreshToken);
        userService.saveUser(user); // 이 클래스엔 트잭 없으니까 애초에  User 가 영속 상태가 아님, 그래서 save 명시적으로 해줘야함, 그래야 mysql 에 반영됨 (userService 에 트잭 있어서 영속성 컨텍스트 내 반영이 됨, 트잭 시작 지점)
    }

    private Optional<Users> alreadyMember(String userId) {
        Optional<Users> user = userService.getUserByUserId(userId);
        if(user.isPresent()) log.info("[Registered Member]");
        else log.info("[New Member]");

        return user;
    }

    private void saveUsersToDatabase(String id, Users.UserRole userRole, String fullName, String channelId, String email, String refreshToken, String countryCode) {
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
        return email != null && email.endsWith("@pages.plusgoogle.com");        // 임시 이메일 주소인지 확인
    }

    private String getRealEmail(String email) {
        StringTokenizer st = new StringTokenizer(email, "-");
        return st.nextToken() + "@gmail.com";
    }

}

/** OGCODE BEFORE 0903
@Slf4j
@Component
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UserService userService;
    private final GeoIpService geoIpService;
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final YoutubeApiClient youtubeApiClient;

    public OAuth2LoginSuccessHandler(UserService userService, GeoIpService geoIpService, OAuth2AuthorizedClientService authorizedClientService, YoutubeApiClient youtubeApiClient) {
        this.userService = userService;
        this.geoIpService = geoIpService;
        this.authorizedClientService = authorizedClientService;
        this.youtubeApiClient = youtubeApiClient;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {

        if (authentication instanceof OAuth2AuthenticationToken oauthToken) {

            OAuth2AuthorizedClient authorizedClient = authorizedClientService.loadAuthorizedClient(oauthToken.getAuthorizedClientRegistrationId(), oauthToken.getName());

            String accessToken = authorizedClient.getAccessToken().getTokenValue();
            String userId = oauthToken.getPrincipal().getName();
            Users.UserRole userRole = userId.equals("112735690496635663877") ? Users.UserRole.ADMIN : Users.UserRole.USER;
            Users user = alreadyMember(userId).orElse(null); //Optional<Users> OptUser = alreadyMember(userId);

            if (user != null) {
                // 보안에서 제거 후 재가입 시, 배포와 로컬 간 일관성 유지 시, 브라우저 캐시 삭제 후 시도 시
                if(authorizedClient.getRefreshToken() != null) {
                    String updatedRefreshToken = authorizedClient.getRefreshToken().getTokenValue();
                    log.info("[New RefreshToken]: {}", updatedRefreshToken);
                    log.info("[DB RefreshToken]: {}", user.getRefreshToken());
                    if(!user.getRefreshToken().equals(updatedRefreshToken)) {
                        saveUpdatedRefreshToken(user, updatedRefreshToken);
                        log.info("[RefreshToken Updated]");
                    }
                } else {
                    log.info("[No New RefreshToken]");
                    log.info("[DB RefreshToken]: {}", user.getRefreshToken());
                }
            } else {
                String fullName = ((OidcUser) oauthToken.getPrincipal()).getFullName(); // pkc1088, whistle_missile 등
                String channelId;

                try {
                    channelId = youtubeApiClient.getChannelIdByUserId(accessToken);
                } catch (IOException | GeneralSecurityException e) {
                    log.info("{}", e.getMessage());
                    log.info("[Will send you /denied]");
                    response.sendRedirect("/denied");
                    return;
                }

                String email = ((OidcUser) oauthToken.getPrincipal()).getEmail();

                if (isTemporaryEmail(email)) email = getRealEmail(email);

                String refreshToken = authorizedClient.getRefreshToken() != null ? authorizedClient.getRefreshToken().getTokenValue() : null;
                String countryCode = geoIpService.getClientCountryCode(request);

                saveUsersToDatabase(userId, userRole, fullName, channelId, email, refreshToken, countryCode);
            }
//
            // 1) Authorities 생성
            List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + userRole));
            // 2) 새로운 Authentication 생성
            OAuth2AuthenticationToken newAuth = new OAuth2AuthenticationToken(oauthToken.getPrincipal(), authorities, oauthToken.getAuthorizedClientRegistrationId());
            // 3) SecurityContext 에 저장
            SecurityContextHolder.getContext().setAuthentication(newAuth);
            // 5) (권장) 세션에 SecurityContext 강제 저장 — 서버가 세션 재생성/fixation 처리해도 안전하게
            HttpSession session = request.getSession();
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, SecurityContextHolder.getContext());
//
        }

        response.sendRedirect("/");// super.onAuthenticationSuccess(request, response, authentication);>simpleUrlAuthenticationSuccessHandler>AbstractAuthenticationTargetUrlRequestHandler 타고 들어가보면 기본 defaultTargetUrl = "/"; 이렇게 셋팅 되어서 에러 뜬거임.
    }

    private void saveUpdatedRefreshToken(Users user, String updatedRefreshToken) {
        user.setRefreshToken(updatedRefreshToken);
        userService.saveUser(user); // 이 클래스엔 트잭 없으니까 애초에  User 가 영속 상태가 아님, 그래서 save 명시적으로 해줘야함, 그래야 mysql 에 반영됨 (userService 에 트잭 있어서 영속성 컨텍스트 내 반영이 됨, 트잭 시작 지점)
    }

    private Optional<Users> alreadyMember(String userId) {
        Optional<Users> user = userService.getUserByUserId(userId);
        if(user.isPresent()) log.info("[Registered Member]");
        else log.info("[New Member]");

        return user;
    }

    private void saveUsersToDatabase(String id, Users.UserRole userRole, String fullName, String channelId, String email, String refreshToken, String countryCode) {
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
        return email != null && email.endsWith("@pages.plusgoogle.com");        // 임시 이메일 주소인지 확인
    }

    private String getRealEmail(String email) {
        StringTokenizer st = new StringTokenizer(email, "-");
        return st.nextToken() + "@gmail.com";
    }

}

 */
