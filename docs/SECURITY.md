# Security Architecture

이 문서는 SSO Lab v1의 현재 보안 경계와 Phase 7 hardening 정책을 설명한다.
기능·아키텍처의 최종 기준은 `md/AI_AGENT_MASTER_SPEC_SSO_LAB.md`이다.

## Threat model 요약

주요 위협은 공개 인증 endpoint의 OTP/SMTP abuse, 계정 존재 여부 추론, credential
brute force, CSRF, OAuth Token의 Browser 노출, Redirect 악용, 내부 API 호출 위조,
DB/로그/예외 응답을 통한 Secret 유출이다. 단일 Auth Server instance를 전제로 하며
Redis와 분산 Rate Limit은 v1 범위가 아니다.

## Identity와 Token 경계

- Identity DB는 `auth-server`만 직접 접근한다.
- Admin Web → Admin Server → Auth Server Internal API 경계를 유지한다.
- React는 Access/Refresh/ID Token을 `localStorage`, `sessionStorage`, IndexedDB에 저장하지
  않는다. BFF가 Authorization Code + PKCE와 Token을 server-side에서 관리한다.
- Email은 AES-256-GCM, lookup은 별도 HMAC-SHA256 key를 사용한다.
- TOTP Secret은 별도 AES-256-GCM key, Recovery Code는 Argon2id hash를 사용한다.
- JWT signing, Email/TOTP encryption, Email/OTP HMAC key를 서로 재사용하지 않는다.
- Access Token 기본 TTL은 5분이다. Stateless token이 Suspend/revoke 직후 짧은 기간
  남을 수 있는 위험을 제한하기 위한 값이다.

## Rate Limit

HTTP abuse 제한은 단일 JVM의 sliding-window map으로 처리하고 모든 key를 SHA-256
fingerprint로만 보관한다. 원문 IP, userId, email은 bucket key에 저장하지 않는다.

| 동작 | Window | IP | Subject | Challenge |
|---|---:|---:|---:|---:|
| Signup start | 10분 | 10 | 3 | - |
| Login start/method | 10분 | 30 | - | - |
| Email OTP send | 10분 | 20 | 5 | - |
| Email OTP resend | 10분 | 20 | - | 5 |
| Email OTP verify | 10분 | 40 | - | 5 |
| TOTP verify | 10분 | 30 | 10 | - |
| Recovery Code verify | 30분 | 10 | 5 | - |
| Admin re-auth | 10분 | 30 | 10 | 5 |

Subject는 동작에 따라 normalized userId, email lookup HMAC을 포함한 signup 식별자,
또는 내부 actor UUID이다. IP는 reverse proxy 처리 후 `remoteAddr`를 사용한다.

OTP challenge의 최대 5회 실패, 60초 resend, expiry 및 atomic consume는 계속 PostgreSQL
상태로 강제한다. In-memory Rate Limit은 프로세스 재시작 시 초기화되며 여러 Auth Server
instance 사이에서 공유되지 않는다. Multi-instance/Redis는 Future Work이다.

## Turnstile

Production profile은 Turnstile을 기본 활성화한다. 보호 대상은 signup start, login start,
Email OTP send/resend 및 Recovery start이다. Local/Test는 명시적으로 비활성화할 수 있다.

- 공개 Site Key는 Auth Web container 시작 시 `/runtime-config.js`에 주입한다. immutable
  image나 build argument에 고정하지 않는다.
- Secret Key는 Auth Server `SecretProvider`에서만 읽는다.
- 누락/실패/Cloudflare 장애는 fail-closed이며 일반화된 오류만 반환한다.
- 자동 테스트는 실제 Cloudflare/Production Secret을 사용하지 않는다.

설정 절차는 `docs/TURNSTILE_SETUP.md`를 따른다.

## Enumeration 방어

- Login method 응답은 사용자 존재/TOTP 등록 여부를 조회하지 않고 동일한 방법 목록을
  반환한다.
- 알 수 없는 userId의 Email OTP 요청도 DB decoy challenge와 동일 응답/TTL을 반환하지만
  메일은 발송하지 않는다.
- Signup 중복 여부는 OTP 성공 후 transaction과 DB unique constraint에서 최종 확인한다.
- OTP/TOTP/Recovery 실패 응답은 `AUTHENTICATION_FAILED`로 통일한다.
- Production 인증 응답은 최소 처리 시간 guard를 적용해 빠른 negative lookup 차이를
  완화한다.
- Rate Limit과 Turnstile을 계정 조회 전에 적용한다.

## CSRF와 Cookie

Cookie 기반 Auth/BFF API는 Spring Security CSRF를 유지한다. React는 `/api/v1/csrf`에서
토큰을 받아 mutation header/form field로 전송한다. 예외는 다음 server-to-server POST로
한정한다.

- `/internal/admin/v1/**`: 별도 service credential, stateless, internal network
- `/internal/oidc/backchannel-logout`: RS256 Logout Token 검증

Session cookie는 HttpOnly, SameSite=Lax이며 Production에서는 Secure이다.

## Self-service 변경과 삭제

- Profile API는 인증된 본인 정보만 반환하고 다른 사용자 검색을 제공하지 않는다.
- username 변경은 현재 Session을 유지하고 `USERNAME_CHANGED` Audit을 남긴다. 새
  `preferred_username` claim은 다음 OIDC 발급부터 반영한다.
- email 변경은 새 email로 전송한 OTP가 성공해야 한다. 사용자별 최신 pending 요청만
  유효하며 DB에는 새 email도 AES-256-GCM ciphertext/HMAC lookup 형태로만 둔다.
- email 변경을 수행한 현재 Auth Session만 유지하고 다른 Auth Session, 모든 Refresh
  Token과 HR/Approval/Admin BFF Session을 폐기한다.
- Hard Delete는 Email OTP 또는 TOTP 기반 fresh re-authentication을 요구한다. UI 확인
  문구는 실수 방지일 뿐 Backend authorization을 대체하지 않는다.
- Hard Delete는 credential, OTP/pending email 개인정보, OAuth authorization/token,
  Spring Session과 membership을 삭제하고 `ACCOUNT_DELETED` Audit만 UUID 기반으로
  보존한다. Audit에 새 email/userId를 복사하지 않는다.
- 마지막 ACTIVE ADMIN의 자기 Hard Delete는 차단하며 consumed Bootstrap claim은 삭제나
  재시작 후에도 계정을 자동 복원하지 않는다.

## Security Headers

Backend는 CSP, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`,
`Referrer-Policy: no-referrer`, Permissions-Policy와 HTTPS 요청의 1년 HSTS를 설정한다.
Frontend Nginx도 CSP/nosniff/frame/referrer 정책을 적용한다. Auth Web CSP는 Cloudflare
Turnstile의 script/frame/connect origin만 추가 허용한다. Phase 8 Caddy/TLS에서 HSTS와
reverse proxy header의 중복·충돌을 최종 확인한다.

## Secret와 Log 정책

- 실제 Secret은 `.env.example`, Git, Frontend bundle, audit row에 저장하지 않는다.
- OTP, TOTP Secret, Recovery Code, Email, OAuth Token/Code, client/internal Secret,
  private/AES/HMAC key, admin re-auth proof DTO는 redacted `toString`을 사용한다.
- request detail logging과 사용자-facing stacktrace/message 노출은 비활성화한다.
- 인증 보안 이벤트 logger는 action과 일반화된 reason만 받고 원문 식별자를 받지 않는다.
- 공통 masker는 credential label, Email, JWT 형태를 `<redacted>`로 치환한다.
- Compose 검증은 실행 로그를 민감 패턴으로 검사하며 source audit은 Browser Token
  storage와 Identity DB dependency 경계를 함께 확인한다.

## Key rotation

Email/TOTP ciphertext는 key version을 저장한다. 실제 re-encryption과 HMAC/JWT rotation
runbook은 Future Work이며 운영 전 별도 backup과 rollback 절차가 필요하다.
