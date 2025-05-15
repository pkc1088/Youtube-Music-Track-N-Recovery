package youtube.youtubeService.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

@Slf4j
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final AuthenticationSuccessHandler oauth2LoginSuccessHandler;

    public SecurityConfig(AuthenticationSuccessHandler oauth2LoginSuccessHandler) {
        log.info("SecurityConfig Activated");
        this.oauth2LoginSuccessHandler = oauth2LoginSuccessHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/images/**", "/css/**", "/login").permitAll()  // 로그인되지 않은 사용자라면 /login으로 리다이렉트
                        .anyRequest().authenticated()                        // login 외 모든 요청은 인증 필요
                )
                .formLogin(form -> form
                        .loginPage("/login")                                 // 커스텀 로그인 페이지 설정
                        .permitAll()                                         // 로그인 페이지는 누구나 접근 가능
                        .defaultSuccessUrl("/welcome", true) // 로그인 성공 시 루트 페이지로 리다이렉트 "/" 이였는데 "/welcome"으로 바꿔봄
                )
                .oauth2Login(oauth2 -> oauth2
                        .loginPage("/login")                                 // OAuth2 로그인도 커스텀 로그인 페이지 사용
                        .defaultSuccessUrl("/welcome", true) // 로그인 성공 시 루트 페이지로 리다이렉트  "/" 이였는데 "/welcome"으로 바꿔봄
                        .successHandler(oauth2LoginSuccessHandler) // Added : 커스텀 핸들러 등록
                        .authorizationEndpoint(authorization -> authorization
                                .baseUri("/oauth2/authorization") // 기본 URI 설정
                                .authorizationRequestRepository(cookieAuthorizationRequestRepository())
                        )
                );

        return http.build();
    }

    @Bean
    public AuthorizationRequestRepository<OAuth2AuthorizationRequest> cookieAuthorizationRequestRepository() {
        return new HttpSessionOAuth2AuthorizationRequestRepository();
    }
}




/*
CustomAuthorizationRequestResolver 여기서 session으로 userId 받으면 null 떠서 에러나는 코드임

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final OAuth2LoginSuccessHandler oauth2LoginSuccessHandler;
    private final ClientRegistrationRepository clientRegistrationRepository;
    private final UserService userService; // 사용자 DB 조회용

    public SecurityConfig(OAuth2LoginSuccessHandler oauth2LoginSuccessHandler,
                          ClientRegistrationRepository clientRegistrationRepository,
                          UserService userService) {
        this.oauth2LoginSuccessHandler = oauth2LoginSuccessHandler;
        this.clientRegistrationRepository = clientRegistrationRepository;
        this.userService = userService;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/login").permitAll()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login").permitAll()
                )
                .oauth2Login(oauth2 -> oauth2
                        .loginPage("/login")
                        .successHandler(oauth2LoginSuccessHandler)
                        .authorizationEndpoint(auth -> auth
                                .authorizationRequestResolver(
                                        new CustomAuthorizationRequestResolver(clientRegistrationRepository, userService)
                                )
                        )
                );

        return http.build();
    }

}
*/


