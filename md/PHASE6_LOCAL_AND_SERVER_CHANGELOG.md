# Phase 6 — Logout 로컬 및 Ubuntu VM 변경 이력

작성일: 2026-08-24 (Asia/Seoul)

이 문서는 Phase 6 구현 중 `C:\dev\sso-lab`과 Ubuntu VM 테스트 전용 경로
`/opt/sso-lab-test`의 변경 및 검증 상태를 추적한다. 실제 배포 경로
`/opt/sso-lab`, 운영 `.env`, 운영 `secrets/`, UFW, DDNS 및 VM 네트워크 설정은
변경하지 않는다.

## 1. 현재 상태

```text
로컬 Phase 6 구현: 완료
로컬 Java build/test: 성공
Frontend build: Ubuntu Docker image build에서 4개 서비스 모두 성공
로컬 Testcontainers: Docker 부재로 skip
Ubuntu VM 소스 동기화: 완료
Ubuntu VM Docker/Testcontainers 통합 검증: 완료
```

Phase 6은 로컬 구현과 Ubuntu VM 통합 검증을 모두 마쳐 공식 완료로 표시한다.
Phase 7 — Security는 시작하지 않았다.

## 2. Auth Server 변경

추가 디렉터리:

```text
backend/auth-server/src/main/java/com/ssolab/auth/oidc/logout/
```

주요 구현:

- `OidcSessionIdHasher`: 원본 Spring Session ID를 SHA-256/Base64URL `sid`로 변환
- `OidcClientSessionEntity`/Repository/Service: Auth SSO Session과 실제 OIDC BFF
  client session/authorization 연결 추적
- `RpInitiatedLogoutSuccessHandler`: Spring Authorization Server의 `/connect/logout`
  검증 결과에 현재 Auth Session, 관련 OAuth authorization, BFF back-channel 폐기를 연결
- `BackChannelLogoutDispatcher`: Auth signing key로 `typ=logout+jwt` Token을 발급하고
  등록된 내부 endpoint에 browser 비의존 POST 전달
- `LogoutStateService`/`LogoutCoordinator`: 개별 Session과 전체 사용자 Session의
  metadata, Spring Session JDBC, OAuth authorization/access/refresh credential 및
  client-session 연결을 일관되게 폐기
- `SessionManagementController`/Service: 현재 사용자 소유 Session 목록, 개별 종료,
  global logout API

변경 파일:

```text
backend/auth-server/src/main/java/com/ssolab/auth/oidc/config/OidcAuthorizationServerConfiguration.java
backend/auth-server/src/main/java/com/ssolab/auth/oidc/config/OidcProperties.java
backend/auth-server/src/main/java/com/ssolab/auth/oidc/client/OidcRegisteredClientBootstrap.java
backend/auth-server/src/main/java/com/ssolab/auth/oidc/claims/OidcIdentityClaimsService.java
backend/auth-server/src/main/java/com/ssolab/auth/oidc/claims/OidcTokenCustomizer.java
backend/auth-server/src/main/java/com/ssolab/auth/passwordless/session/AuthSessionService.java
backend/auth-server/src/main/java/com/ssolab/auth/passwordless/session/UserSessionMetadataEntity.java
backend/auth-server/src/main/java/com/ssolab/auth/passwordless/session/UserSessionMetadataRepository.java
backend/auth-server/src/main/java/com/ssolab/auth/passwordless/session/PasswordlessSessionAuthenticationService.java
backend/auth-server/src/main/java/com/ssolab/auth/passwordless/api/PasswordlessController.java
backend/auth-server/src/main/java/com/ssolab/auth/admin/service/AdminCredentialRevocationService.java
backend/auth-server/src/main/resources/application.yml
```

주요 보안 동작:

- Registered Client의 exact `post_logout_redirect_uri` 검증은 Spring Authorization
  Server 표준 RP-Initiated Logout provider가 수행한다.
- wildcard redirect와 임의 redirect는 등록 단계와 endpoint 검증 단계에서 거부한다.
- Logout Token은 RS256 서명, issuer, audience, `iat`, `exp`, `jti`, `events`, `sid`,
  `typ=logout+jwt`를 포함하고 기본 TTL은 2분이다.
- Token/Secret/원본 Session ID는 application log에 출력하지 않는다.
- SUSPENDED/민감 Role 변경의 기존 credential revoke도 Phase 6 global revoke 경계를
  사용해 BFF back-channel까지 연결했다.

## 3. Session Management

API:

```text
GET    /api/v1/me/sessions
DELETE /api/v1/me/sessions/{publicSessionId}
POST   /api/v1/me/logout-all
```

- 인증된 현재 사용자 UUID를 서버에서 기준으로 조회/종료한다.
- 다른 사용자의 공개 Session handle은 `404`로 거부한다.
- mutation은 기존 CSRF 보호를 그대로 사용한다.
- Browser에는 원본 Spring Session ID 대신 무작위 UUID `public_id`만 전달한다.
- UI/API는 대략적 browser/OS, login time, last activity, absolute expiry, current 상태만
  제공하고 전체 User-Agent/IP/Token은 노출하지 않는다.

## 4. Flyway V6

추가 파일:

```text
backend/auth-server/src/main/resources/db/migration/V6__create_logout_and_session_management.sql
```

변경 구조:

```text
auth.user_session_metadata.public_id
auth.user_session_metadata.device_label
auth.oidc_client_sessions
```

`oidc_client_sessions`는 Auth Server 소유 DB 내부에서만 사용하며 Auth Session,
사용자, Registered Client, OAuth authorization ID, 비가역 `sid`, 고정 back-channel URI를
연결한다. BFF 서비스에는 Identity DB dependency/Entity/Repository를 추가하지 않았다.

## 5. HR / Approval / Admin BFF 변경

각 BFF에 다음 구조를 추가했다.

```text
logout/BffSessionRegistry.java
logout/BackChannelLogoutService.java
logout/BackChannelLogoutController.java
```

공통 동작:

- OIDC login 성공 시 서버측 `HttpSession`을 `sid`/`sub`와 연결
- `/api/v1/logout` POST에서 기존 CSRF를 검증한 후
  `OidcClientInitiatedLogoutSuccessHandler`로 `/connect/logout`에 연결
- 각 Client의 exact post-logout URI만 사용
- `/internal/oidc/backchannel-logout`만 CSRF에서 좁게 제외하고 공개 인증은 허용하되,
  실제 종료는 JWT 서명/issuer/audience/만료/형식 검증 성공 후에만 수행
- Spring Security 7의 일반 JWT `typ=JWT` 기본 검사와 OIDC Back-Channel Logout 표준
  `typ=logout+jwt`가 충돌하지 않도록 Logout Token 전용 decoder를 구성하고,
  JOSE 계층에서는 `logout+jwt`만 exact 허용
- 전용 decoder는 RS256 서명, issuer, audience, timestamp를 검증하고 서비스 계층에서
  `events`, `sid`/`sub`, `iat`, `exp`, `jti`, nonce 부재를 추가 검증
- Logout Token `jti` replay를 거부
- Browser storage에 OAuth/OIDC Token을 저장하지 않고 기존 서버측 authorized-client
  session 저장 방식을 유지

`admin-server`, `hr-server`, `approval-server`에는 JPA/JDBC/PostgreSQL dependency 또는
Identity Repository를 추가하지 않았다.

## 6. Frontend 변경

변경 파일:

```text
frontend/auth-web/src/App.tsx
frontend/hr-web/src/App.tsx
frontend/approval-web/src/App.tsx
frontend/admin-web/src/App.tsx
```

- Auth Web: 내 Session 목록, 개별 종료, 모든 기기 logout UI
- HR/Approval/Admin Web: CSRF hidden input을 포함한 RP Logout POST form
- Token은 React state, `localStorage`, `sessionStorage`, `indexedDB`에 저장하지 않음
- Session 원문/Token/Secret은 UI에 표시하지 않음

## 7. 설정 및 Compose 변경

변경 파일:

```text
.env.example
docker-compose.yml
```

추가 placeholder/내부 URI:

```text
HR_BACKCHANNEL_LOGOUT_URI
APPROVAL_BACKCHANNEL_LOGOUT_URI
ADMIN_BACKCHANNEL_LOGOUT_URI
```

Compose에서는 Auth Server가 각 BFF의 Docker-network 내부 endpoint를 사용한다.
Client BFF에는 기존 exact `*_POST_LOGOUT_REDIRECT_URI`를 전달한다. 실제 Secret이나
운영 `.env` 값은 생성/변경하지 않았다.

## 8. 테스트 변경 및 로컬 결과

변경/추가 파일:

```text
backend/auth-server/src/test/java/com/ssolab/auth/OidcPostgresqlIntegrationTest.java
backend/auth-server/src/test/java/com/ssolab/auth/OidcTestProperties.java
backend/hr-server/src/test/java/com/ssolab/hr/HrServerApplicationTest.java
backend/hr-server/src/test/java/com/ssolab/hr/logout/BackChannelLogoutServiceTest.java
backend/approval-server/src/test/java/com/ssolab/approval/ApprovalServerApplicationTest.java
backend/admin-server/src/test/java/com/ssolab/admin/AdminServerApplicationTest.java
scripts/test/verify-phase6-compose.sh
```

로컬 Gradle 결과:

```text
Gradle 8.14.3 / Java 21
tests=44, failures=0, errors=0, skipped=15, executed=29
BUILD SUCCESSFUL
```

로컬 Docker daemon이 없어 PostgreSQL Testcontainers 테스트 15건은 skip되었다.
로컬 Node.js 실행 파일도 없어 4개 Frontend `npm run build`는 Ubuntu 검증 대상으로
유지했다.

정적 검사 결과:

- Browser token storage 사용 없음
- BFF의 Identity DB/JPA/JDBC/PostgreSQL 직접 접근 없음
- Token/Session/Secret 값을 출력하는 logger/System.out 패턴 없음
- `docker-compose.yml`에 host published port 추가 없음

## 9. Ubuntu VM 동기화 및 통합 검증 결과

모든 SSH/SCP 작업은 변경 가능한 공인 IP가 아니라 다음 hostname만 사용했다.

```text
today-sso.duckdns.org
```

테스트 전용 경로의 realpath가 `/opt/sso-lab-test`인지 확인한 후 로컬 최신 소스를
동기화했다. 다음 항목은 동기화 대상에서 제외했다.

```text
.git
.env
secrets/
node_modules/
build/
dist/
logs/
.gradle/
.idea/
.tooling/
.codex/
.agents/
```

`.tooling/`은 로컬 테스트 전용 SSH private key가 들어 있어 명시적으로 추가 제외했다.
기존 `/opt/sso-lab-test/.env.test`와 `.env.phase4.local`은 보존했고 파일 mode `600`을
확인했다. Secret 값은 출력하거나 변경하지 않았다.

VM host에 Java가 설치되어 있지 않아 공식 `gradle:8.14.3-jdk21` container와 Docker
socket을 사용해 PostgreSQL Testcontainers 포함 전체 테스트를 실행했다.

```text
tests=44, failures=0, errors=0, skipped=0, executed=44
BUILD SUCCESSFUL
```

검증 결과:

- PostgreSQL 17.11 container 기동 성공
- Flyway V6 실제 적용 및 `auth.oidc_client_sessions`, `public_id` 확인
- 전체 8개 application Docker image build 성공
- Phase 6 decoder 보완 후 HR/Approval/Admin BFF image 재빌드 성공
- 4개 Frontend의 TypeScript/Vite build 성공
- PostgreSQL 포함 9개 Compose container 모두 `healthy`
- host published port 없음 (`ports={}`)
- `db-network`, `internal-network`는 `internal=true`; public network만 외부 통신 허용
- exact post-logout URI: HR/Approval/Admin 각 Docker 내부 고정 URI, wildcard 0건
- malformed Back-Channel Logout Token은 세 BFF 모두 HTTP 400으로 거부
- 실제 RSA/JWKS `logout+jwt`는 정상 수락하고 잘못된 서명/issuer/audience/expiry는 거부
- RP-Initiated Logout, open redirect 거부, Global Logout, Access/Refresh Token 폐기,
  Session 소유권 및 CSRF 테스트 성공
- 민감정보 application log scan 성공
- Browser token storage 및 Identity DB 서비스 경계 정적 검사 성공

Phase 6 보완으로 서버 테스트 경로에 추가 동기화한 파일:

```text
/opt/sso-lab-test/backend/hr-server/src/main/java/com/ssolab/hr/logout/BackChannelLogoutService.java
/opt/sso-lab-test/backend/approval-server/src/main/java/com/ssolab/approval/logout/BackChannelLogoutService.java
/opt/sso-lab-test/backend/admin-server/src/main/java/com/ssolab/admin/logout/BackChannelLogoutService.java
/opt/sso-lab-test/backend/hr-server/src/test/java/com/ssolab/hr/logout/BackChannelLogoutServiceTest.java
/opt/sso-lab-test/md/AI_AGENT_START_HERE.md
/opt/sso-lab-test/md/PHASE6_LOCAL_AND_SERVER_CHANGELOG.md
```

서버 변경은 `/opt/sso-lab-test`의 테스트 소스와 해당 Compose container 재생성에만
한정했다. 테스트 스크립트는 실행 비트가 보존되지 않아 파일 mode를 바꾸지 않고
`bash scripts/test/verify-phase6-compose.sh`로 실행했다.

## 10. 변경하지 않은 영역

```text
/opt/sso-lab
/opt/sso-lab/.env
/opt/sso-lab/secrets/
UFW
DDNS
VM 네트워크 설정
AI_AGENT_MASTER_SPEC_SSO_LAB.md
```

Phase 7 — Security 기능은 구현하지 않았다.
