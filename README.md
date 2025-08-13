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
- *타 음악 플랫폼(Spotify, Apple Music 등)*
  - 정식 음원이 아닌 라이브, 콘서트 영상 등을 포함하지 못하며 앞도적으로 적은 수의 음악만 등록되어 있습니다.

- *Youtube Music*
  - 유튜브와 유튜브 뮤직은 기본적으로 호환됩니다.
  
  - 유튜브 뮤직도 유사한 기능 제공하지만, 다음과 같은 **제약**이 존재합니다.
    - **삭제/비공개 영상은 추적 불가**
    - **콘서트/라이브/비공식 영상 추가 불가**
    - **비공식 음원은 강제로 공식 영상으로 대체**
    - **Youtube Music은 유료 서비스**
    - **완전한 플레이리스트 커스텀화가 불가능**

- 즉, **'유튜브 + FixMyPlaylist'** 조합만이 방대하고 다양한 유형의 음악을 무료로 자유롭게 관리할 수 있습니다.
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
    - 'youtube.force-ssl' 허가

2. **Youtube Data API V3 할당량 증설 완료**
    - 10,000 → **100,000**
 
3. **Youtube API Compilance 심의 통과**
    - **'개인정보처리방침'** 및 **'서비스 이용약관'** 명세
<br><br>

## 🚀 4. 주요 기능

- 비정상 영상 자동 탐지
 
- AI 기반 유사 음원 추출 및 재등록

- 재생목록 상태 추적 및 기록

- 자동화된 복구로 사용자 개입 최소화
<p align="center">
  <img width="700" height="400" alt="ui2" src="https://github.com/user-attachments/assets/4e0d8b21-2767-49e0-8f38-a6c2f0bb15de" />
</p>
<p align="center">
  <img width="700" height="400" alt="ui3" src="https://github.com/user-attachments/assets/15aadbdf-be4a-4a77-a960-70429dec25f3" />
</p>


---

## 3. OAuth2 로그인 및 회원가입 Sequence Diagram
<p align="center">
  <img width="3840" height="2650" alt="OAuth2SequenceDiagram" src="https://github.com/user-attachments/assets/f9343027-9d4a-4bd4-ad67-a394d56e1de3" />
</p>


## 4. Layered Architecture
<p align="center">
  <img width="800" height="550" alt="LayeredArchitecturePart1" src="https://github.com/user-attachments/assets/06f2d72d-9ef3-4bd7-ac66-afe66fc61572" />
</p>


## 5. Orchestration Service
<p align="center">
  <img width="800" height="450" alt="LayeredArchitecturePart2" src="https://github.com/user-attachments/assets/83fa26c9-a2ab-4cc5-95ec-6cb608b25c7f" />
</p>


## 6. 복구 시나리오 Sequence Diagram
<img width="3840" height="2880" alt="RecoverSequenceDiagram" src="https://github.com/user-attachments/assets/f44bf0c1-754f-4300-8d22-8224a586a95d" />


## 7. Outbox Pattern

- DB와 API 간 동기화
- 멱등성 보장

## 8. ERD
<p align="center">
  <img width="1121" height="674" alt="ERD" src="https://github.com/user-attachments/assets/e75fc94e-1533-4fb6-967a-220be616da80" />
</p>

- ManyToOne
- Lazy Loading

## 🧩 4. 트러블슈팅 & 기술 과제

- **OAuth2**
  - `@RegisteredOAuth2AuthorizedClient` 사용
  - access/refresh token 처리
  - `access_type`, `prompt`, `scope` 설정 이슈
  - 고유 사용자 식별 및 DB 저장


- **심사 대응**
  - 구글 OAuth2 심사 대응 전략 수립
  - 동의 화면, 개인정보처리방침, 서비스명 일치 검토


- **Youtube 비정상 영상 분류**
  - 삭제/비공개 영상 vs "unavailable video"


- **Persistence & 동기화**
  - JPA 영속성 문제
  - DB cascade vs JPA cascade
  - 플레이리스트 수정 반영 로직


- **보안**
  - CSRF 토큰 관리
  - OAuth 로그인 캐시 이슈

---

## 📡 5. 기술 및 API

- **Youtube Data API V3**
  - 할당량 관리
  - PlaylistItems, Videos, Search API 활용


- **Gemini API**
  - 복구 시 유사 음원 추천에 활용

---

## ✅ 6. 프로젝트 결론 및 리뷰

- 유튜브 사용자의 음악 자산 보호에 실질적 도움을 주는 서비스
- 수작업 백업의 한계를 자동화로 대체
- 실사용자 관점에서의 불편함을 **기술로 해결한 실용적인 예시**

---







