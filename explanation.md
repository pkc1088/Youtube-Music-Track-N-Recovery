- gcloud builds submit --tag gcr.io/youtubeservice-457316/youtube-track-recovery


- gcloud run deploy youtube-track-recovery --image gcr.io/youtubeservice-457316/youtube-track-recovery --region us-central1 --platform managed --allow-unauthenticated --env-vars-file=./env.yaml --min-instances 0 --max-instances 10 --vpc-connector=fixmyplaylist-vpc-connect --timeout=8m


- Redis env.yml
  ```
  SPRING_DATA_REDIS_HOST: "10.165.129.67"
  SPRING_DATA_REDIS_PORT: "6379"
  SPRING_DATA_REDIS_TIMEOUT: "60000"
  SPRING_SESSION_TIMEOUT: "30m"
  SPRING_SESSION_STORE_TYPE: "redis"
  ```


- Outbox: State/activity Diagram
    ```
    flowchart LR
    A["Event 발생:<br>영상 추가/삭제"] --> B["Outbox INSERT:<br>status=PENDING"]
    B --> C["DB Commit"]
    C --> D["AFTER_COMMIT:<br>OutboxEventHandler 실행"]
    D --> E["OutboxProcessor:<br>YouTube Data API 호출"]
    E -- 성공 --> F["status=SUCCESS"]
    E -- 실패 --> G["status=FAILED"]
    G --> H["고객별 재시도"]
    H -- 성공 --> F
    H -- 실패 --> I["status=DEAD"]
    ```

- 복구시나리오: Sequence Diagram
    ```
    sequenceDiagram
        actor Scheduler as Cloud Scheduler
        participant Controller as ScheduledTaskController
        participant Orchestra as RecoverOrchestrationService
        participant UserService as UserService
        participant YoutubeService as YoutubeService
        participant PlaylistService as PlaylistService
        participant MusicService as MusicService
        participant Outbox as OutboxEventHandler
        actor Gemini as GeminiAPI
        actor YouTube as YoutubeAPI
        autonumber
        Scheduler ->> Controller: POST /track-recovery
        Controller ->> Controller: API-Key 요청 보안 검사
        Controller ->> Orchestra: 복구 오케스트레이션 시작
        loop For each Users
            Orchestra ->> UserService: Access Token 발급
            alt Access Token 발급 실패: 고객 탈퇴
                UserService -->> Orchestra: 유저 삭제 후 다음 유저로 스킵
            else Access Token 발급 성공
                Orchestra ->> PlaylistService: Playlists 조회(DB)
                loop For each Playlists
                    Orchestra ->> YoutubeService: 최신화 및 복구 지점 진입
                    YoutubeService ->> YouTube: Playlists 조회(API)
                    alt Playlists 조회(API) 실패: 고객의 삭제
                        YoutubeService ->> PlaylistService: Playlist 제거(DB)
                        YoutubeService -->> Orchestra: 다음 Playlist로 스킵
                    else Playlists 조회(API) 성공
                        YoutubeService ->> MusicService: Music 조회(DB)
                        YoutubeService ->> YouTube: Video 상태 조회(API)
                        alt 정상 Video
                            opt[고객 임의의 Video 추가/삭제]
                                YoutubeService ->> MusicService: 최신화(DB)
                            end
                        else 비정상 Video
                            YoutubeService ->> Gemini: 대체 영상 검색
                            YoutubeService ->> MusicService: 대체 영상으로 업데이트(DB)
                        end
                    end
                    YoutubeService ->> Outbox: @AFTER_COMMIT
                    Outbox ->> YouTube: 대체 영상 추가(API)
                    Outbox ->> YouTube: 비정상 영상 제거(API)
                end
            end
            Orchestra ->> Outbox: 'FAILED' Outbox 재시도
            Outbox ->> YouTube: 재시도(API)
        end
        Orchestra -->> Controller: 종료
        Controller -->> Scheduler: 200 OK
    ```

- OAUTH2: Sequence Diagram
    ```
    sequenceDiagram
        actor User as 사용자
        participant Frontend as Frontend
        participant Backend as Backend
        participant Google as Google OAuth2
        participant Geo as GeoIPService
        participant DB as Repository
        User ->> Frontend: 로그인 버튼 클릭
        Frontend ->> Backend: /oauth2/authorization/google 요청
        Backend ->> Google: OAuth2 인증 요청 (prompt=select_account, access_type=offline)
        Google ->> User: 로그인 & 동의 화면
        User ->> Google: 동의
        Google ->> Backend: Authorization Code 전달 (redirect-uri)
        Backend ->> Google: Token 요청
        Google->>Backend: Access Token(신규 가입자는 Refresh Token 동반)
        Backend->>Google: 사용자 정보 요청
        Google->>Backend: 사용자 정보 반환
        Backend->>DB: 사용자 정보로 기존 가입 여부 조회
        alt 기존 회원
            Backend->>Frontend: 로그인 성공 응답 (기존 Refresh Token 유지)
        else 신규 회원
            alt force-ssl 동의
                Backend->>Geo: IP 기반 국가코드 추출
                Backend->>DB: Refresh Token 저장
                Backend->>Frontend: 회원가입 성공 응답
            else force-ssl 미동의
                Backend->>Google: 사용자 권한 해제
                Backend->>Frontend: /denied 리다이렉트 후 세션, 쿠키 삭제
            end
        end
        Frontend-->>User: /welcome
    ```

- ERD: ERD Diagram
    ```
    erDiagram
        direction LR
        USERS {
            varchar user_id PK ""
            varchar user_name ""
            varchar user_channel_id ""
            varchar user_email ""
            varchar country_code ""
            varchar refresh_token ""
        }
        PLAYLISTS {
            varchar playlist_id PK ""
            varchar playlist_title ""
            varchar service_type ""
            varchar user_id FK ""
        }
        MUSIC {
            bigint id PK ""
            varchar video_id ""
            varchar video_title ""
            varchar video_uploader ""
            varchar video_description ""
            varchar video_tags ""
            varchar playlist_id FK ""
        }
        ACTION_LOG {
            bigint id PK ""
            varchar user_id ""
            varchar playlist_id ""
            varchar action_type ""
            varchar target_video_id ""
            varchar target_video_title ""
            varchar sorce_video_id ""
            varchar source_video_title ""
            datetime createdAt ""
        }
        Outbox {
            bigint id PK ""
            varchar actionType ""
            varchar accessToken ""
            varchar userId ""
            varchar playlistId ""
            varchar videoId ""
            varchar status ""
            int retryCount ""
            datetime lastAttemptedAt ""
            datetime createdAt ""
            datetime updatedAt ""
        }
        USERS||--o{PLAYLISTS:"ON DELETE CASCADE"
        PLAYLISTS||--o{MUSIC:"ON DELETE CASCADE"
    ```