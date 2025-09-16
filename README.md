# <img src="https://github.com/user-attachments/assets/647e2441-5740-4443-a3f6-12a6502bd7dc" alt="Logo" width="35" style="vertical-align: text-bottom;">   FixMyPlaylist

<p align="center">  
  <br>Youtube 재생목록 속 음원의 비정상적인 삭제에 대한 <strong>자동 추적 및 복구 서비스</strong>.
  <br>Youtube 재생목록에서 갑작스럽게 사라진 음악들, 이제는 자동으로 복구하세요.
  <br><br>    
</p>
<p align="center">
  <img width="1037" height="1307" alt="MainUI1" src="https://github.com/user-attachments/assets/f52cb05e-f225-4326-a2f2-f584eb3bc398" />
<!--   <img src="https://github.com/user-attachments/assets/64686b55-75b2-4581-aa0b-5b9e229b5d1b" alt="FixMyPlaylist UI" width="80%"> -->
  <br><br>
</p>

## ☑️ 0. Index
- [1. Project Overview](#-1-project-overview)
- [2. Existing Service Analysis](#-2-existing-service-analysis)
- [3. Google Approval Process](#-3-google-approval-process)
- [4. Internal Policy & Quota Optimization](#-4-internal-policy--quota-optimization)
- [5. Architecture](#-5-architecture)
- [6. Technical Strategy](#-6-technical-strategy)
- [7. OAuth2 & Recovery Flow](#-7-oauth2--recovery-flow)
- [8. UI](#-8-ui)

<br>

## 📌 1. Project Overview

### 🔹 프로젝트 명
- **FixMyPlaylist**

### 🔹 서비스 주소
- [FixMyPlaylist 바로가기](https://youtube-track-recovery-71386729441.us-central1.run.app)

  - https://youtube-track-recovery-71386729441.us-central1.run.app

  - Cold Starting(최소 인스턴스 0)에 의해 15초 정도의 최초 지연이 발생할 수 있습니다.

### 🔹 시연 영상
- [OAuth 구글 심사 시연 영상](https://www.youtube.com/watch?v=dqOrLUjCFic&t=64s)

### 🔹 서비스 이용 방법
1. 구글 계정으로 로그인
2. **최초 로그인 시 OAuth2 권한 요청 승인(youtube.force-ssl)**
3. 로그인 후 추적/복구 대상의 재생목록 등록
4. 이후, 사용자 관여 없이 **1일 1회 자동 추적 및 복구** 진행
5. 사용자의 재생목록 삭제/수정은 자동 반영

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

### ⚙️ 프로젝트 스펙

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
  - **DB**: MySQL(배포), H2(테스트)
  - **In-Memory DB**: Redis(7.2)
  - **Infra**: Google Cloud Run, Cloud SQL, MemoryStroe for Redis, Cloud Scheduler, Cloud Console
  - **APIs**: Youtube Data API V3, Gemini 2.5 Flash-Lite, MAXMIND GeoIP 
<br><br>


## 🌐 2. Existing Service Analysis

### ❌ 유사 서비스(2025.08.13 기준) 

- 비정상 처리된 영상을 클릭 후 비디오 아이디를 검색해 **직접 광범위하게 찾는 방법** 등은 소개가 됩니다.

- 하지만 정식으로 플레이리스트의 내부 목록들을 등록하고 **자동 추적 및 복구**를 서비스하는 곳은 **전무**합니다.

### ❓ 왜 Youtube 인가?
- 타 음악 플랫폼(Spotify, Apple Music 등)
  - 정식 음원이 아닌 라이브, 콘서트 영상 등을 포함하지 못하며 바교적으로 적은 수의 음악만 등록되어 있습니다.

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


## 📝 3. Google Approval Process

1. **Google OAuth2 민감 범위 데이터 심사 완료** *(2025.06.09)*
    - **youtube.force-ssl** 정식 승인

2. **Youtube Data API V3 할당량 증설 완료** *(2025.07.31)*
    - 10,000 → **100,000** 상향(per day)
 
3. **Youtube API Compilance 심의 통과** *(2025.07.31)*
    - **'개인정보처리방침'** 및 **'서비스 이용약관'** 명세
<br><br>


## ✔️ 4. Internal Policy & Quota Optimization

### 1. 중복 영상 처리 및 복구 정책

- 영상 케이스별 복구 정책<br><br>
  | 케이스 | API(개수) | DB(개수) | Action(개수) |
  |:---:|:---:|:---:|:---:|
  | A | 정상(1) | 보유(1) | 유지 |
  | B | 정상(1) | 보유(2) | DB 삭제(1) |
  | C | 정상(2) | 보유(1) | DB 추가(1) |
  | D | 비정상(1) | 보유(1) | 복구(1) |
  | E | 비정상(2) | 보유(1) | 복구(2), DB 추가(1) |
  | F | 비정상(1) | 보유(2) | 복구(1), DB 삭제(1) |
  | G | 비정상(n) | 보유(0) | API 삭졔(n) 호출 |
<br>

- 재생목록 커스텀화의 극대화를 위해 중복 영상 저장 허용

- 고객 임의의 재생목록내 음악 추가/삭제는 최신화 작업을 통해 매일 DB와 동기화
<br><br>

### 2. API 할당량 소모 최적화

- 페이지네이션(Pagination) 적용
  - YouTube Data API로 한 번에 가져올 수 있는 최대 **재생목록/재생목록 아이템/비디오** 수: **50개**

  - 페이지 수 = ⌈전체 대상 개수 ÷ 50⌉
    
- 케이스 별 API 사용량 공식(예시)
  - 채널 전체 100개의 재생목록(**C**) 보유
  - 50개의 재생목록 등록(**P**)
  - 각 재생목록은 100곡(**V**, 재생목록 아이템: **I**) 포함
  - 비정상 영상 1개 복구<br>
  
    | 케이스 | 요청 단계 | 수식                   | 소모량 |
    |:--------|:----------------------|:--------|------------:|
    | 등록   | 채널 전체 재생목록 조회<br>일부 재생목록 등록<br>각 재생목록 아이템 조회<br>각 비디오 디테일 조회 | **⌈(C + P(I + V)) / 50⌉** | 202 |
    | 추적   | 각 재생목록 아이템 조회<br>각 비디오 디테일 조회 | **⌈P(I + V) / 50⌉**     | 200 |
    | 복구   | 대체 영상 검색<br>대체 영상 추가<br>비정상 영상 삭제 | **⌈201 + (I / 50)⌉**      | 203 |
<br>


## 💡 5. Architecture

### 1. Infra Architecture
<p align="center">
  <img width="2202" height="1280" alt="InfraArchitecture" src="https://github.com/user-attachments/assets/d79f147e-617c-42ab-8237-17891771d242" />
</p>

- **Cloud Run**: 컨테이너 기반 서버리스 배포 → 트래픽 급증에도 자동 확장, 무부하 시 비용 최소화

- **Cloud SQL**: SocketFactory + Proxy 기반 통신

- **Memorystore for Redis**: 세션 및 TTL 할당량 관리 저장소로 사용

- **Serverless VPC Access**: VPC 네트워킹을 통한 서비스 간 보안 강화

- **Cloud Scheduler**: 복구 작업 자동화, 멀티 인스턴스 상황에서도 단일 실행 보장
<br><br>

### 2. Layered Architecture
<p align="center">

  <img width="800" height="500" alt="LayeredArchitecutre" src="https://github.com/user-attachments/assets/82d1ccc5-f58c-4e65-85d0-b280dd0ceb65" />
  <!-- <img width="800" height="500" alt="LayeredArchitecturePart1" src="https://github.com/user-attachments/assets/06f2d72d-9ef3-4bd7-ac66-afe66fc61572" /> -->
</p>

- Controller → Service → Repository 의 계층적 단방향 구조

- Controller와 Service 간 DTO 경유 데이터 전달

- 횡단 관심사인 Security 설정으로 인증 상태를 전역적으로 판단

- Orchestration Service 패턴 적용
<br><br>


## 🛠️ 6. Technical Strategy

### 1. Orchestration Service
<p align="center">
  <img width="800" height="400" alt="OrchestrationService" src="https://github.com/user-attachments/assets/467c60cf-9464-40a7-9a41-20bb0a472602" />
  <!-- <img width="800" height="400" alt="LayeredArchitecturePart2" src="https://github.com/user-attachments/assets/83fa26c9-a2ab-4cc5-95ec-6cb608b25c7f" /> -->
</p>

- 복구 시나리오 수행 시, 트랜잭션 내의 서비스단 코드들을 총 관장

- 서비스 클래스들 간 상호 의존 문제를 방지하고 단방향 구조를 유지

- Outbox 패턴과 결합해 재시도 보상 시퀀스 제공
<br><br>

### 2. Outbox Pattern

<p align="center">
  <img width="700" height="550" alt="image" src="https://github.com/user-attachments/assets/92dacb13-29c3-472b-98eb-e8b34dc3658b" />
</p>

- **Outbox Pattern 도입 이유:**
  - DB 작업과 API 추가/삭제 작업 분리
    - 롤백 발생 시 데이터 정합성 유지

    - 트랜잭션 시간이 불필요하게 길어지는 것 방지

  - Outbox로 관리해 정책에 따라 API 추가/삭제 재시도 가능

- Outbox 상태(`PENDING`, `FAILED`, `SUCCESS`, `DEAD`)를 업데이트해 멱등성 보장

- Outbox의 상태 업데이트 시 새로운 트랜잭션(`REQURIES_NEW`) 사용
  - Outbox 이벤트 처리는 DB 최신화가 끝난 후 `AFTER_COMMIT`에 의해 실행

  - 스프링의 트랜잭션 컨텍스트와 DB 트랜잭션 간 생명주기를 고려해 새로운 트랜잭션을 수행
<br><br>

### 3. ERD

<p align="center">
  <img width="800" height="500" alt="ERD0" src="https://github.com/user-attachments/assets/fe844f34-212d-403c-a240-4e60c1deb32b" />
  <!-- <img width="800" height="500" alt="ERD" src="https://github.com/user-attachments/assets/e75fc94e-1533-4fb6-967a-220be616da80" /> -->
</p>

- **`Music` 테이블 설계 의도**
  - PK는 `videoId`가 아닌 별도의 `Id` 필드 사용
    - 단일 재생목록 내 동일한 `videoId`를 가진 영상 중복 추가 가능

  - `Playlist`와 N:M 관계를 두지 않음
    - 복구 시나리오상 `User → Playlist → Music` 순 조회 필요
      - N:M 매핑 후 복구 시, 토큰 발급 시점이 모호함

    - 유튜브에는 동일한 곡이라도 수천 개의 서로 다른 영상(`videoId`)이 존재
      - 모든 유저가 특정 음악에 대해 동일한 `videoId`를 가진 영상만을 재생목록에 담는 것은 아님

- **`@ManyToOne` 단방향 구조**
  - 서비스 로직상 한쪽 방향의 탐색만 필요하기 때문에 단방향 구조로 단순화

- **Lazy Loading 적용**
  - EAGER 방식 대신 지연 로딩으로 불필요한 쿼리 호출 최소화

  - DB 접근 최소화로 성능 최적화

- **DB FK Cascade 적용**
  - 부모 데이터 삭제 시 연관된 자식 데이터의 자동 정리로 데이터 정합성 유지

  - DB 엔진 레벨에서 즉시 처리: 대량 삭제/추가 작업 시 JPA Cascade 보다 성능 우위

- **독립적인 `ActionLog`, `Outbox` 테이블**
  - 장애 복구를 위한 표준적인 안정성 패턴 적용

  - 다른 엔티티들과 직접적인 연관관계를 맺지 않아 사이드 이펙트 방지

  - 독립적인 관리 및 트러블슈팅 용이
<br><br>


## 📊 7. OAuth2 & Recovery Flow

### 1. OAuth2 Authentication & Authorization Sequence
<p align="center">
  <img width="1166" height="1180" alt="OAuth2Diagram" src="https://github.com/user-attachments/assets/40b1a7e2-be54-4ec9-b827-339d3d30f2a2" />
<!--   <img width="3840" height="2650" alt="OAuth2SequenceDiagram" src="https://github.com/user-attachments/assets/f9343027-9d4a-4bd4-ad67-a394d56e1de3" /> -->
</p>

- OAuth2 인증 플로우 구축해 Google OAuth2 연동

- 로그인 핸들러를 통한 신규 회원과 기존 회원 분기 처리로, 회원가입과 로그인을 통합

- Redis를 세션 저장소로 사용하여 멀티 인스턴스 아키텍쳐에서도 확장성과 세션 일관성 보장

- 재가입을 반복해 할당량을 악의적으로 낭비하는 에외 사전 차단

- `Refresh Token` 통해 고객 부재에도 `Access Token`을 발급해 복구의 자동화 실현

- `GeoIP`를 이용해 IP 기반 고객의 국가코드 확보: 특정 국가가 차단된 영상의 판단 기준

- 사용자 ROLE을 동적으로 부여(ADMIN/USER), 인증 이후에도 권한 일관성 유지

- 재생목록 접근 권한(`youtube.force-ssl`) 미허용 시 세션, 쿠키 및 토큰 무효화 후 리다이렉트
<br><br>

### 2. Recovery Sequence
<img width="1106" height="1176" alt="RecoverDiagram" src="https://github.com/user-attachments/assets/154152a8-9c0a-409b-9a58-5fcbb93fb06f" />
<!-- <img width="3840" height="2880" alt="RecoverSequenceDiagram" src="https://github.com/user-attachments/assets/f44bf0c1-754f-4300-8d22-8224a586a95d" /> -->

- `Cloud Scheduler` 트리거: 엔드포인트 호출 시 헤더의 `API KEY`를 이용해 유효성 검사

- `Gemini` LLM 모델을 이용해 저장된 메타데이터를 기반으로 검색할 쿼리를 확보

- API 할당량 소모를 최소화 하기 위해, 당일 로그 기록을 확인하며 불필요한 대체 영상 검색을 방지

- ⚠ 예외 케이스 대비

  | 구분 | 상황 | 식별 | 대처 |
  |------|------|------|------|
  | **유저 예외** | 서비스의 'Delete Account' 버튼을 사용하지 않고<br>Google 보안 페이지에서 직접 탈퇴 | AccessToken 발급 불가 | 유저 삭제 |
  | **재생목록 예외** | 서비스에서 재생목록을 해제하기 전<br>유튜브 내에서 재생목록을 삭제 | 재생목록 API 조회 불가 | 재생목록 삭제 |
  | **음악 예외** | API 조회 시 비정상적인 속성 검출 | 속성 검사로 필터링 | 복구 |

<br>


## 🚀 8. UI

### **[관리자 전용 화면]**
<br>

**1. 세션 관리 화면**
<p align="center">
  <img width="900" height="800" alt="관리자세션화면" src="https://github.com/user-attachments/assets/626d8fc0-a1d4-4326-ad05-13bc718076f6" />
</p>

- `Role` 기반 관리자 페이지와 고객 페이지 구분

- Redis 기반 세션 관리

- 특정 유저 세션 상세 조회 및 즉시 무효화 기능
<br><br>

**2. 할당량 관리 화면**
<p align="center">
  <img width="900" height="400" alt="관리자할당량화면" src="https://github.com/user-attachments/assets/c40f5841-f6ac-4c25-bd0b-8faffba4c1a7" />
</p>

- Redis를 이용해 고객별 할당량을 조회 및 동적 조정
 
- 전역 할당량 제한키 동적 조절 
<br><br>

### [고객 전용 화면]
<br>

<table>
  <tr>
    <td>
      <img width="800" height="700" alt="welcomeUI" src="https://github.com/user-attachments/assets/a0256abf-b628-4325-a4ae-ab256230fbdb" /><br/>
      <img width="800" height="700" alt="RecoveryHistoryUI" src="https://github.com/user-attachments/assets/c186a13f-aa40-4992-af34-fdb86bc5b1f4" />
<!--       <img width="500" height="1400" alt="메인화면(유저)" src="https://github.com/user-attachments/assets/8ad7f54f-d344-4823-af3a-81de54650757" /> -->
    </td>
    <td>
<!--       <img width="500" alt="플리등록화면(결과)" src="https://github.com/user-attachments/assets/1c20c6a7-32be-4db3-a869-8039886391ba" /><br/>
      <img width="500" alt="ui3" src="https://github.com/user-attachments/assets/15aadbdf-be4a-4a77-a960-70429dec25f3" /><br/> -->
      <img width="800" height="2400" alt="playlistSelectionUI" src="https://github.com/user-attachments/assets/9bfc2c06-c0d6-4ba0-b398-cacc464a867a" />
<!--       <img width="500" alt="복구내역" src="https://github.com/user-attachments/assets/574640cf-19bc-4d82-9670-1a285d3a7971" /> -->
    </td>
  </tr>
</table>

- 구글 로그인 연동

- '개인정보처리방침' 및 '서비스 이용약관' 확인 가능
 
- 로그인 후 고객의 플레이리스트 목록을 제공

- 관리 대상이 될 플레이리스트를 등록/제거

- 'Delete Account' 버튼을 통해 사용자 관련 데이터 모두 제거

- 복구 내역 확인
<br><br>





<!-- 
## 🧩 11. 트러블슈팅 & 기술 과제

<details>
<summary><strong>[BUG] 심사 대응</strong></summary>

---
  
구글 OAuth2 심사 대응 전략 수립
- 구글 OAuth2 심사 대응 전략 수립
- 동의 화면, 개인정보처리방침, 서비스명 일치 검토

---
</details>

<details>
<summary><strong>[BUG] OAuth2</strong></summary>

---

**OAuth2**
- access/refresh token 처리
- `access_type`, `prompt`, `scope` 설정 이슈
- 고유 사용자 식별 및 DB 저장

---
</details>

<details>
<summary><strong>[BUG] Transaction 전파, REQURIES_NEW</strong></summary>

---

**Transaction 전파, REQURIES_NEW**
- 설명 추가

---
</details>

<details>
<summary><strong>[BUG] Youtube 비정상 영상 분류</strong></summary>

---

**Youtube 비정상 영상 분류**
- 삭제/비공개 영상 vs "unavailable video"

---
</details>

<details>
<summary><strong>[BUG] Persistence & 동기화</strong></summary>

---

**Persistence & 동기화**
- JPA 영속성 문제
- DB cascade vs JPA cascade
- 플레이리스트 수정 반영 로직

---
</details>

<details>
<summary><strong>[BUG] 보안</strong></summary>

---

**보안**
- CSRF 토큰 관리
- OAuth 로그인 캐시 이슈

---
</details>


## 📡 12. APIs

- **Youtube Data API V3**
  - 할당량 관리
  - PlaylistItems, Videos, Search API 활용

- **Gemini API**
  - 복구 시 유사 음원 추천에 활용

- **MaxMind Geo-Lite**
  - 최초 회원가입 시 국가코드 추출
<br><br>

## ✅ 13. 프로젝트 결론 및 리뷰

- 유튜브 사용자의 음악 자산 보호에 실질적 도움을 주는 서비스
- 수작업 백업의 한계를 자동화로 대체
- 실사용자 관점에서의 불편함을 **기술로 해결한 실용적인 예시**
<br><br>


-->























