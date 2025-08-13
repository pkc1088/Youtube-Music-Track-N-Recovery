# <img src="https://github.com/user-attachments/assets/647e2441-5740-4443-a3f6-12a6502bd7dc" alt="Logo" width="35" style="vertical-align: text-bottom;">   FixMyPlaylist

<p align="center">  
  <br>Youtube 재생목록 속 음원의 비정상적인 삭제에 대한 <strong>자동 추적 및 복구 서비스</strong>.
  <br>Youtube 재생목록에서 갑작스럽게 사라진 음악들, 이제는 자동으로 복구하세요.
  <br><br>    
</p>
<p align="center">
  <img src="https://github.com/user-attachments/assets/64686b55-75b2-4581-aa0b-5b9e229b5d1b" alt="FixMyPlaylist UI" width="80%">
  <br><br>
</p>


## 📌 1. 프로젝트 개요

### 🔹 프로젝트 명
- **FixMyPlaylist**

### 🔹 서비스 주소
- [FixMyPlaylist 바로가기](https://youtube-track-recovery-71386729441.us-central1.run.app)

### 🔹 시연 영상
- [OAuth 구글 심사 시연 영상](https://www.youtube.com/watch?v=dqOrLUjCFic&t=64s)

### 🔹 서비스 이용 방법
1. 구글 계정으로 로그인
2. **최초 로그인 시 OAuth2 권한 요청 승인(youtube.force-ssl)**
3. 로그인 후 추적/복구 대상의 재생목록 등록
4. 이후, 사용자 관여 없이 **1일 1회 자동 추적 및 복구** 진행
5. 사용자의 재생목록 삭제/수정은 자동 반영
<br><br>

## 🎯 2. 프로젝트 소개

### 🔸 프로젝트 동기

- 유튜브를 통해 오래전부터 가수별로 재생목록을 만들고 음악을 추가하며 관리했습니다.

- 다음과 같은 이유로 재생목록 내에서 **음악 영상이 사라지는 현상**을 자주 목격했습니다.
  - **업로드자 영상 삭제 / 비공개 전환**
  - **저작권 침해 / 국가 차단 / 일부 공개 전환**
  - **채널 삭제**

- 대부분의 경우, 어떤 음악이 사라졌는지 **식별이 불가**했습니다.

- 수동으로 스크린샷 백업하는 것은 **물리적 한계**가 있었습니다.

- 이러한 **개인적인 문제**를 해결하고자 프로젝트를 진행하게 되었으며,

- 나아가 저와 비슷한 문제를 겪는 많은 이용자들과 가치를 공유하고자 **서비스화** 했습니다.

### 🔸 프로젝트 목표

- 재생 불가 음악 자동 식별 및 복구

- **전 세계 유사 사용자**에게도 동일 기능 제공

- **클릭 몇 번**으로 재생목록을 등록하면,
  - 1일 1회 자동 추적
  - 비정상 영상 발견 시 유사 음원으로 자동 복구
  - **완전 자동화된 서비스 제공**
<br><br>

## 🌐 3. 현행 서비스 조사

### ❌ 유사 서비스(2025.08.13 기준) 

- 비정상 처리된 영상을 클릭 후 비디오 아이디를 검색해 **직접 광범위하게 찾는 방법** 등은 소개가 됩니다.

- 하지만 정식으로 플레이리스트의 내부 목록들을 등록하고 **자동 추적 및 복구**를 서비스하는 곳은 **전무**합니다.

### ❓ 왜 Youtube Music이 아닌가?
- 타 음악 플랫폼(*Spotify, Apple Music* 등)
  - 정식 음원이 아닌 라이브, 콘서트 영상 등을 포함하지 못하며 앞도적으로 적은 수의 음악만 등록되어 있습니다.

- Youtube Music
  - 유튜브와 유튜브 뮤직은 기본적으로 호환됩니다.
  
  - 유튜브 뮤직도 유사한 기능 제공하지만, 다음과 같은 **제약**이 존재합니다.
    - **삭제/비공개 영상은 추적 불가**
    - **콘서트/라이브/비공식 영상 추가 불가**
    - **비공식 음원은 강제로 공식 영상으로 대체**
    - **Youtube Music은 유료 서비스**
    - **완전한 플레이리스트 커스텀화가 불가능**

- 즉, **'유튜브 + FixMyPlaylist'** 조합만이 다양한 유형의 음악을 무료로 자유롭고 안전하게 관리할 수 있습니다.
<br><br>

## ⚙️ 4. 프로젝트 스펙

- **진행 기간**
  - 전체 개발 기간: 2025.03.04 ~ 
  - 배포 기간: 2025.05.10 ~

- **개발자**
  - 개인 프로젝트

- **개발 환경**
  - **언어**: Java(JDK 17)
  - **빌드**: Gradle(8.10.2)
  - **프레임워크**: Spring Boot(3.4.4)
  - **ORM**: Spring Data JPA
  - **DB**: Cloud SQL(배포), MySQL(로컬), H2(테스트)
  - **Infra**: Google Cloud Run, Cloud Scheduler, Cloud Console
  - **APIs**: Youtube Data API V3, Gemini 2.5 Flash-Lite, MAXMIND GeoIP 
<br><br>

## 📝 5. 심사 

1. **Google OAuth2 민감 범위 데이터 심사 완료**
    - 'youtube.force-ssl' 정식 승인

2. **Youtube Data API V3 할당량 증설 완료**
    - 10,000 → **100,000** 상향(per day)
 
3. **Youtube API Compilance 심의 통과**
    - **'개인정보처리방침'** 및 **'서비스 이용약관'** 명세
<br><br>

## 🚀 6. 사용자 관점 주요 기능

<table>
  <tr>
    <!-- 왼쪽: 세로로 늘린 afterLogin -->
    <td>
      <img width="500" height="1400" alt="afterLogin" src="https://github.com/user-attachments/assets/b5b6c988-9d78-4a24-afc7-832bf0425579" />
    </td>
    <!-- 오른쪽: ui2, ui3, 복구내역 -->
    <td>
      <img width="500" alt="ui2" src="https://github.com/user-attachments/assets/4e0d8b21-2767-49e0-8f38-a6c2f0bb15de" /><br/>
      <img width="500" alt="ui3" src="https://github.com/user-attachments/assets/15aadbdf-be4a-4a77-a960-70429dec25f3" /><br/>
      <img width="500" alt="복구내역" src="https://github.com/user-attachments/assets/574640cf-19bc-4d82-9670-1a285d3a7971" />
    </td>
  </tr>
</table>

- 구글 로그인 연동

- '개인정보처리방침' 및 '서비스 이용약관' 확인 가능
 
- 로그인 후 고객의 플레이리스트 목록을 제공

- 관리 대상이 될 플레이리스트를 등록/제거

- 'Delete Account' 버튼을 통해 모든 사용자 관련 데이터 제거

- 복구 내역을 확인
<br><br>

## 📊 7. Sequence Diagram

### 1. OAuth2 로그인 및 회원가입
<p align="center">
  <img width="3840" height="2650" alt="OAuth2SequenceDiagram" src="https://github.com/user-attachments/assets/f9343027-9d4a-4bd4-ad67-a394d56e1de3" />
</p>

- 설명 추가

- '로그인 성공 핸들러'를 구축해 회원가입과 로그인을 통합

- 계정 선택 가능, `refresh token` 발급으로 고객 부재에도 `access token`을 발급해 자동 복구가 가능.

- `GeoIP`를 이용해 IP 기반 고객의 국가코드를 확보: '국가 차단' 비정상 영상의 판단 기준.

- `youtube.force-ssl` 미허용 시 세션 무효화, 토큰 무효화 후 리다이렉트.
<br><br>

### 2. 복구 시나리오
<img width="3840" height="2880" alt="RecoverSequenceDiagram" src="https://github.com/user-attachments/assets/f44bf0c1-754f-4300-8d22-8224a586a95d" />

- 설명 추가

- `Gemini` LLM 모델을 이용해 저장된 메타데이터를 기반으로 검색할 쿼리를 확보.

- API 할당량 소모를 최소화 하기 위해, 대체할 Video Id에 대해 당일 로그 기록을 확인하며 불필요한 `Youtube Search` 사용을 피함.

- 예외 케이스 대비
  - 유저 예외: 'Delete Account' 버튼이 아닌, '구글 보안 페이지'에서 계정 삭제.
  - 재생목록 예외: 재생목록을 해제하지 않고, 유튜브에서 재생목록을 제거한 경우.
  - 음악 예외: 추적 및 복구의 대상.
<br><br>

### 3. 할당량 소모량 & 최적화

- 케이스 별 사용량
  - 조건: 100개의 재생목록 등록, 각 재생목록은 100곡, 비정상 영상 1개 탐지
  - 등록:
  - 추적:
  - 복구:

- **Pagenation** 적용

- 당일 복구 내역에서 대체 영상 조회
<br><br>

## 💡 8. 아키텍처

### 1. Layered Architecture
<p align="center">
  <img width="800" height="500" alt="LayeredArchitecturePart1" src="https://github.com/user-attachments/assets/06f2d72d-9ef3-4bd7-ac66-afe66fc61572" />
</p>

- 설명 추가

- 'Controller→Service→Repository→Infra'의 단방향 구조

- 횡단 관심사 'Security 셋팅'를 이용해 인증된 유저를 판단 
<br><br>

### 2. Orchestration Service
<p align="center">
  <img width="800" height="400" alt="LayeredArchitecturePart2" src="https://github.com/user-attachments/assets/83fa26c9-a2ab-4cc5-95ec-6cb608b25c7f" />
</p>

- 설명 추가

- 트랜잭션 내의 서비스단 코드들을 총 관장

- 서비스 클래스들 간 상호 의존 문제를 방지하고 단방향 구조를 유지

- Outbox 패턴과 결합해 재시도 보상 시퀀스 제공

- Cloud Scheduler에 의해 트리거

 
<br><br>

## 🛠️ 9. 세부 기술

### 1. Outbox Pattern

<p align="center">
  <img width="700" height="550" alt="image" src="https://github.com/user-attachments/assets/92dacb13-29c3-472b-98eb-e8b34dc3658b" />
</p>


- Outbox Pattern을 도입한 이유:
  - DB 처리 작업과 API 추가/삭제 작업을 분리하지 않으면 롤백 발생 시 데이터 정합성 문제가 발생합니다.
  - DB 처리 작업과 API 호출을 순차적으로 처리할 시 트랜잭션 시간이 불필요하게 길어집니다.
  - Outbox로 관리할 시 정책에 따라 API 재시도가 가능합니다.

- Outbox 상태(PENDING, FAILED, SUCCESS, DEAD)를 업데이트해 멱등성을 보장했습니다.

- Outbox의 상태 업데이트 시: 새로운 트랜잭션(REQURIES_NEW) 사용
  - Outbox 이벤트 처리는 DB 최신화가 끝난 후 AFTER_COMMIT에 의해 실행됩니다.
  - 스프링의 트랜잭션 컨텍스트와 DB 트랜잭션 간 생명주기를 고려해 새로운 트랜잭션을 수행합니다.

- 비디오 처리 케이스 및 중복 영상 처리 정책
  | 케이스 | API(개수) | DB(개수) | Action(개수) |
  |:---:|:---:|:---:|:---:|
  | A | 정상(1) | 정상(1) | 유지 |
  | B | 정상(1) | 정상(2) | DB에서 삭제(1) |
  | C | 정상(2) | 정상(1) | DB에서 추가(1) |
  | D | 비정상(1) | 정상(1) | 복구(1) |
  | E | 비정상(2) | 정상(1) | 복구(2), DB 추가(1) |
  | F | 비정상(1) | 정상(2) | 복구(1), DB 삭제(1) |
  | G | 비정상(n) | 없음(0) | API 삭졔(n) 호출 |

<br><br>

### 2. ERD & JPA

<p align="center">
  <img width="800" height="500" alt="ERD" src="https://github.com/user-attachments/assets/e75fc94e-1533-4fb6-967a-220be616da80" />
</p>

- ManyToOne

- Lazy Loading

- DB FK Cascade 적용

<br><br>

## 🧩 9. 트러블슈팅 & 기술 과제

- **심사 대응**
  - 구글 OAuth2 심사 대응 전략 수립
  - 동의 화면, 개인정보처리방침, 서비스명 일치 검토

- **OAuth2**
  - `@RegisteredOAuth2AuthorizedClient` 사용
  - access/refresh token 처리
  - `access_type`, `prompt`, `scope` 설정 이슈
  - 고유 사용자 식별 및 DB 저장


- **Transaction 전파, REQURIES_NEW**
  - 설명 추가


- **Youtube 비정상 영상 분류**
  - 삭제/비공개 영상 vs "unavailable video"


- **Persistence & 동기화**
  - JPA 영속성 문제
  - DB cascade vs JPA cascade
  - 플레이리스트 수정 반영 로직


- **보안**
  - CSRF 토큰 관리
  - OAuth 로그인 캐시 이슈
<br><br>

## 📡 10. 기술 및 API

- **Youtube Data API V3**
  - 할당량 관리
  - PlaylistItems, Videos, Search API 활용

- **Gemini API**
  - 복구 시 유사 음원 추천에 활용

- **MaxMind Geo-Lite**
  - 최초 회원가입 시 국가코드 추출
<br><br>

## ✅ 11. 프로젝트 결론 및 리뷰

- 유튜브 사용자의 음악 자산 보호에 실질적 도움을 주는 서비스
- 수작업 백업의 한계를 자동화로 대체
- 실사용자 관점에서의 불편함을 **기술로 해결한 실용적인 예시**
<br><br>









