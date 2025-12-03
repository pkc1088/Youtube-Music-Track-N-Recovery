package youtube.youtubeService.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisIndexedHttpSession;

@Slf4j
@Configuration
@EnableWebSecurity
@EnableRedisIndexedHttpSession
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final AuthenticationSuccessHandler oauth2LoginSuccessHandler;

    public SecurityConfig(AuthenticationSuccessHandler oauth2LoginSuccessHandler) {
        log.info("SecurityConfig Activated");
        this.oauth2LoginSuccessHandler = oauth2LoginSuccessHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf
                    .ignoringRequestMatchers("/api/scheduler/**") // CSRF 비활성화
            )
            .authorizeHttpRequests(authorize -> authorize
                    .requestMatchers("/js/**", "/images/**", "/css/**", "/", "/api/scheduler/**", "/google9cce108361f8ecd7.html",
                                    "/privacy-policy.html", "/terms-of-service.html", "/checkboxNotActivated", "/channelNotFound", "/bad-user")
                    .permitAll()
                    .anyRequest().authenticated()
            )
            .oauth2Login(oauth2 -> oauth2
                    .loginPage("/")
                    .successHandler(oauth2LoginSuccessHandler)
                    .authorizationEndpoint(authorization -> authorization
                            .baseUri("/oauth2/authorization")
                            .authorizationRequestRepository(cookieAuthorizationRequestRepository())
                    )
            )
            .logout(logout -> logout
                    .logoutUrl("/logout")
                    .logoutSuccessUrl("/")
                    .invalidateHttpSession(true)
                    .deleteCookies("JSESSIONID")
            );

        return http.build();
    }

    @Bean
    public AuthorizationRequestRepository<OAuth2AuthorizationRequest> cookieAuthorizationRequestRepository() {
        return new HttpSessionOAuth2AuthorizationRequestRepository();
    }
}



