- @Controller는 반환 값이 String 이면 뷰 이름으로 인식된다. 그래서 뷰를 찾고 뷰가 랜더링 된다. 
- @RestController는 반환 값으로 뷰를 찾는 것이 아니라, HTTP 메시지 바디에 바로 입력한다. 따라서 실행 결과로 ok 메세지를 받을 수 있다.  
- @Controller 대신에 @RestController 애노테이션을 사용하면, 해당 컨트롤러에 모두 *@ResponseBody 가 적용되는 효과가 있다. 따라서 뷰 템플릿을 사용하는 것이 아니라, HTTP 메시지 바디에 직접 데이터를 입력한다*.
- 참고로 @ResponseBody 는 클래스 레벨에 두면 전체 메서드에 적용되는데, @RestController 에노테이션 안에 @ResponseBody 가 적용되어 있다.
- *전체 과정 요약*
  1. 애플리케이션 실행: Spring Boot 애플리케이션을 실행합니다.
  2. 브라우저에서 접속: 브라우저에서 특정 경로(/oauth2/authorization/google)로 접속합니다.
  3. Google 로그인 페이지로 리다이렉트: Spring Security가 Google 로그인 페이지로 리다이렉트합니다.
  4. Google 로그인 완료: 사용자가 Google 계정으로 로그인합니다.
  5. 인증 코드 교환: Spring Security가 Google로부터 인증 코드를 받아 액세스 토큰으로 교환합니다.
  6. 리다이렉트: 로그인 성공 후 /로 리다이렉트됩니다.
  7. YouTube API 호출: 액세스 토큰을 사용하여 YouTube API를 호출하고, 재생목록에 영상을 추가합니다. (이미 로그인때 토큰 받았으므로 이땐 중복되게 로그인할 필요 없음)
- *SecurityConfig vs WebConfig*
    - `SecurityConfig`와 `WebConfig`는 Spring 애플리케이션에서 **보안**과 **요청 처리**를 담당하는 두 가지 다른 접근 방식입니다. 이 두 설정은 서로 다른 목적과 동작 방식을 가지고 있으며, 호출 순서와 원리에도 차이가 있습니다. 아래에서 각각의 동작 원리와 차이점을 설명드리겠습니다.
    - **`SecurityConfig` (Spring Security)**
        - `@EnableWebSecurity`가 붙은 `SecurityConfig`는 **Spring Security**를 사용하여 애플리케이션의 보안을 설정합니다. Spring Security는 **필터 기반**의 보안 처리를 제공하며, 다음과 같은 특징이 있습니다:
        - (1) **동작 원리**
            1. **필터 체인**:
                - Spring Security는 **필터 체인**을 통해 요청을 처리합니다.
                - `SecurityFilterChain`을 구성하여, 각 필터가 요청을 차례대로 처리합니다.
            2. **인증 및 인가**:
                - Spring Security는 요청에 대한 **인증(Authentication)**과 **인가(Authorization)**를 처리합니다.
                - 예: 로그인 여부 확인, 역할(ROLE) 기반 접근 제어 등.
            3. **기본 제공 필터**:
                - Spring Security는 기본적으로 여러 필터를 제공합니다 (예: `UsernamePasswordAuthenticationFilter`, `AnonymousAuthenticationFilter` 등).
                - 이러한 필터들은 요청을 처리하고, 보안 관련 작업을 수행합니다.
            4. **커스텀 설정**:
                - `HttpSecurity`를 사용하여 보안 규칙을 커스터마이징할 수 있습니다.
                - 예: 특정 경로에 대한 접근 제어, 로그인 페이지 설정, OAuth2 로그인 설정 등.
        - (2) **호출 순서**
            - Spring Security의 필터 체인은 **서블릿 필터보다 먼저 실행**됩니다.
            - Spring Security 필터 체인 내부에서 여러 필터가 순차적으로 실행됩니다.
    - **`WebConfig` (Spring MVC)**
      - `WebConfig`는 **Spring MVC**를 사용하여 요청 처리 방식을 설정합니다. 이 클래스는 **인터셉터(Interceptor)**와 **필터(Filter)**를 등록하여 요청을 처리합니다.
      - (1) **동작 원리**
          1. **인터셉터(Interceptor)**:
              - 인터셉터는 **컨트롤러 실행 전후**에 특정 작업을 수행합니다.
              - 예: 로그인 체크, 로깅, 요청/응답 수정 등.
          2. **필터(Filter)**:
              - 필터는 **서블릿 레벨**에서 요청과 응답을 처리합니다.
              - 예: 로깅, 인코딩 설정, 보안 처리 등.
          3. **커스텀 설정**:
              - `WebMvcConfigurer`를 구현하여 인터셉터를 등록하거나, `FilterRegistrationBean`을 사용하여 필터를 등록합니다.
      - (2) **호출 순서**
          - **필터(Filter)**는 **인터셉터(Interceptor)**보다 먼저 실행됩니다.
          - 필터는 서블릿 컨테이너 레벨에서 동작하며, 인터셉터는 Spring MVC 레벨에서 동작합니다.
    - **예시 시나리오**
        1. **요청 도착**:
            - 클라이언트가 `/welcome` 경로로 요청을 보냅니다.
        2. **Spring Security 필터 체인**:
            - Spring Security가 요청을 가로채서 인증 및 인가를 확인합니다.
            - 로그인되지 않은 사용자라면 `/login`으로 리다이렉트합니다.
        3. **서블릿 필터**:
            - Spring Security 필터 체인 이후, `LogFilter`와 `LoginCheckFilter`가 실행됩니다.
        4. **Spring MVC 인터셉터**:
            - 서블릿 필터 이후, `LogInterceptor`와 `LoginCheckInterceptor`가 실행됩니다.
        5. **컨트롤러**:
            - 인터셉터 이후, `YoutubeControllerV4`의 `welcomePage` 메서드가 실행됩니다.
- 