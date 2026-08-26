# Phase 7 — Security 로컬 및 서버 변경 이력

작성일: 2026-08-24

로컬 저장소: `C:\dev\sso-lab`

Ubuntu VM 테스트 경로: `/opt/sso-lab-test`

이 문서는 Phase 7 구현 중 로컬 저장소와 테스트 전용 Ubuntu VM에서 변경하거나
검증한 내용을 추적한다. 실제 배포 경로 `/opt/sso-lab`, 운영 `.env`, 운영 `secrets/`,
UFW, DDNS 및 VM 네트워크 설정은 변경하지 않았다.

## 1. 현재 상태

- Phase 1 — Skeleton: 완료
- Phase 2 — Identity: 완료
- Phase 3 — Passwordless: 완료
- Phase 4 — OIDC: 완료
- Phase 5 — Admin: 완료
- Phase 6 — Logout: 완료
- Phase 7 — Security: 완료
- Phase 8 — Infra: 미착수
- Phase 9 — Test / Docs: 미착수

Phase 8 기능은 이번 작업에서 구현하지 않았다.

## 2. Rate Limit 변경

추가한 핵심 파일:

```text
backend/auth-server/src/main/java/com/ssolab/auth/security/ratelimit/RateLimitAction.java
backend/auth-server/src/main/java/com/ssolab/auth/security/ratelimit/RateLimitExceededException.java
backend/auth-server/src/main/java/com/ssolab/auth/security/ratelimit/InMemorySlidingWindowRateLimiter.java
backend/auth-server/src/main/java/com/ssolab/auth/security/ratelimit/SecurityRateLimitService.java
backend/auth-server/src/main/java/com/ssolab/auth/security/ratelimit/SecurityRequestIdentifiers.java
```

단일 Auth Server 인스턴스라는 Master Specification의 v1 전제에 맞춰 Redis를 추가하지
않고 JVM sliding-window limiter를 구현했다. Bucket은 최대 10,000개로 제한했고 IP,
userId, email, challenge 원문 대신 SHA-256/Base64URL fingerprint만 메모리에 저장한다.

| 동작 | Window | IP 제한 | Subject 제한 | Challenge 제한 |
|---|---:|---:|---:|---:|
| Signup start | 10분 | 10 | 3 | - |
| Login start | 10분 | 30 | - | - |
| Email OTP send | 10분 | 20 | 5 | - |
| Email OTP resend | 10분 | 20 | - | 5 |
| Email OTP verify | 10분 | 40 | - | 5 |
| TOTP verify | 10분 | 30 | 10 | - |
| Recovery Code verify | 30분 | 10 | 5 | - |
| Admin re-auth | 10분 | 30 | 10 | 5 |

기존 PostgreSQL OTP 상태의 만료, resend 60초, 최대 실패 5회, atomic consume 제한은
그대로 유지한다. JVM limiter는 HTTP abuse의 추가 방어층이며 프로세스 재시작 시
초기화되고 여러 인스턴스 사이에서 공유되지 않는다.

## 3. Cloudflare Turnstile 변경

추가한 파일:

```text
backend/auth-server/src/main/java/com/ssolab/auth/security/turnstile/TurnstileProperties.java
backend/auth-server/src/main/java/com/ssolab/auth/security/turnstile/TurnstileClient.java
backend/auth-server/src/main/java/com/ssolab/auth/security/turnstile/CloudflareTurnstileClient.java
backend/auth-server/src/main/java/com/ssolab/auth/security/turnstile/TurnstileService.java
backend/auth-server/src/main/java/com/ssolab/auth/security/turnstile/TurnstileVerificationException.java
docs/TURNSTILE_SETUP.md
```

- Production profile 기본값은 `TURNSTILE_ENABLED=true`이다.
- Local/Test profile은 feature flag로 비활성화할 수 있다.
- 보호 대상은 signup start/resend, login start, login email send/resend, recovery start다.
- Site Key만 Auth Web의 Vite build argument로 전달한다.
- Secret은 Browser에 전달하지 않고 기존 `SecretProviderRegistry`를 통해 Auth Server에서만
  읽는다.
- 토큰 누락, 검증 실패, Cloudflare 요청 실패는 fail-closed로 일반화된
  `HUMAN_VERIFICATION_FAILED` 응답을 반환한다.
- 운영 key는 만들거나 저장하지 않았다. VM 검증에는 Cloudflare 공식 public test key만
  컨테이너 환경에 일시 주입했다.

관련 설정 변경:

```text
.env.example
docker-compose.yml
infra/docker/frontend.Dockerfile
backend/auth-server/src/main/resources/application.yml
backend/auth-server/src/main/resources/application-local.yml
backend/auth-server/src/main/resources/application-test.yml
backend/auth-server/src/main/resources/application-prod.yml
frontend/auth-web/src/App.tsx
frontend/auth-web/nginx.conf
```

## 4. Enumeration 및 인증 abuse 방어

추가한 파일:

```text
backend/auth-server/src/main/java/com/ssolab/auth/security/enumeration/EnumerationDefenseProperties.java
backend/auth-server/src/main/java/com/ssolab/auth/security/enumeration/AuthenticationTimingGuard.java
```

변경한 주요 파일:

```text
backend/auth-server/src/main/java/com/ssolab/auth/passwordless/api/PasswordlessController.java
backend/auth-server/src/main/java/com/ssolab/auth/passwordless/api/PasswordlessApiExceptionHandler.java
backend/auth-server/src/main/java/com/ssolab/auth/passwordless/api/SignupStartRequest.java
backend/auth-server/src/main/java/com/ssolab/auth/passwordless/api/LoginOtpRequest.java
backend/auth-server/src/main/java/com/ssolab/auth/passwordless/api/RecoveryStartRequest.java
backend/auth-server/src/main/java/com/ssolab/auth/passwordless/api/ChallengeRequest.java
backend/auth-server/src/main/java/com/ssolab/auth/passwordless/api/TurnstileRequest.java
backend/auth-server/src/main/java/com/ssolab/auth/passwordless/otp/EmailOtpTransactionService.java
backend/auth-server/src/main/java/com/ssolab/auth/admin/internal/InternalAdminController.java
backend/auth-server/src/main/java/com/ssolab/auth/admin/internal/InternalAdminExceptionHandler.java
backend/auth-server/src/main/java/com/ssolab/auth/identity/service/UserIdentityService.java
```

- `/login/start`는 계정 존재 또는 TOTP 등록 여부를 조회하지 않고 항상 동일한 인증 방법
  목록을 반환한다.
- 존재하지 않는 userId의 login email 요청도 DB decoy challenge와 동일한 외부 응답/TTL을
  생성하지만 메일은 발송하지 않는다.
- Signup 중복 검사는 시작 단계에서 노출하지 않고 OTP 성공 후 Identity Service/DB unique
  constraint에서 확정한다.
- OTP/TOTP/Recovery 실패는 일반화된 인증 실패 응답으로 통일한다.
- Production profile은 인증 경로의 최소 응답 시간을 150ms로 설정해 빠른 negative lookup
  차이를 완화한다. Local/Test는 테스트 속도를 위해 0ms다.
- Rate Limit과 Turnstile은 계정 조회보다 먼저 실행한다.
- Signup OTP 검증 후 정상적인 Identity 충돌이 상위 transaction을 rollback-only로 만들지
  않도록 `IdentityConflictException`을 도메인 결과로 처리한다.

## 5. CSRF 및 Security Header 변경

추가한 Backend 공통 정책 파일:

```text
backend/auth-server/src/main/java/com/ssolab/auth/config/SecurityHeaders.java
backend/admin-server/src/main/java/com/ssolab/admin/config/SecurityHeaders.java
backend/hr-server/src/main/java/com/ssolab/hr/config/SecurityHeaders.java
backend/approval-server/src/main/java/com/ssolab/approval/config/SecurityHeaders.java
```

각 SecurityFilterChain에 다음 정책을 적용했다.

- Content-Security-Policy
- `X-Content-Type-Options: nosniff`
- `X-Frame-Options: DENY`
- `Referrer-Policy: no-referrer`
- Permissions-Policy
- HTTPS 요청의 1년 HSTS

Cookie 기반 Auth/Admin/BFF mutation의 Spring Security CSRF를 유지한다. CSRF 예외는 기존
내부 service credential 경계와 exact `/internal/oidc/backchannel-logout` server-to-server
POST로 한정했다. Auth Web CSP에는 Turnstile에 필요한
`https://challenges.cloudflare.com`만 script/frame/connect allow-list로 추가했다.

변경한 Security 설정과 테스트:

```text
backend/auth-server/src/main/java/com/ssolab/auth/config/FoundationSecurityConfig.java
backend/auth-server/src/main/java/com/ssolab/auth/admin/internal/InternalAdminSecurityConfiguration.java
backend/admin-server/src/main/java/com/ssolab/admin/config/OidcBffSecurityConfig.java
backend/hr-server/src/main/java/com/ssolab/hr/config/OidcBffSecurityConfig.java
backend/approval-server/src/main/java/com/ssolab/approval/config/OidcBffSecurityConfig.java
backend/admin-server/src/test/java/com/ssolab/admin/AdminServerApplicationTest.java
backend/hr-server/src/test/java/com/ssolab/hr/HrServerApplicationTest.java
backend/approval-server/src/test/java/com/ssolab/approval/ApprovalServerApplicationTest.java
```

## 6. Secret 및 Log Audit 변경

추가한 파일:

```text
backend/auth-server/src/main/java/com/ssolab/auth/security/logging/SensitiveDataMasker.java
backend/auth-server/src/main/java/com/ssolab/auth/security/logging/SafeSecurityEventLogger.java
docs/SECURITY.md
```

- 보안 이벤트 logger는 action과 일반화된 reason만 받으며 원문 식별자를 받지 않는다.
- 공통 masker는 credential label, Email, JWT 형태를 `<redacted>`로 치환한다.
- 신규 request DTO의 `toString()`은 userId, username, email, Turnstile token을 redacted한다.
- Spring request detail logging 및 사용자-facing stacktrace/message/binding error 노출을
  비활성화했다.
- OTP, TOTP Secret, Recovery Code, Email, OAuth Token/Code, client/internal Secret,
  JWT private/AES/HMAC key, admin re-auth proof를 source와 Compose log에서 점검했다.

## 7. 테스트 변경 및 로컬 결과

추가/변경한 테스트:

```text
backend/auth-server/src/test/java/com/ssolab/auth/security/ratelimit/InMemorySlidingWindowRateLimiterTest.java
backend/auth-server/src/test/java/com/ssolab/auth/security/turnstile/TurnstileServiceTest.java
backend/auth-server/src/test/java/com/ssolab/auth/security/turnstile/CloudflareTurnstileClientTest.java
backend/auth-server/src/test/java/com/ssolab/auth/security/logging/SensitiveDataMaskerTest.java
backend/auth-server/src/test/java/com/ssolab/auth/passwordless/api/SensitiveRequestRedactionTest.java
backend/auth-server/src/test/java/com/ssolab/auth/PasswordlessPostgresqlIntegrationTest.java
scripts/test/verify-phase7-compose.sh
```

로컬 전체 Gradle 결과:

```text
tests=54, failures=0, errors=0, skipped=17, executed=37
```

로컬 PC에 Docker daemon이 없어 Testcontainers 17개는 skip됐고 VM에서 모두 실제
실행했다. 최종 한 줄 수정 후 Windows의 기존 Gradle cache lock이 다른 Windows 사용자
소유여서 로컬 wrapper 재실행은 시작 전에 access denied로 중단됐지만, 같은 수정은 VM의
targeted PostgreSQL 통합 테스트와 전체 54개 테스트로 검증했다.

## 8. Ubuntu VM 동기화 및 통합 검증

접속에는 변경 가능한 공인 IP가 아니라 다음 hostname만 사용했다.

```text
today-sso.duckdns.org
```

`readlink -f`로 대상이 `/opt/sso-lab-test`인지 확인한 뒤 최신 소스를 동기화했다. 다음
항목은 제외했다.

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
.pnpm-store/
```

기존 `/opt/sso-lab-test/.env.test`와 `.env.phase4.local`은 해시가 변경되지 않았고 mode
`600`을 유지했다. 실제 Secret 값은 출력하거나 변경하지 않았다.

VM 전체 Gradle/PostgreSQL Testcontainers 결과:

```text
tests=54, failures=0, errors=0, skipped=0, executed=54
BUILD SUCCESSFUL
```

Docker/Compose 결과:

- Compose config 검증 성공
- Backend 4개 bootJar/image build 성공
- Frontend 4개 TypeScript/Vite/image build 성공
- PostgreSQL 포함 9개 container 모두 `healthy`
- 모든 container host published port 없음 (`ports={}`)
- PostgreSQL은 `db-network`에서 auth-server와만 연결
- admin-server는 internal/public network만 사용하고 Identity DB dependency/model 없음
- Flyway V1~V6 실제 적용 확인; Phase 7 신규 schema/migration은 없음
- 기존 OIDC/Logout exact redirect 및 malformed Logout Token 거부 회귀 성공
- Auth/Admin/HR/Approval Backend Security Header 성공
- Auth Web Turnstile CSP 성공
- CSRF 없는 anonymous mutation HTTP 403
- Turnstile disabled flag 성공, Production default enabled 확인
- Cloudflare 공식 test key로 enabled 성공과 invalid verification HTTP 400 확인
- Phase 7 및 기존 민감 로그 pattern scan 성공
- Browser `localStorage`, `sessionStorage`, `indexedDB` 사용 없음
- Identity DB 서비스 경계 검사 성공

Compose Testcontainers 종료 시 Spring Session cleanup scheduler가 DB 종료와 겹쳐
connection-interrupted 경고를 남겼지만 Gradle은 성공했고 최종 XML은 failure/error 0이었다.

## 9. 발견한 문제와 해결 내용

1. 실제 PostgreSQL에서 signup 중복 충돌 예외가 외부 transaction을 rollback-only로
   표시해 `UnexpectedRollbackException`이 발생했다. Identity 충돌을 정상 도메인 결과로
   처리하도록 transaction rollback 규칙을 보완했고 targeted/전체 테스트를 재통과했다.
2. 기존 테스트 env에 Phase 7 `TURNSTILE_ENABLED`가 없어 Compose의 안전 기본값 `true`가
   적용됐다. env 파일을 수정하지 않고 테스트 명령에만 `false`를 주입해 disabled 동작을
   검증했으며 production 기본값은 계속 `true`로 유지했다.
3. Auth Server Alpine image의 BusyBox wget에 cookie jar 옵션이 없어 첫 Turnstile 실요청이
   시작 전에 중단됐다. 응답 header에서 임시 session cookie를 파싱하는 방식으로 변경해
   CSRF를 포함한 실제 enabled 성공/실패 요청을 완료하고 임시 파일을 삭제했다.
4. 최초 로컬 sync archive를 workspace 내부에 생성해 archive 자체와 `.pnpm-store` 접근
   오류가 발생했다. 서버 전송 전 중단됐으며 불완전 파일을 제거한 뒤 OS 임시 경로 사용과
   `.pnpm-store` 제외로 해결했다.

## 10. 운영 전에 필요한 외부 설정

- Cloudflare Turnstile production widget 생성
- Auth Web용 public Site Key 설정
- Auth Server용 Turnstile Secret을 기존 SecretProvider에 등록
- Production에서 `TURNSTILE_ENABLED=true` 유지
- Phase 8의 Caddy/TLS 뒤 실제 client IP 전달/신뢰 proxy 범위 확정
- Reverse proxy 환경에서 Rate Limit IP key가 최종 client IP를 사용하도록 forwarded-header
  신뢰 경계 검증
- 단일 Auth Server 인스턴스 전제 유지; multi-instance 전환 시 공유 rate limiter 설계

## 11. 변경하지 않은 영역

- `/opt/sso-lab` 실제 배포 경로
- 운영 `.env`와 운영 `secrets/`
- UFW, DDNS, VM network 설정
- Identity DB 소유권과 서비스 경계
- Browser token storage 정책
- Passwordless/OIDC/Admin/Logout 보안 정책
- Phase 8 Caddy/TLS/배포 자동화
- Phase 9 최종 E2E/Test/Docs 범위
