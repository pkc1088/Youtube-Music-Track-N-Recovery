package youtube.youtubeService.handler;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Channel;
import com.google.api.services.youtube.model.ChannelListResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import youtube.youtubeService.api.YoutubeApiClient;
import youtube.youtubeService.domain.Users;
import youtube.youtubeService.service.users.UserService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;
import java.util.StringTokenizer;

@Slf4j
@Component
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UserService userService;
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final YoutubeApiClient youtubeApiClient;

    public OAuth2LoginSuccessHandler(UserService userService, OAuth2AuthorizedClientService authorizedClientService, YoutubeApiClient youtubeApiClient) {
        this.userService = userService;
        this.authorizedClientService = authorizedClientService;
        this.youtubeApiClient = youtubeApiClient;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {

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
                    email = getRealEmail(email, oauthToken);
                }
                String refreshToken = authorizedClient.getRefreshToken() != null ? authorizedClient.getRefreshToken().getTokenValue() : null;

                saveUsersToDatabase(userId, fullName, channelId, email, refreshToken); // new member
            }
        }

        response.sendRedirect("/");// super.onAuthenticationSuccess(request, response, authentication);>simpleUrlAuthenticationSuccessHandler>AbstractAuthenticationTargetUrlRequestHandler 타고 들어가보면 기본 defaultTargetUrl = "/"; 이렇게 셋팅 되어서 에러 뜬거임.
    }

//    @Transactional // 250721 주석함
    void saveUpdatedRefreshToken(Users user, String updatedRefreshToken) {
        user.setRefreshToken(updatedRefreshToken);
        userService.saveUser(user); // 얘 추가해줘야 mysql 에 반영됨 (userService 에 트잭 있어서 영속성 컨텍스트 내 반영이 됨, 트잭 시작지점)
        // Transactional : 어차피 외부 메서드(및 현재 클래스)에 트잭이 안걸려있었기에 프록시가 없어서, 외부 메서드가 호출해도 이게 안 먹혔던거임
        // 애초에  User 가 영속 상태가 아님(이 클래스엔 트잭 없으니까), 그래서 save 명시적으로 해줘야함
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

    public void saveUsersToDatabase(String id, String fullName, String channelId, String email, String refreshToken) {
        log.info("new member Id: {}", id);
        log.info("new member Name: {}", fullName);
        log.info("new member Email: {}", email);
        log.info("new member ChannelId: {}", channelId);
        log.info("new member Refresh Token: {}", refreshToken);
        userService.saveUser(new Users(id, fullName, channelId, email, refreshToken));
    }

    private boolean isTemporaryEmail(String email) {
        return email != null && email.endsWith("@pages.plusgoogle.com");        // 임시 이메일 주소인지 확인
    }

    private String getRealEmail(String email, OAuth2AuthenticationToken oauthToken) {
        StringTokenizer st = new StringTokenizer(email, "-");
        return st.nextToken() + "@gmail.com";
    }

    /*
    public String getChannelIdByUserId(String accessToken) throws IOException, GeneralSecurityException {
        GoogleCredential credential = new GoogleCredential().setAccessToken(accessToken);
        YouTube youtube = new YouTube.Builder(
                GoogleNetHttpTransport.newTrustedTransport(), GsonFactory.getDefaultInstance(), credential)
                .setApplicationName("youtube-channel-info").build();

        ChannelListResponse response;
        try {
            response = youtube.channels().list(Collections.singletonList("snippet")).setMine(true).execute();   // 현재 인증된 사용자의 채널 정보 조회
        } catch (GoogleJsonResponseException e) {
            String revokeUrl = "https://oauth2.googleapis.com/revoke?token=" + accessToken;
            RestTemplate restTemplate = new RestTemplate();
            restTemplate.postForEntity(revokeUrl, null, String.class);

            throw new IOException("Unauthorized 'youtube.force-ssl' or No channel found.. so revoked");// throw new RuntimeException("Unauthorized 'youtube.force-ssl' or No channel found.. so revoked");
        }

        Channel channel = response.getItems().get(0);
        return channel.getId();
    }
    */

}
/*
    public String getChannelIdByUserId(String accessToken) throws IOException, GeneralSecurityException {
        GoogleCredential credential = new GoogleCredential().setAccessToken(accessToken);
        YouTube youtube = new YouTube.Builder(
                GoogleNetHttpTransport.newTrustedTransport(), GsonFactory.getDefaultInstance(), credential)
                .setApplicationName("youtube-channel-info").build();

        ChannelListResponse response = youtube.channels().list(Collections.singletonList("snippet"))
                                        .setMine(true).execute();   // 현재 인증된 사용자의 채널 정보 조회
        if (response.getItems().isEmpty()) {
            throw new RuntimeException("No channel found for the authenticated user.");
        }
        Channel channel = response.getItems().get(0);
        return channel.getId();
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

//        if (authentication instanceof OAuth2AuthenticationToken) {
//            OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
//            // OAuth2 사용자 정보 추출
//            if (oauthToken.getPrincipal() instanceof OidcUser) {
//                OidcUser oidcUser = (OidcUser) oauthToken.getPrincipal();
//                // Access Token 추출
//                String accessToken = oidcUser.getIdToken().getTokenValue();
//                // Refresh Token 추출 (Google의 경우 refresh_token은 별도로 요청해야 함)
//                String refreshToken = null; // Google은 refresh_token을 기본적으로 제공하지 않음
//                // 사용자 정보 추출 (예: 이메일)
//                String email = oidcUser.getEmail();
//                // 데이터베이스에 저장
//                saveTokensToDatabase(email, accessToken, refreshToken);
//            }
//        }
//        // 기본 리다이렉트 동작 수행
//        super.onAuthenticationSuccess(request, response, authentication);




*/
