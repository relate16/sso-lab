# AI Agent Master Specification
## SSO Lab — OIDC SSO + Passwordless Multi-Method Authentication

> **문서 목적**
>
> 이 문서는 AI Agent가 별도의 추가 질의 없이 프로젝트의 전체 구조, 보안 정책, 인증 흐름, 서비스 경계, 데이터 모델, 테스트, 배포, 문서화까지 구현할 수 있도록 작성된 **단일 Master Specification**이다.
>
> AI Agent는 이 문서의 요구사항을 우선순위가 가장 높은 구현 기준으로 사용해야 한다.
> 문서에 명시된 보안 원칙, 서비스 경계, 데이터 소유권, 비밀정보 관리 정책을 임의로 완화하거나 우회해서는 안 된다.

---

# 1. 프로젝트 개요

이 프로젝트는 이력서 및 포트폴리오에서 시연할 수 있는 소규모 인증 플랫폼이다.

핵심 목표는 다음과 같다.

1. Spring Boot 기반의 자체 OIDC Provider / Authorization Server 구현
2. 여러 Client 서비스 간 SSO 동작 시연
3. 비밀번호를 사용하지 않는 Passwordless 인증 제공
4. Email OTP와 TOTP 두 가지 로그인 방식 제공
5. 사용자/그룹/Role 관리
6. 관리자 전용 관리 서비스 제공
7. 중앙 세션 및 로그아웃 처리
8. 민감정보 암호화 및 Secret 분리
9. 실제 공개 배포가 가능한 Docker Compose + TLS 구성
10. 테스트와 개발자 가이드가 충분히 제공되는 재현 가능한 프로젝트 구성

이 문서에서 `PC VM`은 오직 이 Monorepo를 실행하는 **배포 환경**을 의미한다.
사용자 인증 endpoint는 모두 `auth-server`의 사용자-facing Auth API에 속하며, 별도의 로그인 서비스나 별도 프로젝트를 가정하지 않는다.

프로젝트는 **정형적인 “강한 MFA 시스템”을 구현하는 것이 1차 목표가 아니다.**

현재 버전의 인증 모델은:

- Passwordless Authentication
- Email OTP 또는 TOTP 중 하나를 선택하는 Multi-Method Authentication
- OIDC 기반 SSO

이다.

엄격한 의미에서 서로 다른 factor를 동시에 요구하는 MFA / Step-up Authentication은 **Future Work**로 남긴다.

따라서 이력서와 README에서 현재 버전을 과장해서 “강한 MFA를 완성했다”고 표현하지 않는다.

권장 표현:

> Spring Boot 기반 OIDC SSO 및 Passwordless Multi-Method Authentication 플랫폼 구축

또는:

> OIDC SSO + Passwordless Authentication + MFA-ready Architecture

---

# 2. 확정 기술 스택

## 2.1 Backend

- Java 21
- Spring Boot 4.1.0
- Spring Security
- Spring Authorization Server / Spring Security Authorization Server 기능
- Spring Data JPA
- Spring Session JDBC
- PostgreSQL
- Flyway
- Gradle Kotlin DSL
- Bean Validation
- Testcontainers
- JUnit 5
- Mockito
- 필요 시 WireMock
- OpenAPI 문서화

Spring 관련 dependency 버전은 가급적 Spring Boot BOM이 관리하도록 한다.

직접 버전을 고정해야 하는 라이브러리는 이유를 주석 또는 문서로 남긴다.

## 2.2 Frontend

각 Frontend는 별도 React application 및 별도 Docker image로 구성한다.

- React
- TypeScript
- Vite
- Node.js LTS
- npm + package-lock.json
- 복잡한 UI Framework는 필수가 아님
- 단순하고 깔끔한 CSS 또는 CSS Modules 우선

UI 목표:

- 단순함
- 가독성
- 인증 흐름을 시각적으로 이해하기 쉬움
- 과도한 애니메이션 금지
- 업무 시스템 수준의 복잡한 화면은 구현하지 않음

## 2.3 Infrastructure

- Docker
- Docker Compose
- Caddy
- Let's Encrypt
- PostgreSQL 1 instance
- PC VM 기반 공개 배포
- GitHub Actions
- 무료 서브도메인 사용을 기본 전제로 함

Redis는 v1에서 사용하지 않는다.

---

# 3. Repository 구조

프로젝트는 **Monorepo**로 구성한다.

권장 구조는 다음과 같다.

```text
sso-lab/
├── backend/
│   ├── auth-server/
│   ├── admin-server/
│   ├── hr-server/
│   └── approval-server/
│
├── frontend/
│   ├── auth-web/
│   ├── admin-web/
│   ├── hr-web/
│   └── approval-web/
│
├── infra/
│   ├── caddy/
│   ├── docker/
│   └── scripts/
│
├── docs/
│   ├── ARCHITECTURE.md
│   ├── LOCAL_SETUP.md
│   ├── DEPLOYMENT.md
│   ├── SECURITY.md
│   ├── SSO_FLOW.md
│   ├── TEST_GUIDE.md
│   ├── DOMAIN_CHANGE_GUIDE.md
│   ├── GMAIL_SMTP_SETUP.md
│   ├── TURNSTILE_SETUP.md
│   ├── BOOTSTRAP_ADMIN_GUIDE.md
│   ├── SECRET_MANAGEMENT.md
│   ├── VM_SETUP_GUIDE.md
│   └── AWS_SETUP_GUIDE.md
│
├── .github/
│   └── workflows/
│
├── docker-compose.yml
├── .env.example
├── DEVELOPER_SETUP.txt
└── README.md
```

각 Backend와 Frontend는 서비스별로 **별도의 Docker image**를 생성해야 한다.

---

# 4. 서비스 아키텍처

전체 구조:

```text
                              Internet
                                 │
                             HTTPS :443
                                 │
                              ┌──▼──┐
                              │Caddy│
                              └──┬──┘
                                 │
        ┌────────────────────────┼─────────────────────────┐
        │                        │                         │
        ▼                        ▼                         ▼
     Auth Web                  HR Web                 Approval Web
      React                     React                    React
        │                        │                         │
        ▼                        ▼                         ▼
   Auth Server              HR Server              Approval Server
   Spring Boot              Spring Boot             Spring Boot
        ▲                        │                         │
        │                        └──────── OIDC ──────────┘
        │
        │ OIDC
        ▼
   Admin Server
   Spring Boot
        ▲
        │
    Admin Web
      React

        │
        ▼
   PostgreSQL
```

---

# 5. 서비스별 책임

## 5.1 auth-server

`auth-server`는 이 프로젝트의 핵심 서비스이다.

다음 기능의 **유일한 Owner**이다.

- OIDC Provider
- OAuth2 Authorization Server
- 사용자 Identity
- Email OTP
- TOTP
- Recovery Code
- User Role
- Hierarchical Group
- OIDC Client 등록
- SSO Session
- Refresh Token / Authorization 상태
- 회원가입
- 회원정보 변경
- 회원탈퇴
- 계정 정지
- Audit Log
- System Config
- Secret Provider 추상화
- Internal Admin API

### 절대 규칙

**Identity DB는 auth-server만 직접 접근할 수 있다.**

다음 서비스는 Identity DB에 직접 SQL/JPA 접근하면 안 된다.

- admin-server
- hr-server
- approval-server

---

## 5.2 admin-server

admin-server는 관리자 전용 BFF / Backend 서비스이다.

역할:

- Admin Web의 Backend
- OIDC Client
- ADMIN Role 검증
- 관리자 Session 관리
- Auth Server Internal Admin API 호출
- 관리자 민감정보 재인증 상태 관리
- 관리자 입력 validation
- 외부에서 Identity DB 직접 접근 금지

관리 작업은 반드시:

```text
Admin Web
   ↓
Admin Server
   ↓
Auth Server Internal API
   ↓
Identity DB
```

흐름을 따른다.

Admin Server가 User/Group/TOTP 관련 DB Table을 직접 수정하면 구현 오류로 간주한다.

---

## 5.3 hr-server

HR Demo용 OIDC Client/BFF이다.

실제 인사 기능 CRUD를 만드는 것이 목적이 아니다.

화면에서 다음 정도만 보여주면 된다.

- 현재 사용자
- userId
- username
- email
- Role
- Group
- `sub`
- `amr`
- `acr`
- 인증 시간
- 로그아웃
- 전체 로그아웃

HR Server는 DB 없이 운영 가능하도록 한다.

---

## 5.4 approval-server

Approval Demo용 OIDC Client/BFF이다.

v1에서는 HR과 인증 수준 요구사항이 동일하다.

다음 정보를 보여준다.

- 현재 사용자
- Role
- Group
- 현재 인증 방법
- OIDC Claims
- 로그인 상태
- 로그아웃
- 전체 로그아웃

현재 버전에서는 Approval 접근 시 별도의 Step-up Authentication을 요구하지 않는다.

---

# 6. Frontend 구조

## 6.1 auth-web

일반 사용자용 인증 UI.

포함 화면:

- 로그인
- 회원가입
- Email OTP 입력
- TOTP 입력
- 내 정보
- username 변경
- email 변경
- 그룹 조회
- TOTP 등록
- TOTP 해제
- Recovery Code 재발급
- TOTP Recovery
- 로그인 Session 목록
- 개별 Session 종료
- 전체 기기 로그아웃
- 회원탈퇴

일반 회원에게 **타 회원 조회 화면을 제공하지 않는다.**

---

## 6.2 admin-web

별도 React Application.

포함 화면:

- Dashboard
- User 목록
- User 상세
- User Suspend / Resume
- User Group 할당/해제
- USER / ADMIN Role 확인 및 관리
- Group Tree
- Group 생성
- Group 수정
- Group 이동
- Group 삭제
- Audit Log
- Masked Email
- Email 원문 보기 → 재인증 필요

Admin Web은 auth-web 내부 route로 구현하지 않는다.

별도 build, 별도 image, 별도 hostname을 사용한다.

---

## 6.3 hr-web / approval-web

각각 별도의 React Application 및 별도 Docker image로 만든다.

업무 기능을 과도하게 구현하지 않는다.

핵심 목적은 SSO 시연이다.

---

# 7. React + BFF 보안 원칙

각 React Application은 OAuth/OIDC Token을 직접 관리하지 않는다.

다음 항목은 Browser Storage에 저장 금지:

- Access Token
- Refresh Token
- ID Token
- Client Secret

금지 대상:

```text
localStorage
sessionStorage
IndexedDB를 Token 저장소로 사용하는 방식
```

구조:

```text
Browser
  │
  │ Secure + HttpOnly Session Cookie
  ▼
Spring Boot BFF
  │
  │ Authorization Code Flow
  ▼
Auth Server
```

BFF가 Token을 server-side에서 관리한다.

각 OIDC Client는 Confidential Client로 등록한다.

Authorization Code Flow를 사용한다.

PKCE S256을 가능한 범위에서 함께 적용한다.

Browser Cookie 기본 정책:

- Secure
- HttpOnly
- SameSite=Lax
- 적절한 Path
- Host-only Cookie 우선

Production에서는 HTTPS가 아니면 인증 Cookie를 허용하지 않는다.

Local 개발 profile에서만 예외를 둘 수 있다.

---

# 8. React 사용 이유 및 Trade-off 문서화

`docs/ARCHITECTURE.md`에 반드시 다음 취지를 정리한다.

## 장점

- 인증 서비스와 Demo 서비스 UI 분리
- Portfolio 시연성이 좋음
- Client-side 상태 관리가 편리함
- Admin / Auth / HR / Approval 화면을 독립적으로 구성 가능
- Backend BFF와 역할 분리 가능
- Token을 Browser에 노출하지 않으면서 React 사용 가능

## 단점

- 단순한 OIDC Demo에는 Server-side Template보다 복잡함
- Node Build Pipeline 필요
- CSRF/CORS/BFF 보안 경계가 추가됨
- Frontend/Backend 배포 단위 증가
- Browser Session과 Backend Token 상태를 함께 관리해야 함

문서에서는 “React를 사용하면 보안이 좋아진다”고 표현하지 않는다.

정확한 설명은:

> React를 사용하면서 발생하는 Browser Token 노출 문제를 BFF 패턴으로 통제하였다.

로 한다.

---

# 9. OIDC Client 구성

최소 등록 Client:

```text
hr-client
approval-client
admin-client
```

각 Client는 서로 다른:

- client_id
- client_secret
- redirect_uri
- post_logout_redirect_uri

를 가진다.

Client Secret은 절대 Git에 저장하지 않는다.

Auth Server DB에는 필요 시 안전하게 hash된 client secret만 저장한다.

실제 원문 secret은 각 Backend의 Secret Provider 또는 Docker Secret / env에서 읽는다.

---

# 10. OIDC 인증 Flow

기본 Flow:

```text
User
 ↓
HR 또는 Approval 접속
 ↓
해당 Backend Session 없음
 ↓
Auth Server /authorize redirect
 ↓
Auth Server SSO Session 확인
 ├─ Session 있음 → 즉시 Authorization Code
 └─ Session 없음 → Passwordless Login
                    ↓
             Email OTP 또는 TOTP
                    ↓
                 성공
                    ↓
             Authorization Code
                    ↓
              Client Backend
                    ↓
               Token 교환
                    ↓
            Backend Session 생성
                    ↓
               React 화면
```

SSO의 핵심 시연:

```text
1. HR 접속
2. Auth Server에서 로그인
3. HR 로그인 완료

4. 같은 Browser에서 Approval 접속
5. Approval → Auth Server redirect
6. Auth Server SSO Session이 존재하므로 사용자 입력 없이 Authorization Code 발급
7. Approval 로그인 완료
```

---

# 11. 현재 인증 수준 정책

현재 버전에서는 Email OTP와 TOTP를 **서로 다른 로그인 방법**으로 제공한다.

둘 중 하나만 성공해도 로그인 완료이다.

현재 정책:

```text
HR       → loa:1
Approval → loa:1
Admin    → loa:1 + ADMIN Role
```

현재 버전에서는 `loa:2`를 사용하지 않는다.

`acr`을 문자열 숫자 비교처럼 `>=` 비교하지 않는다.

Client는 허용된 `acr` 값 또는 프로젝트 내부 Authentication Context 정책으로 검증한다.

기본 `acr`:

```text
urn:jb:loa:1
```

---

# 12. OIDC Claim 정책

Email OTP 로그인:

```json
{
  "sub": "internal-user-uuid",
  "roles": ["USER"],
  "groups": ["/개발본부/백엔드팀"],
  "amr": ["email_otp"],
  "acr": "urn:jb:loa:1"
}
```

TOTP 로그인:

```json
{
  "sub": "internal-user-uuid",
  "roles": ["USER"],
  "groups": ["/개발본부/백엔드팀"],
  "amr": ["totp"],
  "acr": "urn:jb:loa:1"
}
```

`amr`에는 실제 해당 로그인에서 성공한 인증수단만 기록한다.

필수/권장 Claims:

- sub
- userId 또는 preferred_username
- username 또는 name
- email
- roles
- groups
- amr
- acr
- auth_time
- sid

Email은 `email` scope를 요청한 내부 Client에만 제공한다.

Group은 전체 계층 path를 배열로 전달한다.

예:

```json
{
  "groups": [
    "/개발본부",
    "/개발본부/백엔드팀",
    "/프로젝트/SSO-TF"
  ]
}
```

---

# 13. 회원가입

회원가입은 누구나 가능해야 한다.

비밀번호는 받지 않는다.

필수 입력:

- userId
- username
- email

## 13.1 userId

- unique
- 가입 후 변경 불가
- 로그인 ID
- 공백 불가
- 권장 허용 문자: `a-z`, `0-9`, `.`, `_`, `-`
- 서버에서 lowercase 기준으로 처리하여 uniqueness ambiguity를 줄인다.
- 길이는 4~30자 권장

## 13.2 username

- 가입 후 본인이 변경 가능
- Unicode 허용 가능
- 공백 trim
- 길이 제한 필요

## 13.3 email

- unique
- 가입 전 Email OTP 검증 필수
- DB 평문 저장 금지
- normalized email 기준으로 중복 검사

권장 normalize:

```text
trim
lowercase
```

---

# 14. 회원가입 Email OTP Flow

```text
회원가입 정보 입력
 ↓
Turnstile 검증
 ↓
Email OTP 발송
 ↓
사용자 OTP 입력
 ↓
OTP 검증 성공
 ↓
userId unique 재검사
 ↓
email_lookup_hash unique 재검사
 ↓
User 생성
 ↓
USER Role 부여
 ↓
가입 완료
```

가입 인증 중에는 임시 등록정보를 DB에 보관할 수 있다.

이를 위해 `pending_registrations` 테이블을 사용할 수 있다.

가입 완료 후 해당 pending data는 즉시 제거하거나 consumed 처리한다.

---

# 15. Passwordless Login

## 15.1 기본 Flow

```text
userId 입력
 ↓
로그인 방법 결정
 ↓

TOTP 미등록 사용자:
└─ Email OTP

TOTP 등록 사용자:
├─ Email OTP
└─ TOTP

사용자가 방법 선택
 ↓
성공
 ↓
loa:1
 ↓
SSO 로그인 완료
```

비밀번호 입력란은 만들지 않는다.

---

# 16. Email OTP 정책

기본값:

```text
유효시간           5분
최대 실패 시도     5회
재발송 대기        60초
성공 후            즉시 폐기
```

위 값들은 코드의 기본값이다.

실제 값은 `system_config`에서 override 가능해야 한다.

OTP 원문은 DB에 저장하지 않는다.

OTP는 cryptographically secure random generator를 사용한다.

DB에는 hash 또는 안전한 검증값만 저장한다.

OTP Challenge에는 목적을 구분한다.

예:

```text
SIGNUP
LOGIN
EMAIL_CHANGE
ADMIN_REAUTH
```

---

# 17. Login Enumeration 방어

로그인 API는 다음과 같은 직접적인 응답을 하지 않는다.

금지:

```text
존재하지 않는 사용자입니다.
등록되지 않은 이메일입니다.
이 사용자는 TOTP가 없습니다.
```

Email OTP 요청 시 권장 응답:

> 입력 정보가 유효하다면 인증 메일이 발송되었습니다.

Login Method 조회는 Rate Limit 및 Turnstile 검증 이후에만 수행한다.

TOTP 등록 여부를 UI에서 구분해야 하는 요구사항 때문에 완전한 metadata hiding은 어렵다.

따라서 다음 원칙을 따른다.

- 존재하지 않는 userId에 대해 명시적인 Not Found 응답 금지
- 응답 시간 차이를 과도하게 만들지 않음
- Method 조회 endpoint rate limit
- Turnstile 보호
- Audit 기록
- 실패 메시지 통일

---

# 18. Rate Limit

Redis는 사용하지 않는다.

v1은 단일 Auth Server instance를 전제로 한다.

Rate Limit은 다음 기준을 조합한다.

- IP
- userId
- email lookup hash
- OTP challenge

필수 보호 대상:

- 회원가입 OTP 발송
- 로그인 Email OTP 발송
- OTP 검증
- TOTP 검증
- Recovery Code 검증
- Admin re-auth
- 회원가입

단일 노드 특성상 IP rate limit은 in-memory 구현이 가능하다.

단, OTP challenge 자체의 attempt / resend / expiry는 DB 상태를 기준으로 강제한다.

이 한계는 `SECURITY.md`에 기록한다.

---

# 19. Cloudflare Turnstile

인터넷에 공개된 Production 환경에서는 다음 요청에 Turnstile을 적용한다.

- 회원가입 시작
- 로그인 인증 시작
- Email OTP 발송
- 반복적인 Recovery 요청

Local 개발에서는 feature flag로 비활성화 가능하다.

예:

```text
TURNSTILE_ENABLED=false
```

Production profile에서는 기본적으로 활성화한다.

Turnstile Secret은 Secret Provider로 관리한다.

Turnstile Site Key는 frontend 환경변수로 제공 가능하다.

`docs/TURNSTILE_SETUP.md`에:

- 계정 생성
- Site 등록
- Site Key
- Secret Key
- Local Test Key 사용법
- Production 설정

을 작성한다.

---

# 20. TOTP

TOTP는 RFC 6238 호환 방식으로 구현한다.

호환성을 위해 기본값:

```text
Digits: 6
Period: 30 seconds
Algorithm: SHA1
Validation window: current ± 1 step
```

TOTP Secret은 DB 평문 저장 금지.

AES-256-GCM으로 암호화한다.

---

# 21. TOTP 등록

Flow:

```text
로그인 상태
 ↓
[TOTP 등록]
 ↓
재인증
Email OTP 또는 현재 사용 가능한 인증수단
 ↓
새 TOTP Secret 생성
 ↓
QR Code / otpauth URI 표시
 ↓
Authenticator로 Scan
 ↓
6자리 TOTP 입력
 ↓
서버 검증 성공
 ↓
TOTP 활성화
 ↓
Recovery Code 10개 발급
```

QR만 보여주고 등록 완료 처리하면 안 된다.

반드시 실제 TOTP Code를 최소 1회 성공 검증한 뒤 활성화한다.

Pending TOTP Secret은 활성화 전 별도 상태로 관리한다.

---

# 22. TOTP 해제

TOTP 해제 전 재인증 필수.

성공 시:

- TOTP Credential 삭제/비활성화
- 기존 Recovery Code 모두 폐기
- Audit 기록

TOTP 해제 후 로그인 방식은 Email OTP만 남는다.

---

# 23. Recovery Code

Recovery Code는 일반 로그인 수단이 아니다.

목적:

> TOTP 분실 시 TOTP 초기화 / 재등록

발급 개수:

```text
10개
```

각 Code:

- 충분한 entropy
- 한 번만 사용 가능
- DB 원문 저장 금지
- Argon2id hash 사용
- 사용 시 `used_at` 기록

Recovery Code 재발급 시:

- 기존 미사용 Code 전부 무효화
- 새 10개 생성
- 평문은 생성 직후 한 번만 사용자에게 표시
- 서버는 이후 평문을 다시 복구할 수 없어야 함

---

# 24. Recovery Flow

```text
TOTP 분실
 ↓
Recovery 메뉴
 ↓
userId + Recovery Code
 ↓
검증
 ↓
Restricted Recovery Session 생성
 ↓
기존 TOTP Secret 폐기
 ↓
새 TOTP 등록
 ↓
TOTP 최초 검증
 ↓
Recovery Session 종료
```

Recovery Code 자체로 일반 OIDC SSO 로그인 성공 처리하지 않는다.

Restricted Recovery Session에서는 TOTP 복구 외의 민감 기능을 수행할 수 없어야 한다.

---

# 25. 사용자 Profile

일반 사용자는 자기 정보만 조회 가능하다.

타 사용자 목록/검색 기능은 제공하지 않는다.

표시 정보:

- userId
- username
- email
- Role
- Group
- TOTP 등록 여부
- 현재 로그인 Session

Group 미할당 사용자는 UI에:

```text
할당된 그룹 없음
```

으로 표시한다.

---

# 26. username 변경

정책:

```text
username
→ 본인 변경 가능
→ 인증 Session 유지
```

Audit 기록.

---

# 27. email 변경

Flow:

```text
새 email 입력
 ↓
중복 검사
 ↓
새 Email로 OTP 발송
 ↓
OTP 검증
 ↓
email_ciphertext 변경
email_lookup_hash 변경
 ↓
현재 Session을 제외한 다른 Session 폐기
 ↓
Refresh Token 폐기
 ↓
Audit 기록
```

userId는 변경되지 않는다.

---

# 28. 이메일 암호화

Email은 DB에서 AES-256-GCM으로 암호화한다.

권장 저장 구조:

```text
email_ciphertext
email_iv
email_key_version
email_lookup_hash
```

검색 및 unique 검사:

```text
normalizedEmail
   ↓
HMAC-SHA256
   ↓
email_lookup_hash
```

DB에 plain email column을 두지 않는다.

---

# 29. Encryption Key 분리

논리적으로 다음 Key를 분리한다.

```text
EMAIL_ENCRYPTION_KEY
TOTP_ENCRYPTION_KEY
EMAIL_LOOKUP_HMAC_KEY
JWT_SIGNING_KEY
```

한 Key를 여러 목적으로 재사용하지 않는다.

JWT Private Key와 대칭 암호화 Key는 반드시 분리한다.

---

# 30. Secret Provider

애플리케이션 코드에서 Secret 원문을 하드코딩하면 안 된다.

다음 abstraction을 구현한다.

```java
public interface SecretProvider {
    SecretValue getSecret(String reference);
}
```

구현 예:

```text
EnvironmentSecretProvider
DockerSecretProvider
AwsSsmSecretProvider
```

현재 PC VM 배포에서는:

- Docker Secret 또는
- Environment Variable

을 우선 사용한다.

AWS 배포로 변경할 경우:

- AWS SSM Parameter Store SecureString

을 사용할 수 있도록 코드 레벨에서 확장 가능해야 한다.

---

# 31. Secret Reference와 system_config

Secret 원문은 `system_config`에 저장하지 않는다.

허용 예:

```text
EMAIL_ENCRYPTION_KEY_PROVIDER=DOCKER_SECRET
EMAIL_ENCRYPTION_KEY_REF=email-encryption-key

JWT_KEY_PROVIDER=ENV
JWT_KEY_REF=JWT_PRIVATE_KEY
```

금지:

```text
EMAIL_ENCRYPTION_KEY=실제AES키
JWT_PRIVATE_KEY=실제PrivateKey
SMTP_PASSWORD=실제비밀번호
```

Secret Provider/Reference는 DB 또는 Bootstrap config를 통해 교체 가능해야 한다.

Java 코드가 provider abstraction을 통해 Secret을 가져오도록 한다.

---

# 32. System Config

시간/정책 값은 `system_config` table로 변경 가능하도록 한다.

기본값:

```text
SSO_SESSION_IDLE_TIMEOUT       = 30m
SSO_SESSION_ABSOLUTE_TIMEOUT   = 8h
ACCESS_TOKEN_TTL               = 5m
ID_TOKEN_TTL                   = 5m
REFRESH_TOKEN_TTL              = 8h
ADMIN_REAUTH_TTL               = 5m

EMAIL_OTP_TTL                  = 5m
EMAIL_OTP_MAX_ATTEMPTS         = 5
EMAIL_OTP_RESEND_INTERVAL      = 60s
```

DB 값이 없으면 코드 default를 사용한다.

잘못된 값이 있으면:

- validation error 기록
- 안전한 기본값 fallback
- startup 또는 runtime warning log

를 남긴다.

Config Service는 typed configuration API로 구현한다.

서비스 코드 곳곳에서 raw 문자열 key를 직접 조회하지 않도록 한다.

---

# 33. Group

계층형 Group.

예:

```text
회사
├── 개발본부
│   ├── 백엔드팀
│   └── 프론트팀
└── 경영본부
    └── 인사팀
```

DB 구조는 self-reference를 사용한다.

```text
groups
- id UUID
- name
- parent_id UUID nullable
```

사용자는 여러 Group에 소속 가능하다.

```text
users N:M groups
```

가입 직후:

```text
Group 없음
```

Group 할당/해제는 ADMIN만 가능하다.

---

# 34. Group 안전성

필수 제약:

- 자기 자신을 parent로 설정 금지
- descendant를 parent로 지정하는 cycle 금지
- 같은 parent 하위의 동일 name 충돌 방지
- Group 삭제 시 child/group member가 존재하면 기본적으로 409 Conflict
- 무조건 cascade delete하지 않음
- UI에서 Tree 구조 표시

Group claim은 full path로 계산한다.

---

# 35. Role

현재 Role:

```text
USER
ADMIN
```

모든 가입자:

```text
USER
```

Bootstrap Admin:

```text
USER
ADMIN
```

향후 RBAC 확장을 고려하여 단순 enum field 하나만 User Table에 저장하지 않는다.

권장:

```text
roles
user_roles
```

N:M 구조.

현재는 Permission Table까지 필수 구현하지 않는다.

단, 추후:

```text
Role → Permission
```

을 추가할 수 있도록 Service/API boundary를 과도하게 고정하지 않는다.

관리자가 Role을 변경할 수 있다면 마지막 ACTIVE ADMIN을 제거할 수 없게 safeguard를 둔다.

---

# 36. Bootstrap Admin

초기 ADMIN 생성은 Bootstrap 방식.

환경설정 예:

```text
BOOTSTRAP_ADMIN_ENABLED=true
BOOTSTRAP_ADMIN_EMAIL=developer@example.com
```

가입 완료 시 verified normalized email이 Bootstrap Email과 일치하면 ADMIN Role을 부여한다.

첫 Bootstrap Admin 생성 후:

- claimed 상태 저장
- 동일 이메일 재사용으로 추가 Bootstrap Admin 생성 금지
- Bootstrap 기능을 끄도록 개발 가이드에서 안내

Bootstrap email 원문을 DB에 장기 보관할 필요는 없다.

`DEVELOPER_SETUP.txt`와 `docs/BOOTSTRAP_ADMIN_GUIDE.md`에 반드시 상세 가이드를 작성한다.

---

# 37. Account 상태

최소 상태:

```text
ACTIVE
SUSPENDED
```

ADMIN만 변경 가능.

SUSPENDED 시:

- 신규 로그인 불가
- Auth Server SSO Session 폐기
- Refresh Token 폐기
- 가능한 Client Session에 Back-Channel Logout
- Audit 기록

JWT Access Token은 stateless 특성상 이미 발급된 토큰이 TTL 동안 남을 수 있다.

Access Token TTL을 5분으로 짧게 두는 이유를 `SECURITY.md`에 기록한다.

---

# 38. 회원탈퇴

회원탈퇴는 Hard Delete.

삭제 대상:

- user row
- user role
- user group membership
- TOTP
- Recovery Code
- OTP Challenge
- OIDC Authorization
- Refresh Token
- SSO Session
- 관련 Client Session logout 요청

Audit Log는 유지한다.

Audit Log의 actor는 userId 또는 email을 저장하지 않는다.

내부 UUID만 남긴다.

삭제 후 UUID와 실제 삭제 사용자를 다시 연결할 수 있는 별도 lookup table을 만들지 않는다.

---

# 39. 내부 User ID

DB PK:

```text
UUID
```

예:

```text
users.id = UUID
```

OIDC `sub`도 이 stable internal UUID 기반으로 사용한다.

userId와 분리한다.

Audit:

```text
actor_id = UUID
target_id = UUID nullable
```

Audit Table에서 User FK를 강제하지 않는다.

Hard Delete 이후에도 Audit이 남아야 하기 때문이다.

---

# 40. PostgreSQL 데이터 소유권

PostgreSQL Instance는 한 개 사용한다.

```text
PostgreSQL
  └── auth schema
```

직접 DB 접근 허용:

```text
auth-server only
```

금지:

```text
admin-server
hr-server
approval-server
frontend
```

admin-server가 User/Group Table에 직접 UPDATE하면 요구사항 위반이다.

---

# 41. 권장 DB Table

최소 다음 구조를 고려한다.

```text
users
roles
user_roles

groups
user_groups

pending_registrations
email_otp_challenges

totp_credentials
recovery_codes

system_config

audit_logs

oauth2_registered_client
oauth2_authorization
oauth2_authorization_consent

SPRING_SESSION
SPRING_SESSION_ATTRIBUTES

user_session_metadata
```

실제 Spring Authorization Server / Spring Session 버전에 맞는 공식 JDBC Schema를 기반으로 migration을 구성한다.

모든 schema 생성/변경은 Flyway Migration으로 관리한다.

Production에서 Hibernate `ddl-auto=create/update` 사용 금지.

권장:

```text
ddl-auto=validate
```

---

# 42. Audit Log

Audit은 무기한 보관한다.

자동 Cleanup은 v1에 구현하지 않는다.

향후 관리 기능으로 추가 가능하도록 한다.

필수 Event 예:

```text
SIGN_UP_STARTED
SIGN_UP_COMPLETED
EMAIL_VERIFIED

LOGIN_STARTED
LOGIN_SUCCESS
LOGIN_FAILED

EMAIL_OTP_SENT
EMAIL_OTP_FAILED

TOTP_ENROLLED
TOTP_DISABLED
TOTP_SUCCESS
TOTP_FAILED

RECOVERY_CODE_USED
RECOVERY_CODES_REGENERATED

SSO_AUTHORIZED

LOGOUT_CURRENT_SESSION
LOGOUT_ALL_SESSIONS
BACKCHANNEL_LOGOUT

USERNAME_CHANGED
EMAIL_CHANGED

ACCOUNT_SUSPENDED
ACCOUNT_RESUMED
ACCOUNT_DELETED

GROUP_CREATED
GROUP_UPDATED
GROUP_MOVED
GROUP_DELETED
GROUP_ASSIGNED
GROUP_REMOVED

ROLE_ASSIGNED
ROLE_REMOVED

ADMIN_REAUTH_SUCCESS
ADMIN_REAUTH_FAILED
ADMIN_EMAIL_REVEALED
```

절대 로그에 남기지 말 것:

- OTP 원문
- TOTP Secret
- Recovery Code 원문
- AES Key
- JWT Private Key
- SMTP Password
- Turnstile Secret
- Access Token
- Refresh Token
- 전체 Email 원문

IP는 필요 시 일부 Masking 또는 Hashing을 사용한다.

---

# 43. Admin Email Masking

ADMIN도 다른 사용자의 Email을 기본적으로 원문 조회할 수 없다.

기본:

```text
h***@gmail.com
```

원문 조회 Flow:

```text
[Email 보기]
 ↓
명시적 재인증
 ↓
Email OTP 또는 TOTP
 ↓
재인증 성공
 ↓
5분 Elevated Admin Session
 ↓
Email 원문 조회 가능
```

5분 후 자동으로 다시 Masking.

Email 원문 조회는 Audit 기록.

화면 reload 또는 admin session 갱신 시 elevated 상태를 적절히 유지/만료 처리한다.

Browser가 임의로 `elevated=true`를 조작해서 우회할 수 없도록 server-side 상태 또는 Auth Server가 서명한 재인증 증거를 사용한다.

---

# 44. Admin 재인증

권장 구현:

- OIDC `prompt=login`
- `max_age=0`
- 또는 Auth Server가 발급한 별도 short-lived re-auth proof

중요한 점:

> 재인증 성공 여부는 반드시 서버가 검증 가능한 상태여야 하며 Browser JSON 값만 신뢰하면 안 된다.

재인증 후 기본 TTL:

```text
5분
```

DB `ADMIN_REAUTH_TTL` 설정으로 변경 가능.

---

# 45. Admin Server OIDC

Admin Server도 일반 OIDC Client이다.

```text
Admin Web
 ↓
Admin Server
 ↓ OIDC
Auth Server
```

접근 조건:

```text
acr = urn:jb:loa:1
AND
roles contains ADMIN
```

USER만 가진 계정:

```text
403 Forbidden
```

---

# 46. Admin Server → Auth Server 내부 인증

Admin Server의 Internal API 호출은 무인증 HTTP로 호출하면 안 된다.

최소 다음 보안을 적용한다.

1. Docker Internal Network 사용
2. Caddy에서 `/internal/**` 외부 공개 금지
3. Server-to-Server authentication
4. OAuth2 Client Credentials 또는 이에 준하는 service credential
5. 관리자 user context를 별도로 전달하고 Auth Server에서 ADMIN 상태 재확인
6. 민감 Email 원문 조회에는 추가 재인증 증거 확인

Auth Server는 Admin Server Client ID를 allow-list로 검증한다.

---

# 47. Session 정책

기본:

```text
SSO Session Idle Timeout     30분
SSO Session Absolute TTL     8시간

Access Token                 5분
ID Token                     5분
Refresh Token                8시간

Admin Sensitive Re-auth      5분
Email OTP                    5분
```

Remember Me는 구현하지 않는다.

SSO Session은 Spring Session JDBC를 사용하여 PostgreSQL에 저장한다.

이를 통해:

- 다중 기기 Session 조회
- 개별 Session 종료
- 전체 Session 종료

가 가능해야 한다.

---

# 48. 다중 로그인

복수 Browser / Device 로그인 허용.

예:

```text
Chrome / Windows
Safari / iPhone
Firefox / Linux
```

각 Session은 독립적으로 관리한다.

사용자는 자신의 Session 목록을 볼 수 있다.

표시 가능한 항목:

- Browser/Device 추정 문자열
- 로그인 시각
- 마지막 활동 시각
- 현재 Session 여부

과도하게 정밀한 fingerprinting은 구현하지 않는다.

---

# 49. 개별 Session 종료

사용자는 자신의 특정 Session을 종료할 수 있다.

현재 Session을 종료하면 로그아웃 화면으로 이동한다.

다른 기기 Session 종료 시:

- 해당 Auth Session 폐기
- Refresh Token 폐기
- 관련 Client에 Back-Channel Logout 시도

---

# 50. Current Session Logout

Client에서 현재 SSO Session을 종료하는 버튼을 제공한다.

현재 Session 로그아웃:

- RP-Initiated Logout
- 현재 Auth Server SSO Session 종료
- 같은 SSO Session에 연결된 Client에 Back-Channel Logout
- 현재 Browser의 Client Session 제거

즉 같은 Browser의 HR/Approval이 동일 SSO Session을 사용 중이면 중앙 로그아웃 효과를 보여준다.

---

# 51. Global Logout

“전체 기기 로그아웃”은 현재 사용자에 속한 모든 Session을 폐기한다.

처리:

```text
모든 Auth Session 폐기
모든 Refresh Token revoke
모든 관련 Client에 Back-Channel Logout
```

다른 Device도 다시 인증해야 한다.

---

# 52. OIDC Back-Channel Logout

반드시 구현 대상.

Client별 Back-Channel Logout endpoint를 제공한다.

Auth Server는 logout token을 전송한다.

Client Backend는:

- logout token signature 검증
- issuer 검증
- audience 검증
- sid 또는 sub 확인
- 연결된 local session 폐기

Browser Frontend에 Token을 보내지 않는다.

---

# 53. 사용자 Session 목록

auth-web의 내 정보에 Session 관리 화면 포함.

예:

```text
Chrome / Windows
현재 세션
2026-08-18 11:30 로그인

Safari / iPhone
2026-08-17 18:21 로그인
[세션 종료]
```

Session metadata에 plain IP 전체를 장기 보관하지 않는 방향을 우선한다.

---

# 54. Gmail SMTP

v1 메일 발송은 Gmail SMTP 사용.

Mail 전송 abstraction을 만든다.

예:

```java
public interface VerificationMailSender {
    void sendOtp(...);
}
```

Production:

```text
GmailSmtpMailSender
```

Test:

```text
FakeMailSender / InMemoryMailSender
```

자동 테스트에서 실제 Gmail SMTP에 연결하면 안 된다.

SMTP Password / App Password는 Secret Provider로 관리한다.

`docs/GMAIL_SMTP_SETUP.md`에 개발자 설정 가이드 작성.

---

# 55. CSRF / CORS / Security Headers

React + Cookie Session 구조이므로 CSRF를 반드시 고려한다.

권장:

- Spring Security CSRF 활성화
- React가 state-changing request에 CSRF token 전송
- API 인증을 단순 `csrf.disable()`로 우회하지 않음

가능하면 각 Frontend와 Backend를 동일 Origin에서 reverse proxy하여 CORS 필요성을 줄인다.

예:

```text
https://hr.example
  /        → hr-web
  /api/*   → hr-server
```

Security headers:

- HSTS in production
- Content-Security-Policy
- X-Content-Type-Options
- Referrer-Policy
- Frame 관련 정책

Caddy/Spring Security 양쪽에서 중복/충돌되지 않도록 관리한다.

---

# 56. Domain 설계

논리 hostname:

```text
AUTH_PUBLIC_URL
ADMIN_PUBLIC_URL
HR_PUBLIC_URL
APPROVAL_PUBLIC_URL
```

예:

```text
https://auth.example
https://admin.example
https://hr.example
https://approval.example
```

무료 서브도메인에서 동일 parent domain 구성이 어려우면 서로 다른 hostname도 허용한다.

예:

```text
jb-auth.<free-domain>
jb-admin.<free-domain>
jb-hr.<free-domain>
jb-approval.<free-domain>
```

URL 하드코딩 금지.

---

# 57. Domain 변경 가능성

도메인 변경 시 Java/React Source Code 수정이 최소화되어야 한다.

환경설정으로 관리:

```text
AUTH_PUBLIC_URL
ADMIN_PUBLIC_URL
HR_PUBLIC_URL
APPROVAL_PUBLIC_URL
```

영향 대상:

- Caddy
- OIDC issuer
- redirect_uri
- post_logout_redirect_uri
- CORS
- Cookie
- Frontend API Base URL
- OIDC Client bootstrap

`docs/DOMAIN_CHANGE_GUIDE.md`에 정확한 변경 순서를 작성한다.

---

# 58. TLS

인터넷에 공개되는 Production 환경은 HTTPS only.

Caddy를 사용하여 Let's Encrypt 자동 인증서 발급 및 갱신.

기본:

```text
80
443
```

PC VM이 Router 뒤에 있을 경우 Port Forwarding 필요.

Public IPv4가 없고 CGNAT 환경이라면 별도 outbound tunnel 방식으로 배포할 수 있도록 대체 가이드를 언급한다.

Primary Architecture는:

```text
Internet
 ↓
Public IP
 ↓
Router 80/443 forwarding
 ↓
PC VM
 ↓
Caddy
```

이다.

---

# 59. Local 개발

Local hostname 예:

```text
auth.localhost
admin.localhost
hr.localhost
approval.localhost
```

또는 `/etc/hosts` 기반 이름을 사용한다.

Local TLS는 선택 가능하다.

Production과 보안 차이가 발생하는 경우 반드시 가이드에 명시한다.

---

# 60. Docker Compose

최소 Container:

```text
postgres
auth-server
admin-server
hr-server
approval-server

auth-web
admin-web
hr-web
approval-web

caddy
```

Service별 Docker image 분리.

Backend MSA 서비스는 별도 image.

Frontend도 서비스별 image.

Docker Network:

```text
public-network
internal-network
db-network
```

등 논리적으로 분리할 수 있다.

Postgres Port를 외부 인터넷에 expose하지 않는다.

Auth Server Internal Admin API를 Caddy의 외부 Route로 노출하지 않는다.

---

# 61. Health Check

각 Backend:

```text
/actuator/health
```

또는 이에 준하는 health endpoint 제공.

외부 배포 시 민감 Actuator endpoint는 노출 금지.

허용:

- health
- readiness
- liveness

금지:

- env
- beans
- heapdump
- configprops 전체 공개

---

# 62. API Versioning

Application API는 기본적으로:

```text
/api/v1/**
```

Internal Admin API:

```text
/internal/admin/v1/**
```

OIDC 표준 endpoint는 Spring Authorization Server endpoint 규칙을 따른다.

---

# 63. Auth API 예시

정확한 이름은 구현 중 소폭 조정 가능하지만 기능은 반드시 존재해야 한다.

```text
POST /api/v1/signup/start
POST /api/v1/signup/verify

POST /api/v1/login/start
POST /api/v1/login/email/send
POST /api/v1/login/email/verify
POST /api/v1/login/totp/verify

GET  /api/v1/me
PATCH /api/v1/me/username

POST /api/v1/me/email/change/start
POST /api/v1/me/email/change/verify

POST /api/v1/me/totp/enroll/start
POST /api/v1/me/totp/enroll/confirm
DELETE /api/v1/me/totp

POST /api/v1/me/recovery-codes/regenerate

POST /api/v1/totp/recovery/start
POST /api/v1/totp/recovery/enroll/confirm

GET    /api/v1/me/sessions
DELETE /api/v1/me/sessions/{sessionId}
POST   /api/v1/me/logout-all

DELETE /api/v1/me
```

모든 mutation은 CSRF 및 권한 검증.

---

# 64. Internal Admin API 예시

```text
GET  /internal/admin/v1/users
GET  /internal/admin/v1/users/{userId}

POST /internal/admin/v1/users/{userId}/suspend
POST /internal/admin/v1/users/{userId}/resume

PUT  /internal/admin/v1/users/{userId}/groups
PUT  /internal/admin/v1/users/{userId}/roles

POST /internal/admin/v1/users/{userId}/email/reveal

GET  /internal/admin/v1/groups
POST /internal/admin/v1/groups
PATCH /internal/admin/v1/groups/{groupId}
POST /internal/admin/v1/groups/{groupId}/move
DELETE /internal/admin/v1/groups/{groupId}

GET /internal/admin/v1/audit-logs
```

URL의 `{userId}`는 사람이 쓰는 login userId보다 내부 UUID를 사용하는 것을 우선한다.

예:

```text
/users/{userUuid}
```

---

# 65. Error Response

일관된 error schema 사용.

예:

```json
{
  "code": "OTP_INVALID",
  "message": "인증에 실패했습니다.",
  "traceId": "..."
}
```

auth-server의 사용자 인증 API에서는 지나치게 구체적인 보안 정보를 노출하지 않는다.

Internal log에는 traceId를 통해 원인을 추적할 수 있게 한다.

---

# 66. Flyway

모든 DB 변경은 Flyway.

예:

```text
V1__create_users.sql
V2__create_roles.sql
V3__create_groups.sql
V4__create_otp_tables.sql
...
```

Migration은 재현 가능해야 한다.

Production에서 application이 schema를 임의 생성하지 않는다.

---

# 67. Client 등록 Bootstrap

OIDC Client 정보는 DB에서 관리 가능하다.

초기 Client는 environment/config 기반 bootstrap을 제공한다.

예:

```text
HR_CLIENT_ID
HR_CLIENT_SECRET_REF
HR_REDIRECT_URI

APPROVAL_CLIENT_ID
APPROVAL_CLIENT_SECRET_REF
APPROVAL_REDIRECT_URI

ADMIN_CLIENT_ID
ADMIN_CLIENT_SECRET_REF
ADMIN_REDIRECT_URI
```

Startup 시 idempotent bootstrap.

Domain 변경 시 redirect URI를 안전하게 갱신할 수 있는 절차를 문서화한다.

Client Secret 원문은 Auth Server DB에 평문 저장하지 않는다.

---

# 68. Secret Commit 금지

GitHub Public Repository로 공개할 수 있음을 전제로 한다.

절대 commit 금지:

```text
.env
SMTP password
Gmail App Password
JWT Private Key
AES Key
HMAC Key
TOTP Secret
Turnstile Secret
Real OAuth Client Secret
Bootstrap Admin Secret
Production DB Password
```

반드시 제공:

```text
.env.example
```

필요하다면:

```text
secrets.example/
```

에는 dummy file 이름과 형식만 제공한다.

---

# 69. Git Ignore

`.gitignore`에서 최소 다음 보호:

```text
.env
.env.*
!.env.example

secrets/
*.pem
*.key
*.p12
*.jks

node_modules/
dist/
build/
.gradle/
.idea/
```

실제 개발 도구에 맞게 확장한다.

---

# 70. CI/CD

GitHub Actions.

기본 Pipeline:

```text
Pull Request
  ↓
Backend Unit Test
Frontend Test/Lint
  ↓
Testcontainers Integration Test
  ↓
Build
  ↓
Docker Image Build
```

Main Branch:

```text
test
 ↓
build
 ↓
docker image
 ↓
push
 ↓
deploy
```

Deployment 대상:

```text
PC VM
```

SSH 또는 안전한 self-hosted runner 방식을 사용할 수 있다.

GitHub Secret에 Production Secret을 저장할 경우 최소권한으로 관리하고 로그에 출력하지 않는다.

---

# 71. Deployment

Primary:

```text
GitHub
 ↓
CI/CD
 ↓
Container Registry
 ↓
PC VM
 ↓
docker compose pull
 ↓
docker compose up -d
```

Rollback 방법을 DEPLOYMENT.md에 작성한다.

---

# 72. Testing 전략

필수:

1. Domain/Service Unit Test
2. Repository Integration Test
3. Testcontainers PostgreSQL
4. Security Test
5. OIDC Integration Test
6. E2E Test
7. Frontend 주요 Flow Test

자동 테스트에서는:

- 실제 Gmail 발송 금지
- 실제 Turnstile 호출 금지
- 실제 Production Secret 금지

Test Double 제공.

---

# 73. 핵심 E2E Scenario

기존 Step-up 시나리오는 v1에서 제외되었으므로 테스트도 현재 인증 정책에 맞춘다.

## E2E-1: Email OTP SSO

```text
1. HR 접속
2. userId 입력
3. Email OTP 로그인
4. HR 로그인 완료
5. Approval 접속
6. 별도 인증 입력 없이 SSO 로그인
7. Approval Claim에 amr=["email_otp"]
8. acr="urn:jb:loa:1"
```

---

## E2E-2: TOTP 로그인

```text
1. TOTP 등록 사용자
2. HR 접속
3. TOTP 로그인 선택
4. TOTP 인증 성공
5. HR 로그인
6. Approval 접속
7. SSO 로그인
8. amr=["totp"]
9. acr="urn:jb:loa:1"
```

---

## E2E-3: Central / Back-Channel Logout

```text
1. HR 로그인
2. Approval SSO 로그인
3. HR에서 현재 SSO Session 로그아웃
4. Auth Server Session 종료
5. Back-Channel Logout 전송
6. Approval Backend Session 제거
7. Approval 재접속 시 다시 Auth Server 인증 필요
```

---

# 74. 추가 E2E 권장

- 회원가입 + Email 인증
- TOTP 등록
- TOTP Recovery
- Email 변경 후 다른 Session 폐기
- 전체 기기 로그아웃
- Admin Suspend
- Admin Email 재인증 후 5분 unmask
- 5분 후 다시 mask
- Hard Delete
- Group 할당 claim 반영

---

# 75. Testcontainers

Repository Integration Test는 PostgreSQL Testcontainers 사용.

H2로 Production query behavior를 대신하지 않는다.

Flyway migration도 Testcontainer DB에 실제 적용한다.

---

# 76. Test용 OTP

Test profile에서 OTP 값을 직접 Production log에 출력하는 방식은 금지한다.

대신 Test용 Mail Sink 또는 InMemoryMailSender를 제공한다.

테스트 코드는 해당 sink를 통해 OTP를 획득한다.

---

# 77. TOTP 자동 테스트

Test에서는 고정 test secret과 controllable clock을 사용한다.

Production crypto key를 사용하지 않는다.

Clock abstraction을 도입하여:

- OTP expiration
- TOTP window
- Session expiry
- reauth TTL

테스트를 안정적으로 수행할 수 있게 한다.

---

# 78. UI 디자인 가이드

목표:

- 흰 배경 중심
- 명확한 카드/폼
- 과도한 색상 사용 금지
- 반응형
- 오류/성공 메시지 명확
- 인증 상태 badge 사용 가능

Auth Web Home 예:

```text
SSO Lab

[로그인]
[회원가입]

Passwordless Authentication
OIDC SSO Demo
```

Demo Client에는 현재 인증 정보를 잘 보이게 표시한다.

---

# 79. HR / Approval Demo UI

예:

```text
[ HR Service ]

로그인 사용자
홍길동

userId
hong

Email
hong@example.com

Role
USER

Groups
/개발본부/백엔드팀

Authentication
email_otp

ACR
urn:jb:loa:1

[현재 세션 로그아웃]
[전체 기기 로그아웃]
```

Approval도 비슷하게 구성한다.

목적은 업무 UI가 아니라 인증 상태 시연이다.

---

# 80. Admin UI

User List:

- userId
- username
- masked email
- ACTIVE/SUSPENDED
- roles
- group count

Email:

```text
h***@gmail.com
```

[원문 보기] 클릭 시 재인증.

Group UI는 Tree 형태.

Audit UI:

- event
- timestamp
- actor UUID
- target UUID
- success/failure
- source 요약

---

# 81. 로그 정책

Application Log는 구조화된 로그를 권장한다.

Trace ID / Request ID 제공.

민감정보 마스킹.

금지:

```text
logger.info("otp={}", otp)
logger.info("email={}", fullEmail)
logger.debug("accessToken={}", token)
```

---

# 82. Passwordless 인증 용어

README에서 정확한 용어 사용.

현재:

```text
Email OTP OR TOTP
```

중 하나만 요구한다.

따라서 현재 버전은:

```text
Passwordless Multi-Method Authentication
```

이라고 표현한다.

현재 버전을:

```text
Two-Factor Authentication completed
Strong MFA
```

라고 과장하지 않는다.

---

# 83. Future Work — Step-up / MFA

향후 다음 구조로 확장할 수 있도록 설계한다.

```text
loa:1
→ Email OTP OR TOTP

loa:2
→ 별도 Step-up 정책
```

Future Approval 정책 예:

```text
Approval
required_acr = urn:jb:loa:2
```

현재 v1에서는 사용하지 않는다.

향후 가능한 방법:

- Passkey/WebAuthn
- Passwordless first factor + TOTP second factor
- 최근 인증시간 기반 Step-up
- 민감 업무별 `acr_values`

README Future Work에 명시한다.

---

# 84. Future Work — RBAC

현재:

```text
USER
ADMIN
```

향후:

```text
Role
 ↓
Permission
 ↓
Resource / Action
```

으로 확장 가능하도록 한다.

Group 자체는 v1에서 Authorization Rule을 가지지 않는다.

현재 Group은 Claim 전달용.

---

# 85. Future Work — Group Authorization

향후:

```text
Group
 ↓
Role Mapping
 ↓
Client Access Policy
```

확장 가능.

v1에서는 하지 않는다.

---

# 86. Future Work — Infra

향후 고려:

- Redis
- Multi-instance Auth Server
- Distributed Session
- Kubernetes
- AWS deployment
- AWS SSM
- AWS KMS
- Managed PostgreSQL
- Observability stack

v1에서 무리하게 구현하지 않는다.

---

# 87. 보안상 하지 말아야 할 것

AI Agent는 다음 방식으로 “간단하게 만들기 위해” 보안을 우회하면 안 된다.

금지:

1. Email DB 평문 저장
2. TOTP Secret 평문 저장
3. Secret DB 평문 저장
4. JWT Private Key source code 포함
5. Token localStorage 저장
6. 모든 API에 `csrf.disable()`
7. `permitAll()` 남발
8. Admin Server의 DB 직접 접근
9. Client 서비스의 Identity DB 접근
10. Production에서 H2 사용
11. Production에서 JPA ddl-auto update
12. OTP log 출력
13. Recovery Code 평문 저장
14. Internal Admin API public exposure
15. Admin Email 기본 원문 표시
16. 인증 실패 이유로 계정 존재 여부 직접 노출
17. self-signed Production TLS를 기본으로 사용
18. 같은 암호키를 Email/TOTP/HMAC/JWT에 재사용

---

# 88. 외부 서비스 장애 처리

Gmail SMTP 또는 Turnstile 장애 시:

- 500 stacktrace 직접 노출 금지
- 사용자에게 일반화된 오류
- 내부 Audit/Log에 traceId 기록
- retry 정책은 무한 retry 금지
- SMTP 재발송 버튼이 rate limit을 우회하지 못하게 함

---

# 89. Time Handling

모든 DB Timestamp는 UTC 저장.

Frontend 표시 시 Browser timezone 또는 명시된 timezone으로 변환.

JWT/OIDC 시간 Claim도 UTC epoch 기준.

Clock은 테스트 가능하도록 abstraction 고려.

---

# 90. Transaction

다음 작업은 transaction boundary를 명확히 한다.

예:

- 가입 OTP 검증 + User 생성
- Email 변경
- TOTP 등록 완료
- Account Suspend + Session revoke metadata
- User Hard Delete
- Group 이동

외부 network call과 DB transaction을 너무 길게 묶지 않는다.

---

# 91. Concurrency

unique 제약은 application check만 믿지 않는다.

DB Unique Index 필수:

- normalized userId
- email_lookup_hash

중복 가입 race를 DB가 최종 차단하도록 한다.

OTP consume도 concurrency safe하게 구현한다.

동일 OTP를 동시에 두 번 성공 처리하면 안 된다.

Recovery Code 역시 atomic consume.

---

# 92. Data Encryption 구현 주의

AES-GCM 암호화 시 각 암호화마다 random nonce/IV 사용.

Nonce 재사용 금지.

Ciphertext에 key version을 저장하여 향후 Key Rotation 가능하도록 한다.

HMAC key rotation도 Future Work를 고려한다.

---

# 93. JWT Signing

JWT는 asymmetric signing.

권장:

- RSA 또는 EC
- Private Key는 Secret Provider
- JWT Public Key만 JWKS로 공개

JWKS endpoint 제공.

Private Key는 절대 Browser 또는 Client에 전달하지 않는다.

Key Rotation 가능성을 문서로 남긴다.

---

# 94. OIDC Metadata

표준 Discovery endpoint와 JWKS endpoint를 제공한다.

Client들은 issuer 기반 설정을 사용하도록 한다.

issuer는 `AUTH_PUBLIC_URL`을 기준으로 일관되게 구성한다.

외부 서비스 URL 변경 시 issuer 변경 영향에 대해 DOMAIN_CHANGE_GUIDE에 설명한다.

---

# 95. Admin / Internal Network

Admin Internal API는 외부 Caddy에서 route하지 않는다.

가능하면 Docker internal network hostname으로 호출한다.

예:

```text
http://auth-server:8080/internal/admin/v1/...
```

서비스 인증이 있어도 public exposure하지 않는다.

Defense in depth 적용.

---

# 96. README 필수 내용

README에는 최소 다음 포함:

1. 프로젝트 한 줄 소개
2. 핵심 기술
3. Architecture Diagram
4. SSO Demo 설명
5. Passwordless 인증 방식
6. Email OTP/TOTP 차이
7. 현재 v1이 strict MFA가 아니라는 설명
8. Security Highlights
9. Local 실행 방법 링크
10. Demo URL
11. 테스트 실행
12. Future Work
13. Screenshot 또는 GIF 추가 공간
14. Repository 구조

---

# 97. docs/ARCHITECTURE.md

필수:

- 서비스 관계
- DB ownership
- OIDC flow
- BFF 이유
- React 사용 장단점
- MSA 수준을 과도하게 쪼개지 않은 이유
- Admin Server 분리 이유
- Identity data single owner 이유
- Redis 제외 이유

---

# 98. docs/LOCAL_SETUP.md

필수:

- Java 21
- Node LTS
- Docker
- env 생성
- Docker Secret 생성
- Gmail 설정
- Turnstile local mode
- Flyway
- Bootstrap Admin
- hostname
- 전체 기동
- 첫 로그인
- TOTP 등록
- Demo SSO 확인

---

# 99. docs/DEPLOYMENT.md

필수:

- PC VM
- Router Port Forwarding
- Firewall
- Caddy
- TLS
- Docker Compose
- GitHub Actions
- Deploy
- Rollback
- Log 확인
- Health Check
- CGNAT 대안 언급

---

# 100. docs/SECURITY.md

필수:

- Threat Model 요약
- Browser Token 저장 금지 이유
- BFF
- CSRF
- Cookie
- Email 암호화
- HMAC lookup
- TOTP 암호화
- Recovery Code hash
- Secret Provider
- Key separation
- Rate limit
- Turnstile
- Audit
- Enumeration defense
- Admin Email reauth
- Short access token TTL
- Single-node rate limit 한계
- Future Key Rotation

---

# 101. docs/SSO_FLOW.md

다이어그램 포함:

- HR 최초 로그인
- HR → Approval SSO
- Email OTP
- TOTP
- RP-Initiated Logout
- Back-Channel Logout
- Global Logout
- Admin Login
- Admin Reauth

Mermaid 사용 가능.

---

# 102. docs/TEST_GUIDE.md

필수:

- Unit Test
- Integration Test
- Testcontainers
- E2E
- Test Mail
- Test Turnstile
- TOTP Test Clock
- GitHub Actions
- 핵심 E2E 3개

---

# 103. docs/DOMAIN_CHANGE_GUIDE.md

도메인 변경 대상 체크리스트:

- DNS
- Caddy
- env
- AUTH_PUBLIC_URL
- ADMIN_PUBLIC_URL
- HR_PUBLIC_URL
- APPROVAL_PUBLIC_URL
- OIDC issuer
- registered client redirect URI
- logout URI
- frontend API URL
- cookie
- CORS
- certificate
- smoke test

---

# 104. docs/GMAIL_SMTP_SETUP.md

필수:

- Gmail SMTP 선택 이유
- App Password 등 필요한 준비
- 환경변수
- Secret 처리
- From 주소
- 테스트 메일
- 오류 해결
- 실제 secret commit 금지

---

# 105. docs/TURNSTILE_SETUP.md

필수:

- 가입 필요 여부
- Site 생성
- hostname
- site key
- secret
- local test
- production enable
- secret commit 금지

---

# 106. docs/BOOTSTRAP_ADMIN_GUIDE.md

필수:

- BOOTSTRAP_ADMIN_ENABLED
- BOOTSTRAP_ADMIN_EMAIL
- 최초 Admin 가입
- claimed 처리
- Bootstrap 종료
- Admin 복구
- 마지막 Admin 보호

---

# 107. docs/SECRET_MANAGEMENT.md

필수:

- SecretProvider interface
- Environment
- Docker Secret
- AWS SSM
- Local 사용
- AWS 확장
- Secret reference DB
- actual secret DB 저장 금지
- JWT key
- AES key
- HMAC key
- SMTP password
- Turnstile secret

---

# 108. DEVELOPER_SETUP.txt

Repository root에 반드시 생성.

개발자가 가장 먼저 보는 짧은 체크리스트.

예:

```text
1. Java 21 설치
2. Node LTS 설치
3. Docker 설치
4. .env.example → .env 복사
5. Secret 생성
6. Gmail SMTP 설정
7. Turnstile 설정 또는 Local disable
8. Bootstrap Admin Email 설정
9. docker compose up
10. 최초 회원가입
11. Admin 권한 확인
12. HR → Approval SSO 테스트
```

자세한 내용은 docs 링크.

---

# 109. API 문서

각 Backend는 OpenAPI 문서를 제공한다.

사용자-facing Auth API와 Internal Admin API를 명확히 구분한다.

Production 환경에서 Internal Admin API의 Swagger/OpenAPI 문서는 외부에 노출하지 않는다.

---

# 110. Observability 최소 요구

v1에서 별도 ELK/Prometheus 설치는 필수 아님.

최소:

- structured log
- trace/request id
- health
- Audit Log
- authentication failure metrics를 향후 추가하기 쉬운 구조

---

# 111. 실패 처리

인증 관련 실패는 다음 범주를 명확히 구분하되 사용자에게 반환하는 오류 메시지는 과도하게 상세하지 않게 한다.

- invalid OTP
- expired OTP
- exceeded attempts
- rate limited
- account suspended
- invalid TOTP
- invalid recovery code
- internal mail failure
- invalid OIDC client
- invalid redirect URI
- session expired

---

# 112. OIDC Redirect URI 검증

Wildcard Redirect URI 사용 금지.

정확한 allow-list.

예:

```text
https://hr.example/login/oauth2/code/hr-client
```

환경별로 명시적으로 등록.

---

# 113. Client Secret

hr/admin/approval backend는 서로 다른 Client Secret 사용.

한 Secret을 여러 Client가 공유하면 안 된다.

Secret Rotation 가능성을 문서화한다.

---

# 114. Group Claim 갱신

User Group 변경 후 이미 발급된 Token에는 이전 Claim이 남을 수 있다.

새 Token 또는 Session 재인증 이후 최신 Group을 반영한다.

관리자가 Group을 변경했을 때 즉시 모든 세션을 강제 종료하는 기능은 v1 필수 아님.

단, 보안적으로 중요한 Role/Suspend 변경은 Session revoke를 수행한다.

---

# 115. Role 변경

ADMIN Role 부여/제거 시:

- 관련 Session/Token 갱신 필요성을 고려
- 보수적으로 해당 User의 기존 SSO Session 및 Refresh Token을 revoke해도 됨
- Audit 필수

마지막 ACTIVE ADMIN의 ADMIN Role 제거 금지.

---

# 116. Admin User Listing Privacy

Admin List에서:

- email masked
- TOTP secret 절대 표시 금지
- Recovery code 표시 금지
- crypto metadata 노출 금지
- 내부 UUID는 운영 관리 목적에서 표시 가능

---

# 117. 회원 탈퇴 확인

회원탈퇴는 irreversible.

UI에서 명확한 확인 단계.

필요 시 현재 인증수단 재인증을 추가해도 좋다.

최소한 현재 로그인 Session 존재 및 CSRF 검증은 반드시 필요.

삭제 완료 후:

- current session 종료
- auth-web logout
- Client back-channel logout

---

# 118. 포트폴리오 공개 배포 안전장치

이 Monorepo를 포트폴리오용으로 인터넷에 공개 배포할 수 있으므로:

- signup rate limit
- login rate limit
- Turnstile
- SMTP abuse 방지
- user enumeration 완화
- admin internal API 보호
- Production debug mode 금지
- Stacktrace 사용자 노출 금지
- Database public port 금지

필수.

---

# 119. Environment Profile

권장:

```text
local
test
prod
```

Local:

- Turnstile optional
- fake mail optional
- HTTP 허용 가능

Test:

- Testcontainers
- fake mail
- fake turnstile

Prod:

- HTTPS required
- Gmail SMTP
- Turnstile enabled
- Secure Cookie
- Caddy
- actual Secret Provider

---

# 120. 코드 품질

AI Agent는 다음을 지킨다.

- Controller에 business logic 집중 금지
- Domain/Service/Repository 경계
- Crypto Utility를 임의 static helper로 흩뿌리지 않음
- Secret access는 SecretProvider 통일
- Config access는 typed ConfigService 통일
- DTO와 Entity 무분별한 직접 노출 금지
- API validation
- Global Exception Handler
- Security-sensitive code unit/integration test

---

# 121. Dependency 관리

가능하면:

- Spring BOM 사용
- frontend package-lock commit
- Dependabot 등은 선택사항
- known deprecated API를 새 코드에 사용하지 않음

버전 변경 시 README 또는 migration note에 기록.

---

# 122. Definition of Done — 기능

다음이 모두 동작해야 완료이다.

- [ ] 비밀번호 없는 회원가입
- [ ] Email OTP 가입 인증
- [ ] userId unique
- [ ] email unique
- [ ] Email encrypted at rest
- [ ] Email HMAC lookup
- [ ] Email OTP Login
- [ ] TOTP Login
- [ ] TOTP Enrollment
- [ ] Recovery Code
- [ ] TOTP Recovery
- [ ] 사용자 Profile
- [ ] username 변경
- [ ] email 변경
- [ ] Session 목록
- [ ] 개별 Session 종료
- [ ] 전체 기기 로그아웃
- [ ] Hard Delete
- [ ] Role USER/ADMIN
- [ ] Hierarchical Group
- [ ] User N:M Group
- [ ] Admin Group 관리
- [ ] Admin User Suspend
- [ ] Admin Masked Email
- [ ] Admin Re-auth 후 Email reveal
- [ ] Audit
- [ ] HR OIDC Client
- [ ] Approval OIDC Client
- [ ] Admin OIDC Client
- [ ] HR → Approval SSO
- [ ] RP-Initiated Logout
- [ ] Back-Channel Logout
- [ ] Global Logout

---

# 123. Definition of Done — 보안

- [ ] Token Browser Storage 미사용
- [ ] HttpOnly Secure Cookie
- [ ] CSRF protection
- [ ] TLS
- [ ] Secret source code 미포함
- [ ] Secret DB 원문 미저장
- [ ] Email AES-256-GCM
- [ ] TOTP AES-256-GCM
- [ ] HMAC-SHA256 email lookup
- [ ] Recovery Code Argon2id
- [ ] JWT asymmetric signing
- [ ] Key separation
- [ ] OTP 원문 log 미출력
- [ ] Account enumeration 완화
- [ ] Rate Limit
- [ ] Turnstile
- [ ] Admin Internal API 외부 미노출
- [ ] PostgreSQL 외부 미노출

---

# 124. Definition of Done — 테스트

- [ ] Domain/Service Unit Test
- [ ] JPA Repository Test
- [ ] PostgreSQL Testcontainers
- [ ] Flyway Migration Test
- [ ] Email OTP Test
- [ ] TOTP Test
- [ ] Recovery Code Test
- [ ] SSO E2E
- [ ] Back-Channel Logout E2E
- [ ] Admin Email Re-auth Test
- [ ] Account Suspend Test
- [ ] Hard Delete Test
- [ ] GitHub Actions에서 테스트 성공

---

# 125. Definition of Done — 배포

- [ ] 서비스별 Docker Image
- [ ] docker-compose.yml
- [ ] Caddy
- [ ] Public HTTPS
- [ ] Free Subdomain 기반 배포
- [ ] Domain env화
- [ ] PC VM 실행
- [ ] Health Check
- [ ] CI/CD
- [ ] Deploy Guide
- [ ] Domain Change Guide

---

# 126. Definition of Done — 문서

반드시 생성:

- [ ] README.md
- [ ] DEVELOPER_SETUP.txt
- [ ] docs/ARCHITECTURE.md
- [ ] docs/LOCAL_SETUP.md
- [ ] docs/DEPLOYMENT.md
- [ ] docs/SECURITY.md
- [ ] docs/SSO_FLOW.md
- [ ] docs/TEST_GUIDE.md
- [ ] docs/DOMAIN_CHANGE_GUIDE.md
- [ ] docs/GMAIL_SMTP_SETUP.md
- [ ] docs/TURNSTILE_SETUP.md
- [ ] docs/BOOTSTRAP_ADMIN_GUIDE.md
- [ ] docs/SECRET_MANAGEMENT.md
- [ ] docs/VM_SETUP_GUIDE.md
- [ ] docs/AWS_SETUP_GUIDE.md

---

# 127. AI Agent 구현 우선순위

권장 순서:

## Phase 1 — Skeleton

1. Monorepo
2. Backend service 생성
3. Frontend service 생성
4. PostgreSQL
5. Docker Compose
6. Flyway

## Phase 2 — Identity

1. User
2. Role
3. Group
4. Email Encryption
5. SecretProvider
6. System Config

## Phase 3 — Passwordless

1. Signup OTP
2. Login Email OTP
3. TOTP
4. Recovery Code
5. Session

## Phase 4 — OIDC

1. Authorization Server
2. Registered Clients
3. HR Client
4. Approval Client
5. Admin Client
6. Claims
7. SSO

## Phase 5 — Admin

1. admin-server
2. Internal API
3. User 관리
4. Group 관리
5. Suspend
6. Email Re-auth
7. Audit

## Phase 6 — Logout

1. RP-Initiated Logout
2. Back-Channel Logout
3. Global Logout
4. Session UI

## Phase 7 — Security

1. Rate Limit
2. Turnstile
3. CSRF
4. Security headers
5. Secret audit
6. Log masking

## Phase 8 — Infra

1. Caddy
2. Domain
3. TLS
4. CI/CD
5. PC VM deploy

## Phase 9 — Test / Docs

1. Unit
2. Integration
3. E2E
4. Docs
5. README
6. Developer Setup

---

# 128. AI Agent 작업 규칙

AI Agent는 구현 시 다음 규칙을 따른다.

1. 이 문서와 충돌하는 임의의 설계 변경 금지
2. 보안 요구사항을 “Demo라서” 생략 금지
3. 불필요한 마이크로서비스 추가 금지
4. User Service, OTP Service 등을 별도 서비스로 과도하게 쪼개지 않음
5. Auth Server가 Identity Owner
6. Admin Server는 별도 서비스
7. DB 하나
8. Auth Server만 Identity DB 접근
9. 서비스별 Docker Image
10. Frontend도 서비스별 Image
11. Redis 추가 금지
12. 현재 버전에 Step-up 강제 추가 금지
13. Approval도 v1에서는 loa:1
14. Passwordless Login은 Email OTP OR TOTP
15. TOTP 미등록이면 Email OTP만
16. TOTP 등록이면 사용자가 Email OTP/TOTP 선택
17. Recovery Code 일반 Login 금지
18. Secret DB 평문 저장 금지
19. Token Browser 저장 금지
20. 모든 주요 보안 결정 문서화

---

# 129. 구현 중 불가피한 변경

라이브러리 API 차이 등으로 정확한 클래스/endpoint 형태를 변경해야 하는 경우:

1. 핵심 요구사항을 유지한다.
2. 변경 이유를 문서화한다.
3. 보안 수준을 낮추지 않는다.
4. 동일 목적을 달성하는 표준적인 Spring Security 방식 우선.
5. README 또는 ARCHITECTURE에 차이를 기록한다.

---

# 130. 최종 시연 Scenario

포트폴리오 Demo에서는 다음 흐름이 가장 중요하다.

```text
1. 신규 사용자 회원가입
   - userId
   - username
   - email
   - Email OTP

2. HR 접속
   - Email OTP 로그인

3. HR 화면
   - 사용자
   - role
   - group
   - amr=email_otp
   - acr=loa:1

4. Approval 새 탭 접속
   - 로그인 화면 없이 즉시 로그인
   - SSO 확인

5. Auth Web에서 TOTP 등록
   - QR
   - TOTP 확인
   - Recovery Code 10개

6. 전체 로그아웃

7. 다시 로그인
   - TOTP 선택
   - amr=totp

8. Admin 로그인
   - ADMIN Role
   - User 목록
   - masked email

9. Email 원문 보기
   - 재인증
   - 5분 원문 표시

10. HR에서 중앙 로그아웃
    - Approval도 Back-Channel Logout
```

이 흐름을 README GIF/Screenshot으로 보여줄 수 있게 UI를 구성한다.

---

# 131. 프로젝트 성공 기준

이 프로젝트의 핵심 성공 기준은 “기능 개수”가 아니다.

다음이 명확하게 설명되고 실제 동작해야 한다.

- 왜 Auth Server가 Identity의 단일 Owner인가
- 왜 Admin Server가 DB를 직접 보지 않는가
- OIDC SSO가 어떻게 동작하는가
- 왜 Browser에 Token을 저장하지 않는가
- Email OTP/TOTP가 어떻게 다른 로그인 방법으로 동작하는가
- Email을 왜 암호화하면서 HMAC lookup을 함께 쓰는가
- Secret을 왜 DB 원문으로 저장하지 않는가
- 중앙 로그아웃과 Back-Channel Logout이 어떻게 연결되는가
- 현재 버전이 strict MFA가 아닌 이유
- 향후 Step-up/MFA를 어떻게 확장할 수 있는가

---

# 132. 최종 프로젝트 소개문 예시

README 또는 이력서에는 아래 수준의 표현을 참고한다.

> Spring Boot 기반 OIDC Authorization Server를 직접 구성하고, HR/Approval/Admin 서비스 간 SSO를 구현했습니다.
> 비밀번호 대신 Email OTP 또는 TOTP를 선택해 인증하는 Passwordless Multi-Method Authentication을 제공하며, Token은 React Browser에 저장하지 않고 Backend BFF에서 관리했습니다.
> 사용자 이메일과 TOTP Secret은 암호화하여 저장하고, HMAC 기반 Email 검색, Secret Provider 추상화, Session 관리, OIDC Back-Channel Logout, 관리자 재인증 기반 민감정보 조회를 구현했습니다.

현재 구현에서 Step-up 또는 서로 다른 두 factor를 동시에 요구하지 않는다면 “강한 MFA 완료”라고 표현하지 않는다.

---

# 133. 최종 주의사항

이 Monorepo 프로젝트는 학습/포트폴리오 목적이며 인터넷에 공개 배포될 수 있다.

따라서 AI Agent는:

- 비밀정보를 임의로 commit하지 말 것
- 테스트 편의를 위해 Production 보안을 제거하지 말 것
- 실제 Gmail 계정 정보가 로그에 남지 않게 할 것
- 임시 개발용 endpoint를 Production profile에서 제거할 것
- Debug secret/OTP endpoint를 만들지 말 것
- `/internal/**` endpoint를 외부로 공개하지 말 것
- Demo Data가 실제 개인정보처럼 보이지 않게 할 것

---


# 134. 개발 완료 후 환경별 개발자 세팅 가이드

AI Agent는 기능 구현을 완료한 뒤, 개발자가 별도의 추가 질의 없이 프로젝트를 실제로 실행/배포할 수 있도록 **환경별 세팅 가이드**를 반드시 작성해야 한다.

다음 두 문서는 필수 산출물이다.

```text
docs/VM_SETUP_GUIDE.md
docs/AWS_SETUP_GUIDE.md
```

두 문서는 단순 개념 설명이 아니라 **처음 프로젝트를 받는 개발자가 순서대로 따라 하면 실제 서비스가 기동되는 수준의 실행형 가이드**여야 한다.

---

## 134.1 VM_SETUP_GUIDE.md

개인 PC의 VM 또는 일반 Linux VM 환경에 배포하는 방법을 설명한다.

Primary 기준 환경:

```text
Host PC
 ↓
Linux VM
 ↓
Docker / Docker Compose
 ↓
Caddy
 ↓
Auth / Admin / HR / Approval
 ↓
PostgreSQL
```

최소 다음 내용을 포함한다.

1. 권장 Linux 배포판 및 최소 사양
2. CPU / RAM / Disk 권장치
3. VM Network Mode 설명
   - Bridged
   - NAT + Port Forwarding
4. 고정 내부 IP 또는 DHCP Reservation 권장
5. Docker 설치
6. Docker Compose 설치/확인
7. Repository Clone
8. `.env.example` → 실제 `.env` 작성
9. Docker Secret 생성
10. PostgreSQL credential 준비
11. JWT Signing Key 생성 및 배치
12. Email/TOTP/HMAC 암호화 Key 생성
13. Gmail SMTP 설정
14. Cloudflare Turnstile 설정
15. Bootstrap Admin 설정
16. 무료 서브도메인 연결
17. DNS 설정
18. 공유기 80/443 Port Forwarding
19. Host/VM Firewall 설정
20. Caddy 설정
21. Let's Encrypt 인증서 발급 확인
22. Docker Compose 기동
23. Flyway migration 확인
24. Health Check
25. 최초 회원가입
26. Bootstrap Admin 확인
27. HR → Approval SSO 동작 확인
28. TOTP 등록/로그인 확인
29. Back-Channel Logout 확인
30. 로그 확인 방법
31. Container 재기동
32. 서버 재부팅 후 자동 시작 설정
33. Backup 기본 방법
34. Update / Deploy 절차
35. Rollback 절차
36. 장애 발생 시 체크리스트

Public IPv4가 없는 CGNAT 환경에서는 직접 80/443 inbound가 불가능할 수 있음을 설명한다.

이 경우 가능한 대안도 별도 항목으로 안내한다.

예:

```text
Cloudflare Tunnel
또는
다른 outbound tunnel 방식
```

단, Primary Architecture 자체를 Tunnel 의존적으로 변경하지 않는다.

---

## 134.2 AWS_SETUP_GUIDE.md

AWS에 동일 프로젝트를 배포할 경우의 개발자 가이드를 작성한다.

v1의 Primary 배포 환경은 PC VM이지만, 코드와 SecretProvider가 AWS 환경으로 이전 가능하도록 설계되어 있으므로 **AWS 배포 가이드도 반드시 제공한다.**

최소 기준 Architecture:

```text
Internet
 ↓
DNS
 ↓
EC2
 ↓
Caddy
 ↓
Docker Compose
 ↓
Auth / Admin / HR / Approval
 ↓
PostgreSQL
```

소규모 포트폴리오 환경에서는 비용을 줄이기 위해 PostgreSQL도 동일 EC2의 Docker Container로 실행하는 구성을 기본 예제로 사용할 수 있다.

다만 문서에는 향후 RDS로 이전하는 방법과 차이점도 간략히 설명한다.

최소 다음 내용을 포함한다.

1. AWS Account 사전 준비
2. Region 선택
3. 예상 비용이 발생할 수 있는 항목 명시
4. EC2 Instance 생성
5. 권장 Instance Spec
6. Linux AMI 선택
7. EBS 설정
8. Elastic IP 필요 여부 및 장단점
9. Security Group 설정
10. 22/80/443 Inbound 최소권한 구성
11. SSH Key 또는 Session Manager 접근 방법
12. Docker 설치
13. Docker Compose 설치
14. Repository Clone
15. Environment/Profile 설정
16. AWS SSM Parameter Store 사용법
17. `SecureString` 생성
18. IAM Role 생성 및 EC2 연결
19. EC2가 필요한 SSM Parameter만 조회하도록 최소권한 IAM Policy 구성
20. `AwsSsmSecretProvider` 활성화 방법
21. SMTP / Turnstile Secret 배치
22. JWT Signing Key 처리 방법
23. Email/TOTP/HMAC Key 처리 방법
24. 도메인/DNS 연결
25. Caddy 설정
26. Let's Encrypt TLS 확인
27. Docker Compose 기동
28. PostgreSQL 및 Flyway 확인
29. Health Check
30. Bootstrap Admin 생성
31. SSO Smoke Test
32. TOTP Smoke Test
33. Back-Channel Logout Smoke Test
34. GitHub Actions에서 AWS EC2로 배포하는 방법
35. SSH 배포 또는 Self-hosted Runner 사용 시 장단점
36. Container/Volume Backup
37. 서버 재부팅 대응
38. 로그 확인
39. 버전 Update
40. Rollback
41. EC2 종료/재생성 시 주의사항
42. 비용 절감을 위한 리소스 종료/정리 방법

AWS용 문서는 특정 무료 정책이 영구적으로 유지된다고 가정하지 않는다.

즉 다음처럼 작성한다.

> AWS Free Tier 및 Credit 정책은 변경될 수 있으므로 실제 배포 시 AWS Console의 현재 과금 정책을 확인할 것.

---

## 134.3 환경별 설정 차이 표

두 가이드 모두 다음 차이를 명시한다.

| 항목 | Local/PC VM | AWS |
|---|---|---|
| Secret | Docker Secret / Env | AWS SSM SecureString 권장 |
| Secret Provider | Docker/Environment | AwsSsmSecretProvider |
| Public IP | 가정용 회선/Public IPv4 | EC2 Public/Elastic IP |
| Firewall | Router + VM Firewall | Security Group + OS Firewall |
| Domain | 무료 서브도메인 | 동일하게 사용 가능 |
| TLS | Caddy + Let's Encrypt | Caddy + Let's Encrypt |
| DB | Docker PostgreSQL | Docker PostgreSQL 기본, RDS 확장 가능 |
| 배포 | GitHub Actions → VM | GitHub Actions → EC2 |
| Network 이슈 | CGNAT 가능 | Security Group / VPC 고려 |

---

## 134.4 동일 코드베이스 요구사항

VM과 AWS를 위해 별도 소스 브랜치나 별도 구현을 만들지 않는다.

같은 코드와 같은 Docker Image를 사용해야 한다.

환경 차이는 다음으로 처리한다.

```text
Spring Profile
Environment Variable
SecretProvider
system_config
Caddy Configuration
Docker Compose override
```

예:

```text
application-local.yml
application-prod.yml
```

또는 동등한 profile 구조.

AWS 전용 Secret 접근 로직이 Domain/Business Service 내부에 직접 섞이지 않도록 한다.

---

## 134.5 Docker Compose 환경 분리

필요하다면 다음 구조를 사용할 수 있다.

```text
docker-compose.yml
docker-compose.vm.yml
docker-compose.aws.yml
```

단, 중복을 최소화한다.

기본 설정은 `docker-compose.yml`에 두고 환경별 override만 분리한다.

예:

```bash
docker compose \
  -f docker-compose.yml \
  -f docker-compose.vm.yml \
  up -d
```

AWS:

```bash
docker compose \
  -f docker-compose.yml \
  -f docker-compose.aws.yml \
  up -d
```

---

## 134.6 개발자 가이드 검증

AI Agent는 문서를 작성만 하고 끝내지 않는다.

최소한 Local/VM 기준으로 문서 절차를 실제 자동화 테스트 또는 실행 가능한 범위에서 검증한다.

AWS 계정/과금 리소스를 AI Agent가 직접 생성할 수 없는 환경이라면:

- Terraform 등을 임의로 실행해 유료 리소스를 생성하지 않는다.
- 실제 실행이 불가능했던 단계는 명확하게 표시한다.
- 명령어, 필요한 IAM Permission, Environment Variable, 예상 결과를 구체적으로 작성한다.

가이드에는 각 주요 단계별 **정상 확인 방법**을 함께 제공한다.

예:

```text
docker compose ps
curl https://auth.example/actuator/health
OIDC discovery endpoint 확인
TLS certificate 확인
```

---

## 134.7 Definition of Done 추가

개발 완료 조건에 다음을 추가한다.

- [ ] PC VM 배포 가이드 작성
- [ ] AWS EC2 배포 가이드 작성
- [ ] VM용 Secret 설정 가이드 작성
- [ ] AWS SSM SecureString 설정 가이드 작성
- [ ] VM 도메인/TLS 가이드 작성
- [ ] AWS 도메인/TLS 가이드 작성
- [ ] VM 배포 Smoke Test 절차 작성
- [ ] AWS 배포 Smoke Test 절차 작성
- [ ] VM Update/Rollback 절차 작성
- [ ] AWS Update/Rollback 절차 작성
- [ ] 환경별 설정 차이 표 작성

---

# 135. 문서 상태


이 문서는 v1 구현의 Master Specification이다.

현재 확정된 핵심 범위:

```text
OIDC SSO
Passwordless Email OTP
Passwordless TOTP
TOTP Recovery
Hierarchical Group
USER / ADMIN
Admin Service
Email Encryption
Secret Provider
Session Management
Back-Channel Logout
Docker Compose
Caddy TLS
PC VM Deployment
CI/CD
Automated Tests
Developer Documentation
```

Future Work:

```text
Step-up Authentication
Strict MFA
Passkey/WebAuthn
loa:2
RBAC Permission
Group Authorization
Redis
Multi-instance
AWS production deployment
KMS
Observability stack
```

AI Agent는 **v1을 먼저 완성한 뒤에만** Future Work를 추가한다.
