//package youtube.youtubeService.handler;
//
//import jakarta.servlet.http.HttpServletRequest;
//import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
//import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
//import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
//import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
//import org.springframework.stereotype.Component;
//import youtube.youtubeService.service.users.UserService;
//
//import java.util.HashMap;
//import java.util.Map;
//
//public class CustomAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {
//
//    private final OAuth2AuthorizationRequestResolver defaultResolver;
//    private final UserService userService; // 너의 유저 서비스
//
//    public CustomAuthorizationRequestResolver(ClientRegistrationRepository repo, UserService userService) {
//        this.defaultResolver = new DefaultOAuth2AuthorizationRequestResolver(repo, "/oauth2/authorization");
//        this.userService = userService;
//    }
//
//    @Override
//    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
//        OAuth2AuthorizationRequest originalRequest = defaultResolver.resolve(request);
//        return customizeRequest(originalRequest, request);
//    }
//
//    @Override
//    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
//        OAuth2AuthorizationRequest originalRequest = defaultResolver.resolve(request, clientRegistrationId);
//        return customizeRequest(originalRequest, request);
//    }
//
//    private OAuth2AuthorizationRequest customizeRequest(OAuth2AuthorizationRequest original, HttpServletRequest request) {
//        if (original == null) return null;
//
//        Map<String, Object> additionalParams = new HashMap<>(original.getAdditionalParameters());
//
//        // access_type=offline 항상 필요
//        additionalParams.put("access_type", "offline");
//        System.err.println("access_type=offline added");
//        // 로그인된 사용자 ID 세션에서 꺼내기
//        String userId = (String) request.getSession().getAttribute("userId");
//        System.err.println("userId : " + userId);
//        // DB에 사용자 없으면 prompt=consent 추가
//        if (userId == null || userService.getUserByUserId(userId) == null) {
//            System.err.println("prompt=consent added");
//            additionalParams.put("prompt", "consent");
//        }
//
//        return OAuth2AuthorizationRequest.from(original)
//                .additionalParameters(additionalParams)
//                .build();
//
//
//        /*
//        // 회원가입 버튼에서 온 경우라면 force prompt
//        if (request.getRequestURI().contains("/signup")) {
//            additionalParams.put("prompt", "consent");
//            additionalParams.put("access_type", "offline");
//        }
//
//        return OAuth2AuthorizationRequest.from(original)
//                .additionalParameters(additionalParams)
//                .build();
//
//         */
//    }
//}
