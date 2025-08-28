package youtube.youtubeService.handler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;
import youtube.youtubeService.api.YoutubeApiClient;
import youtube.youtubeService.domain.Users;
import youtube.youtubeService.service.GeoIpService;
import youtube.youtubeService.service.users.UserService;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.*;

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

/*
@Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {

        log.info("onAuthentication Success");
        if (authentication instanceof OAuth2AuthenticationToken oauthToken) {

            OAuth2AuthorizedClient authorizedClient = authorizedClientService.loadAuthorizedClient(
                    oauthToken.getAuthorizedClientRegistrationId(), oauthToken.getName());

            String accessToken = authorizedClient.getAccessToken().getTokenValue();
            String userId = oauthToken.getPrincipal().getName();    // 112735690496635663877, 107155055893692546350

            if(alreadyMember(userId)) {
                log.info("you are already a member of this service");
                // added 25.03.25 ~ 이거 며칠 지나서 로그인하니까 에러페이지 뜸 (디비에 pkc1088 리프레쉬 토큰은 있었음) : .getTokenValue() 여기서 접근하면 에러나는거임 null 일 떄
                Users user = userService.getUserByUserId(userId);

                // 보안에서 제거 후 재가입 시, 배포와 로컬 간 일관성 유지 시, 브라우저 캐시 삭제 후 시도 시
                if(authorizedClient.getRefreshToken() != null) {
                    String updatedRefreshToken = authorizedClient.getRefreshToken().getTokenValue();
                    log.info("발급된 리프레시 토큰이 null은 아니고 : {}", updatedRefreshToken);
                    log.info("DB에 저장된 기존 refreshToken : {}", user.getRefreshToken());
                    if(!user.getRefreshToken().equals(updatedRefreshToken)) {
                        saveUpdatedRefreshToken(user, updatedRefreshToken);
                        log.info("refreshToken Updated");
                    }
                } else {
                    log.info("발급된 리프레시 토큰이 null");
                    log.info("DB에 저장된 refreshToken : {}", user.getRefreshToken());
                }
            } else {
                String fullName = ((OidcUser) oauthToken.getPrincipal()).getFullName(); // pkc1088, whistle_missile 등
                String channelId;

                try {
                    channelId = youtubeApiClient.getChannelIdByUserId(accessToken); // channelId = getChannelIdByUserId(accessToken);;
                } catch (IOException | GeneralSecurityException e) {
                    log.info("{}", e.getMessage());
                    log.info("will send you /denied");
                    response.sendRedirect("/denied");
                    return;
                }

                String email = ((OidcUser) oauthToken.getPrincipal()).getEmail();

                if (isTemporaryEmail(email)) {
                    log.info("Temporary Email");
                    email = getRealEmail(email);
                }

                String refreshToken = authorizedClient.getRefreshToken() != null ? authorizedClient.getRefreshToken().getTokenValue() : null;
                String countryCode = geoIpService.getClientCountryCode(request);


    saveUsersToDatabase(userId, fullName, channelId, email, refreshToken, countryCode); // new member
        }
    }

        response.sendRedirect("/");// super.onAuthenticationSuccess(request, response, authentication);>simpleUrlAuthenticationSuccessHandler>AbstractAuthenticationTargetUrlRequestHandler 타고 들어가보면 기본 defaultTargetUrl = "/"; 이렇게 셋팅 되어서 에러 뜬거임.
    }
     void saveUpdatedRefreshToken(Users user, String updatedRefreshToken) {
        user.setRefreshToken(updatedRefreshToken);
        userService.saveUser(user); // 이 클래스엔 트잭 없으니까 애초에  User 가 영속 상태가 아님, 그래서 save 명시적으로 해줘야함, 그래야 mysql 에 반영됨 (userService 에 트잭 있어서 영속성 컨텍스트 내 반영이 됨, 트잭 시작 지점)
    }

    private boolean alreadyMember(String userId) {
        try{
            if(userId.equals(userService.getUserByUserId(userId).getUserId())) {
                log.info("Registered Member");
                return true;
            }
        } catch (RuntimeException e) {
            log.info("New Member");
        }
        return false;
    }

    public void saveUsersToDatabase(String id, String fullName, String channelId, String email, String refreshToken, String countryCode) {
        log.info("new member Id: {}", id);
        log.info("new member Name: {}", fullName);
        log.info("new member Email: {}", email);
        log.info("new member ChannelId: {}", channelId);
        log.info("new member Country Code: {}", countryCode);
        log.info("new member Refresh Token: {}", refreshToken);
        userService.saveUser(new Users(id, fullName, channelId, email, countryCode, refreshToken));
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

/*@Bean
public OAuth2AuthorizationRequestResolver customAuthorizationRequestResolver(ClientRegistrationRepository clientRegistrationRepository) {
    return new DefaultOAuth2AuthorizationRequestResolver(
            clientRegistrationRepository, "/oauth2/authorization"
    ) {
        @Override
        public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
            OAuth2AuthorizationRequest authorizationRequest = super.resolve(request);
            if (authorizationRequest != null) {
                // 추가 파라미터 설정
                return OAuth2AuthorizationRequest.from(authorizationRequest)
                        .additionalParameters(params -> {
                            params.put("access_type", "offline");
                            params.put("prompt", "consent");
                        })
                        .build();
            }
            return authorizationRequest;
        }
    };
}
*/
