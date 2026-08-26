# Phase 4 로컬 및 Ubuntu VM 변경 이력

- 작성일: 2026-08-21 (Asia/Seoul)
- 로컬 Repository: `C:\dev\sso-lab`
- Ubuntu VM 테스트 경로: `/opt/sso-lab-test`
- 보호 대상 운영 경로: `/opt/sso-lab`
- 공식 Phase 기준: `AI_AGENT_MASTER_SPEC_SSO_LAB.md`의 9개 Phase
- 현재 상태: **Phase 4 — OIDC 완료**
- 다음 구현 대상: **Phase 5 — Admin (미착수)**

이 문서는 Phase 4에서 적용한 로컬 소스, DB Migration, VM 테스트 환경 및 검증 결과를 추적하기 위한 기록이다. 실제 Client Secret, RSA Private Key, 토큰, Authorization Code, 비밀번호 및 `.env.test`/`.env.phase4.local` 값은 기록하지 않는다.

---

## 1. 구현 범위

Master Specification의 Phase 4 범위만 구현했다.

- Spring Authorization Server 기반 OAuth 2.1/OIDC Provider
- HR, Approval, Admin용 confidential Registered Client
- Authorization Code Flow + PKCE S256
- ID Token, JWT Access Token, rotating Refresh Token
- OIDC Discovery, JWKS, UserInfo
- Identity 기반 OIDC Claim
- Phase 3 Passwordless Session과 Authorization 요청 연결
- HR/Approval/Admin BFF OAuth2 Client와 서비스 간 SSO
- React에 토큰을 전달하지 않는 server-side BFF Session
- Flyway V4 및 PostgreSQL Testcontainers 검증

Phase 5 Admin 관리 기능, Phase 6 Logout/back-channel logout, 실제 Gmail SMTP, 최종 Browser E2E 및 운영 Reverse Proxy는 구현하지 않았다.

---

## 2. 로컬 설정 및 의존성 변경

| 파일 | 변경 내용 |
|---|---|
| `backend/auth-server/build.gradle.kts` | Spring Authorization Server, WebMVC test, Spring Security test 의존성 추가 |
| `backend/hr-server/build.gradle.kts` | Spring OAuth2 Client/BFF와 테스트 의존성 추가 |
| `backend/approval-server/build.gradle.kts` | Spring OAuth2 Client/BFF와 테스트 의존성 추가 |
| `backend/admin-server/build.gradle.kts` | Spring OAuth2 Client/BFF와 테스트 의존성 추가 |
| `backend/auth-server/src/main/resources/application.yml` | issuer, signing key reference, TTL, 3개 Client와 exact Redirect URI 설정 추가 |
| 각 Client `application.yml` | public authorize URI, internal token/JWKS/UserInfo URI, Client/Redirect 설정, Secure Session Cookie 추가 |
| `.env.example` | RSA signing key, 3개 Client Secret 및 exact Redirect URI용 placeholder 추가 |
| `docker-compose.yml` | auth-server 및 BFF의 OIDC 환경변수 연결. 외부 publish port는 추가하지 않음 |
| `md/AI_AGENT_START_HERE.md` | Phase 4 완료, Phase 5 다음 대상으로 진행 상태 갱신 |

실제 운영 `.env`, Secret 파일 또는 운영 RSA/Client Secret은 생성하거나 수정하지 않았다.

---

## 3. Flyway V4

추가 파일:

- `backend/auth-server/src/main/resources/db/migration/V4__create_oidc_authorization_server.sql`

추가 테이블:

| 테이블 | 역할 |
|---|---|
| `auth.oauth2_registered_client` | Client metadata, bcrypt Client Secret, exact Redirect URI, grant/scope/token 정책 |
| `auth.oauth2_authorization` | Authorization Code, Access/ID/Refresh Token 및 authorization 상태의 JDBC 저장 |
| `auth.oauth2_authorization_consent` | 표준 Authorization Consent 저장 구조 |

Spring Security Authorization Server 7.1의 공식 JDBC schema를 PostgreSQL 17에 맞춰 `timestamptz`와 `text`로 적용했다. 실제 VM에서 Flyway V1~V4가 모두 `success=true`임을 확인했다.

---

## 4. Authorization Server 구조

주요 패키지:

- `com.ssolab.auth.oidc.config`
- `com.ssolab.auth.oidc.client`
- `com.ssolab.auth.oidc.crypto`
- `com.ssolab.auth.oidc.claims`
- `com.ssolab.auth.oidc.flow`

핵심 파일:

- `OidcAuthorizationServerConfiguration.java`
- `OidcProperties.java`
- `OidcRegisteredClientBootstrap.java`
- `ManagedRegisteredClientRepository.java`
- `OidcSigningKeyLoader.java`
- `OidcIdentityClaimsService.java`
- `OidcTokenCustomizer.java`
- `OidcContinuationService.java`

구조와 정책:

- Spring Authorization Server endpoint filter chain을 application filter chain보다 우선 적용
- Authorization, Token, UserInfo, Discovery, JWKS endpoint 활성화
- Registered Client/Authorization/Consent를 auth DB JDBC에 저장
- RSA 2048-bit 이상 key pair를 `SecretProvider` reference로 로드
- Private/Public RSA modulus 일치 확인 후 RS256 JWK 제공
- 시작 시 HR/Approval/Admin 3개 Client를 idempotent upsert
- Client Secret은 `SecretProvider`로 읽고 DB에는 bcrypt encoded 값만 저장
- Identity가 `ACTIVE`가 아니면 신규 발급과 Refresh 모두 차단
- JDBC authorization metadata 역직렬화는 `PasswordlessPrincipal`, `ArrayList`, OIDC 표준 `Long`/`Date`만 좁게 추가 허용

---

## 5. Registered Client와 Redirect URI

| Client | 유형 | Grant | 인증 | PKCE | Scope |
|---|---|---|---|---|---|
| `hr-client` | Confidential BFF | authorization_code, refresh_token | client_secret_basic | S256 필수 | openid, profile, email |
| `approval-client` | Confidential BFF | authorization_code, refresh_token | client_secret_basic | S256 필수 | openid, profile, email |
| `admin-client` | Confidential BFF | authorization_code, refresh_token | client_secret_basic | S256 필수 | openid, profile, email |

보안 규칙:

- Implicit와 Resource Owner Password Credentials grant 미등록
- Redirect URI는 absolute URI 한 개씩 exact allow-list 등록
- wildcard, fragment 및 부정확한 URI 설정은 시작 시 거부
- Refresh Token rotation 사용, 이전 Refresh Token 재사용 거부
- Consent 화면은 현재 first-party BFF Client 정책에 따라 요구하지 않음

VM DB 확인 결과 Client 3개, bcrypt Secret 3개, wildcard Redirect URI 0개였다.

---

## 6. Authorization Code + PKCE 흐름

```text
React -> Client BFF /oauth2/authorization/{client}
      -> Auth /oauth2/authorize (code_challenge=S256)
      -> 미인증이면 기존 Passwordless Login 화면
      -> Email OTP 또는 TOTP 성공
      -> 저장된 안전한 상대 authorize 경로로 continuation
      -> exact Redirect URI로 Authorization Code 반환
      -> BFF가 server-to-server Token endpoint에서 code_verifier + Client 인증
      -> BFF Session에 OAuth2AuthorizedClient 저장
      -> React에는 선별된 사용자 Claim만 반환
```

`OidcContinuationService`는 저장된 요청 중 `/oauth2/authorize` 상대 경로만 복원하며 외부 absolute redirect를 반환하지 않는다. Passwordless 성공 시 Session fixation 방지를 위한 Session 교체 후 필요한 continuation만 새 Session으로 전달한다.

검증 항목:

- 정상 Authorization Code 발급/교환
- S256 PKCE 포함 및 잘못된 verifier 거부
- exact allow-list 밖 Redirect URI 400 거부
- Authorization Code 두 번째 사용 거부
- 코드 재사용 검증을 Refresh rotation 시나리오와 분리

---

## 7. Token 종류와 TTL

| Token | TTL | 저장/관리 |
|---|---:|---|
| Authorization Code | 2분 | auth DB JDBC authorization 상태 |
| Access Token | 5분 | RS256 JWT, BFF server-side Session |
| ID Token | 5분 | RS256 JWT, BFF server-side Session |
| Refresh Token | 8시간 | opaque value, auth DB + BFF server-side Session, rotation |

Spring Authorization Server 7.1의 기본 ID Token TTL이 30분이므로 `OidcTokenCustomizer`에서 Master 요구값인 5분으로 명시적으로 제한했다.

Token, Code, Client Secret은 application log에 출력하지 않는다. Docker 전체 log에 대해 민감 값 이름+값 패턴을 검사했고 검출 0건이었다.

---

## 8. OIDC Claim 설계

발급 Claim:

- `sub`: 변경 불가능한 User UUID
- `userId`, `preferred_username`: 업무 로그인 ID
- `username`, `name`: 표시 이름
- `email`: `email` scope가 있을 때만 Phase 2 AES-256-GCM 값을 auth-server 내부에서 복호화
- `roles`: 정렬된 Role 이름
- `groups`: `/개발본부/백엔드팀` 형식의 전체 계층 경로
- `amr`: `email_otp` 또는 `totp`
- `acr`: `urn:jb:loa:1`
- `auth_time`: Passwordless 실제 인증 시각
- `sid`: Phase 3 Session ID

UserInfo는 검증된 ID Token Claim 중 허용 목록만 반환한다. Client 서비스는 Identity repository나 Email 암호화 key에 접근하지 않는다.

---

## 9. Passwordless Session 연결과 SSO

변경 파일:

- `PasswordlessPrincipal.java`
- `PasswordlessSessionAuthenticationService.java`
- `PasswordlessController.java`
- `AuthenticationResponse.java`
- `SessionCookieConfiguration.java`

연결 방식:

- OIDC는 Phase 3 `PasswordlessPrincipal`이 존재하는 인증 Session만 사용자 인증으로 인정
- 임시 사용자, form login, basic auth 또는 개발용 bypass 없음
- Principal에 UUID, Role, 인증 방법, 인증 시각, Session ID를 보존
- 매 요청의 Phase 3 Session validation과 SUSPENDED 검사를 유지
- Spring Session Cookie를 `HttpOnly`, `SameSite=Lax`, 환경별 `Secure`로 명시
- 전역 CSRF 비활성화 없음

동일 Auth Passwordless Session으로 HR, Approval, Admin Authorization Code와 Token 교환이 모두 성공함을 Testcontainers에서 확인했다. Admin BFF는 추가로 `ROLE_ADMIN`이 없는 사용자 Session API 접근을 403으로 거부한다.

---

## 10. HR/Approval/Admin BFF와 React

각 Backend에 추가된 구조:

- `config/OidcBffProperties.java`
- `config/OidcBffSecurityConfig.java`
- `api/OidcSessionController.java`

BFF 정책:

- Spring Security OAuth2 Client 사용
- Client Secret은 Backend 환경변수에서만 사용
- PKCE S256 authorization request customizer 강제
- Authorization Code/Token 교환은 Backend에서 수행
- Access/Refresh/ID Token은 server-side HTTP Session에만 저장
- React에는 `/api/v1/session`으로 선별된 verified Claim만 제공
- CSRF 기본 보호 유지, form/basic login 비활성화
- exact issuer/endpoint/redirect 설정 검증

Frontend 변경:

- `frontend/auth-web/src/App.tsx`: Phase 3 Passwordless Login 후 OIDC continuation 처리
- `frontend/hr-web/src/App.tsx`: BFF Login 링크와 Session Claim 표시
- `frontend/approval-web/src/App.tsx`: BFF Login 링크와 Session Claim 표시
- `frontend/admin-web/src/App.tsx`: BFF Login 링크와 Admin Session 상태 표시
- 각 `styles.css`: Phase 4 화면 스타일

`localStorage`, `sessionStorage`, `indexedDB` 사용은 정적 검사 결과 0건이다.

---

## 11. 테스트 변경 및 결과

주요 테스트 파일:

- `backend/auth-server/src/test/java/com/ssolab/auth/OidcPostgresqlIntegrationTest.java`
- `backend/auth-server/src/test/java/com/ssolab/auth/OidcTestProperties.java`
- `backend/hr-server/src/test/java/com/ssolab/hr/HrServerApplicationTest.java`
- `backend/approval-server/src/test/java/com/ssolab/approval/ApprovalServerApplicationTest.java`
- `backend/admin-server/src/test/java/com/ssolab/admin/AdminServerApplicationTest.java`
- 기존 Phase 1~3 PostgreSQL integration test에 test-only RSA/Client Secret property 등록

로컬 결과:

- Java 21 / Gradle 8.14.3 `clean build`: 성공, 33 tasks
- 로컬 Docker 부재로 PostgreSQL Testcontainers 13건: 예상대로 skipped
- auth/hr/approval/admin React `npm run build`: 4개 모두 성공
- Browser token storage 정적 검사: 통과
- Identity DB 직접 접근 의존성/repository 정적 검사: 통과

Ubuntu VM 결과:

- `./gradlew test --rerun-tasks --no-daemon`: 성공
- 총 34 tests, skipped 0, failures 0, errors 0
- PostgreSQL Testcontainers Phase 1/2: 4건 성공
- Passwordless PostgreSQL Testcontainers Phase 3: 8건 성공
- OIDC PostgreSQL Testcontainers Phase 4: 1건 성공
- 나머지 crypto/validation/BFF unit/application tests: 성공
- 보강된 OIDC test에서 HR → Approval → Admin 동일 Auth Session SSO 정상 교환 확인

---

## 12. Ubuntu VM 및 Docker 변경

테스트 경로에서만 수행했다.

- 로컬 최신 소스를 `/opt/sso-lab-test`에 동기화
- 테스트 전용 `/opt/sso-lab-test/.env.phase4.local` 생성, mode `600`
- 기존 `/opt/sso-lab-test/.env.test` 값은 수정하지 않음
- `.env.test` SHA-256은 작업 전후 동일함
- VM system Java 설치 없이 `/opt/sso-lab-test/.tooling/jdk21` 테스트 runtime 사용
- `scripts/test/verify-phase4-compose.sh` 동기화 및 실행
- 8개 application image build 성공
- PostgreSQL 포함 9개 Compose container 재기동 및 모두 healthy
- Flyway V1~V4 실제 적용 확인
- OIDC Discovery/JWKS 확인
- 모든 container Host port bindings `{}` 확인
- `db-network`에는 PostgreSQL과 auth-server만 연결됨
- auth-server만 `public/internal/db-network`에 연결됨
- HR/Approval/Admin은 DB network에 연결되지 않음
- Docker 민감 로그 패턴 검사 통과

테스트 VM에서 과거 동기화 방식 때문에 남아 있던 Client의 구형 `FoundationSecurityConfig.java` 3개는 Phase 4 source 구조와 충돌하여 테스트 경로에서만 제거했다. 로컬에서는 해당 파일이 새 OIDC BFF security config로 대체되어 있었다.

`/opt/sso-lab`, 운영 `.env`, 운영 `secrets/`, UFW, DDNS 및 네트워크 설정은 변경하지 않았다.

---

## 13. 발견한 문제와 해결

| 문제 | 해결 |
|---|---|
| Boot 4 테스트 starter/API 차이 | Boot 4.1 공식 WebMVC test starter와 현재 Security API 사용 |
| Windows archive 동기화 후 VM에 구형 Security 파일 잔존 | 테스트 경로의 정확한 stale 파일 3개만 제거 |
| Spring Session Cookie header에 HttpOnly 미표시 | `SessionCookieConfiguration`으로 HttpOnly/SameSite/Secure 명시 |
| MockMvc authorize param이 실제 query string으로 인식되지 않음 | 테스트를 실제 browser와 같은 `queryParam` 요청으로 변경 |
| JDBC authorization metadata가 custom Principal을 거부 | `PasswordlessPrincipal`과 필요한 안전한 값 타입만 좁게 allow-list |
| immutable JDK collection이 JDBC metadata 복원을 방해 | 역할/그룹/amr을 `ArrayList`로 정규화 |
| ID Token 기본 TTL 30분 | Master 요구대로 5분으로 customizer에서 제한 |
| `auth_time` Long이 Refresh 처리의 Date 기대값과 충돌 | OIDC 표준 처리와 호환되는 `Date`로 보존 |
| Code 재사용 시험이 기존 Refresh Token을 폐기할 수 있음 | 별도 code를 발급해 재사용 시험을 다른 token lifecycle과 격리 |

---

## 14. 운영 전 직접 준비할 항목

- RSA 2048-bit 이상 운영 signing key pair와 key rotation 절차
- HR/Approval/Admin별 충분히 긴 독립 Client Secret
- 운영 HTTPS issuer URL
- 운영 HTTPS exact Redirect URI와 post-logout Redirect URI
- 운영 Reverse Proxy/TLS에서 `/oauth2/**`, discovery, JWKS, UserInfo 및 각 BFF callback routing
- 운영 환경의 SecretProvider backend 또는 Docker Secret 파일
- `SESSION_COOKIE_SECURE=true` 유지와 운영 Domain/Cookie 정책 확인
- 향후 실제 Gmail SMTP Secret과 Browser E2E 환경은 Master의 후속 Phase에서 준비

실제 Secret 값은 Git, 이 문서, application log 및 command argument에 저장하지 않는다.

---

## 15. 남은 TODO와 Phase 5

Phase 4 범위의 구현과 테스트 VM 통합 검증은 완료했다.

후속 TODO:

- 실제 HTTPS domain/Reverse Proxy가 준비된 환경의 최종 Browser E2E
- 실제 Gmail SMTP 발송 검증
- Phase 6의 RP-Initiated/Back-channel Logout
- Phase 7의 전체 보안 검증 강화
- Phase 8의 운영 Infra 연동
- Phase 9의 전체 Test/Docs 완성

다음 **Phase 5 — Admin**에서는 Master Specification에 따라 Admin 기능, Identity 관리 API/화면, 정책 기반 권한 경계 및 관련 감사 요구사항을 구현한다. Phase 5 작업은 아직 시작하지 않았다.
