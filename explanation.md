# Youtube music playlist track & recovery service
Youtube 음원 파일의 비정상적인 삭제에 대한 추적 및 복구 서비스

![img.png](src/main/resources/static/images/ui1.png)
# 1. 프로젝트 소개
- **프로젝트 명**
    - Youtube 음원 파일의 비정상적인 삭제에 대한 추적 및 복구 서비스

- **서비스 주소**
    - [Youtube Playlist Track & Recovery Service](https://youtube-track-recovery-71386729441.us-central1.run.app)

- **시연 url**
    - Youtube 시연
    - [OAuth 구글 심사](https://www.youtube.com/watch?v=dqOrLUjCFic&t=64s)


- **프로젝트 동기**
    - 저는 유튜브 뮤직이 출시되기 이전부터 유튜브로 음악 강상을 했습니다.
    - 가수별로 재생목록을 만들고 해당 가수의 음악을 유튜브에서 찾아 추가한 뒤 감상했습니다.
    - 하지만 유튜브에서는 다음과 같은 상황에서 해당 음원(비디오)를 이용할 수 없게 되며 그런 비정상 처리된 음악이 어떤 음악인지 알 방법이 없었습니다
    - 유튜브 재생목록이 표시하는 비정상적인 접근 케이스
        - 업로드자의 영상 삭제
        - 업로드자의 영상 비공개 전환
        - 업로드자 채널의 삭제
        - 저작권 침해로 인한 삭제
        - 특정 국가 차단
        - 개인 정보 보호 등에 의한 이용 불가 등
    - 저는 이러한 상황을 방지하기 위해 스크린샷으로 일일이 재생목록 및 그 내부를 찍어가며 백업을 해서 관리했습니다.
    - 하지만 제가 관리하는 플레이리스트가 500개, 음악이 3000개가 넘어가면서 물리적으로 이 음악들을 백업하는 방식에 한계를 느꼈습니다.


- **프로젝트 목표**
    - 저는 이러한 상황에서 제가 공들여 추가한 음악들 중 어떤 음악이 이용할 수 없게 됐는지 알 수 있길 원했습니다.
    - 그리고 해당 음악과 관련된 정보를 이용해 다시 제 재생목록에 복구해야겠다는 필요성을 절실히 느꼈습니다.
    - 저 혼자만을 위한 이런 기능을 구축하는 것은 매우 쉬운 일입니다. 하지만 저는 저와 비슷한 고민을 하고 필요성을 느끼는 사람들이 전세계에 많이 존재한다고 판단했습니다.
    - 그래서 사람들이 본인의 유튜브 재생목록을 제 서비스에 단 한번의 클릭으로 등록만 하면 해당 음원을 저장해 차후에 앞서 언급한 비정상적인 상황에서 복구가 필요할 때, 추적하고 올바른 음원으로 사용자의 플레이리스트에 복구하는 완전히 자동화된 서비스를 제공하고자 결심했습니다.


- **왜 Youtube Music이 아닌가?**
    - 유튜브 뮤직에서는 위와 같은 상황을 대비해 이용할 수 없는 unlisted 영상들을 자동으로 이용 가능한 동일한 음악으로 대체하는 기능을 제공해줍니다
    - 하지만 유튜브 뮤직은 다음과 같은 결정적인 결점이 존재합니다
        - 비공개/삭제된 영상은 여전히 Youtube Music에서 자동으로 제거됩니다.
        - 유튜브 뮤직은 커스텀이 불가합니다.
            - 유튜브 저작권에 저촉되지 않는 원본 음악 (예를 들면 ~offical, ~Vevo 등)과 같은 음악만 담도록 강제합니다
            - 콘서트 라이브 음악 혹은 유튜브 자체적으로 음악이라 판단되지 않은 영상을 추가할 수 없습니다.
            - 위와 같은 상황에서 유튜브 뮤직은 Alias 혹은 Redirect 등의 방법으로 유튜브에서 사용자가 추가한 음악을 공식 음악으로 대체되도록 강제합니다.
    - 유튜브 뮤직은 유료입니다.
        - 반면 유튜브는 무료입니다.


- **서비스 이용 방법**
    - 구글 계정 로그인을 합니다
    - 최초 회원가입의 경우 서드 파티 및 앱 서비스로서 youtube.force-ssl에 대한 권한(OAuth2 인증)을 허용해 주셔야 합니다.
    - 이후의 로그인은 단순히 구글 로그인만 진행하면 됩니다
    - 로그인이 되면 해당 유튜브 채널과 관련된 플레이리스트 목록을 볼 수 있습니다
    - 해당 목록 중 추적 및 복구 서비스에 등록하고자 하는 플레이리스트를 추가해주면 됩니다
    - 향후 서비스를 업데이트 해서 `추적 및 단순 알림 서비스`와 `추적 및 복구 서비스`로 구분해 사용자의 편의성을 증대시킬 계획입니다
    - 이제 고객이 관여할 일은 전혀 없습니다.
    - 고객이 등록한 플레이리스트는 제 DB에 저장되고 1일 1회 추적 및 복구 서비스를 제공 받습니다
    - 등록된 플레이리스트에 대해 사용자 임의로 음악을 추가하고 삭제하는 것은 전혀 문제가 없습니다.
    - 실제 플레이리스트를 제거해도 예외처리가 되며 자동으로 관리 대상에서 제거됩니다.
    - 사용자의 플레이리스트 수정은 1일 1회 플레이리스트 최신화를 통해 반영되며 해당 음악들 역시 추적 및 복구 대상으로 설정됩니다.


# 2. 프로젝트 스펙
- **제작 기간** : 2025.03.04 - 진행 중


- **팀원 및 역할**
    - 개인 프로젝트


- **개발 환경**
    - 언어 : Java
    - 프레임워크 : Spring Boot
    - 데이터베이스
        - 배포 : Cloud SQL
        - 로컬 : MySql, H2
    - 인프라 & 서비스 : Cloud Run, Cloud Scheduler


- **디렉토리 구조**
``` 
 youtubeService
    ├── config
    ├── controller
    ├── domain
    ├── handler
    ├── policy
    │   ├── gemini
    │   └── simple
    ├── repository
    │   ├── musics
    │   ├── playlists
    │   └── users
    ├── scheduler
    └── service
        ├── musics
        ├── playlists
        ├── users
        └── youtube
```

# 3. 프로젝트 주요 기능

![img.png](src/main/resources/static/images/ui2.png)

![img.png](src/main/resources/static/images/ui3.png)



# 4. 트러블 슛팅
- OAuth2 권한 획득 및 이용
    - @RegisteredOAuth2AuthorizedClient과 access & refresh token 차이
    - access_type과 prompt
    - 구글 고유 ID로 회원 구분
    - 브라우저 캐싱 삭제와 OAuth2 재인증, 로컬 vs 배포 차이
    - SecurityConfig와 loginSuccessHandler()

- 구글 OAuth 심사


- 유튜브 비정상 영상의 properties 정의
    - 삭제 및 비공개 영상과 'unavailable video'의 차이


- scheduler, controller, service, repository, domain 의존성


- 브라우저 캐시 삭제 후 refreshToken이 mysql에 업데이트 되지 않는 버그 (JPA 영속성)


- CSRF 토큰


- JPA cascade vs DB cascade


- @Scheduled 에러

# 5. 기술 및 API 분석
- Youtube Data API V3 할당량 정책


# 6. 프로젝트 결론 및 리뷰