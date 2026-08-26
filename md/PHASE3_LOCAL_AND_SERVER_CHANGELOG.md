# Phase 3 로컬 및 Ubuntu VM 변경 이력

- 작성일: 2026-08-20 (Asia/Seoul)
- 로컬 Repository: `C:\dev\sso-lab`
- Ubuntu VM 테스트 경로: `/opt/sso-lab-test`
- 보호 대상 운영 경로: `/opt/sso-lab`
- 공식 Phase 기준: `AI_AGENT_MASTER_SPEC_SSO_LAB.md`의 9개 Phase
- 현재 상태: **Phase 3 — Passwordless 완료**
- 다음 구현 대상: **Phase 4 — OIDC (미착수)**

이 문서는 Phase 3에서 적용한 로컬 소스, DB Migration, VM 테스트 환경 및 검증 결과를 추적하기 위한 기록이다. 실제 Secret, 비밀번호, OTP, TOTP Secret, Recovery Code 원문 및 `.env.test` 값은 기록하지 않는다.

---

## 1. 구현 범위

Master Specification의 Phase 3 범위만 구현했다.

- Signup Email OTP
- Login Email OTP
- RFC 6238 TOTP 등록/검증
- Recovery Code 발급/사용/TOTP 복구
- JDBC 기반 Passwordless Session metadata
- Passwordless 사용자 API와 CSRF 보호
- Verification Mail abstraction와 test mail sink
- Flyway V3 및 PostgreSQL Testcontainers 검증

Phase 4 OIDC Provider, Registered Client, Token/Claim/SSO 기능은 구현하지 않았다.

---

## 2. 설정 및 의존성 변경

| 파일 | 변경 내용 |
|---|---|
| `backend/auth-server/build.gradle.kts` | Spring Security Argon2id 구현에 필요한 `bcprov-jdk18on:1.85` 추가. Boot BOM 미관리 의존성이므로 공식 안정 버전 직접 고정 |
| `backend/auth-server/src/main/resources/application.yml` | OTP HMAC/TOTP AES key reference, Secure/HttpOnly/SameSite=Lax Session Cookie 설정 추가 |
| `.env.example` | OTP HMAC 및 TOTP AES key용 비실사용 placeholder/reference 추가 |
| `docker-compose.yml` | `auth-server`에 OTP/TOTP provider/reference/version 환경설정 전달. 외부 publish port 추가 없음 |
| `md/AI_AGENT_START_HERE.md` | Phase 3 완료, Phase 4 다음 대상으로 진행 상태 변경 |

실제 key와 실제 `.env` 값은 생성하거나 수정하지 않았다.

---

## 3. Flyway V3

추가 파일:

- `backend/auth-server/src/main/resources/db/migration/V3__create_passwordless_authentication.sql`

추가 테이블:

| 테이블 | 역할 |
|---|---|
| `auth.pending_registrations` | 가입 OTP 검증 전 userId/username/암호화 Email 임시 보관 |
| `auth.email_otp_challenges` | 목적별 OTP HMAC verifier, 만료, 실패 횟수, 재발송, 소비 상태 |
| `auth.totp_credentials` | 사용자별 AES-256-GCM 암호화 TOTP Secret과 등록 상태/마지막 counter |
| `auth.recovery_codes` | Argon2id hash, batch, 사용/무효화 시각 |
| `auth.user_session_metadata` | 인증 방법, 제한 scope, idle/absolute 만료, 무효화 상태 |

DB 보호 규칙:

- OTP 원문 column 없음
- TOTP Secret 평문 column 없음
- Recovery Code 원문 column 없음
- Email 평문 column 없음
- OTP verifier는 32-byte HMAC-SHA256만 허용
- TOTP IV는 12 bytes, key version은 양수
- OTP 최대 시도/실패 횟수 및 시간 정합성 CHECK
- Recovery/User/Session FK와 cascade 정책
- Session method/scope allow-list
- 조회 및 만료 처리를 위한 index

---

## 4. Signup/Login Email OTP

주요 패키지:

- `com.ssolab.auth.passwordless.otp`
- `com.ssolab.auth.passwordless.mail`

핵심 파일:

- `EmailOtpTransactionService.java`
- `PasswordlessEmailOtpService.java`
- `EmailOtpChallengeEntity.java`
- `PendingRegistrationEntity.java`
- `OtpCodeGenerator.java`
- `OtpVerifier.java`
- `VerificationMailSender.java`
- `InMemoryVerificationMailSender.java`
- `UnavailableVerificationMailSender.java`

정책:

- `SecureRandom` 6자리 OTP
- 기본 TTL 5분
- 최대 실패 5회
- 재발송 대기 60초
- Typed System Config override 사용
- challenge UUID + purpose + OTP를 별도 HMAC key로 HMAC-SHA256
- 원문 OTP DB/로그 저장 금지
- 목적을 `SIGNUP`, `LOGIN`, 향후 `EMAIL_CHANGE`, `ADMIN_REAUTH`로 구분
- 성공 시 즉시 consumed 처리하여 재사용 차단
- 검증/소비에 pessimistic lock 사용
- 재발송 시 이전 OTP 즉시 무효화, 실패 횟수는 reset하지 않아 우회 방지
- 존재하지 않거나 잘못된 userId도 decoy challenge와 동일 일반 응답 사용
- SUSPENDED 사용자는 mail 발송 및 인증 성공 대상에서 제외

Signup 성공 transaction에서 userId/Email unique를 다시 검사하고 Phase 2의 암호화 Email을 그대로 User Identity로 이전하며 USER Role을 부여한다.

---

## 5. TOTP

주요 패키지:

- `com.ssolab.auth.passwordless.totp`
- `com.ssolab.auth.passwordless.crypto`

핵심 파일:

- `TotpAlgorithm.java`
- `TotpService.java`
- `TotpCredentialEntity.java`
- `TotpSecretCipher.java`
- `PasswordlessCryptoProperties.java`

정책:

- RFC 6238 호환
- HMAC-SHA1
- 6 digits
- 30-second period
- current ±1 step 검증
- 160-bit random TOTP Secret
- 실제 코드 확인 전 `PENDING`, 성공 후 `ACTIVE`
- TOTP Secret은 별도 key로 AES-256-GCM 암호화
- 12-byte random IV, 128-bit tag, user UUID/key version AAD
- 마지막 성공 counter 저장으로 동일 TOTP 재사용 차단
- `otpauth://` URI는 응답 가능하나 객체 `toString()`에서는 redacted
- TOTP 활성화 성공 시 Recovery Code 10개 발급

---

## 6. Recovery Code와 제한 복구 흐름

주요 파일:

- `RecoveryCodeGenerator.java`
- `RecoveryCodeService.java`
- `RecoveryCodeEntity.java`
- `RecoveryCodeBatch.java`
- `TotpRecoveryService.java`

정책:

- 10개 발급
- 각 code는 약 130-bit entropy의 Crockford-style alphabet 사용
- Bouncy Castle 기반 Spring Security Argon2id hash
- code별 random salt가 포함된 encoded hash만 DB 저장
- 사용 시 `used_at` 기록
- 재발급 시 기존 미사용 code 전부 invalidation
- 평문은 발급 응답에서 한 번만 제공하며 객체 log 문자열에서는 redacted
- row lock으로 원자적 단일 소비
- Recovery Code는 일반 로그인 endpoint에 연결하지 않음
- 성공 시 기존 TOTP 폐기 후 새 pending TOTP를 생성
- Recovery session scope는 `RECOVERY_ONLY`
- Master에 별도 Recovery Session TTL이 없어 기존 5분 re-auth TTL을 보수적으로 재사용
- 새 TOTP 확인 후 제한 Session 즉시 종료

---

## 7. Session과 API

주요 파일:

- `AuthSessionService.java`
- `PasswordlessSessionAuthenticationService.java`
- `PasswordlessSessionValidationFilter.java`
- `UserSessionMetadataEntity.java`
- `PasswordlessPrincipal.java`
- `PasswordlessController.java`
- `CsrfController.java`
- `PasswordlessApiExceptionHandler.java`

Session 정책:

- 기존 Spring Session JDBC `auth.SPRING_SESSION` 사용
- 기본 idle timeout 30분
- absolute timeout 8시간
- Remember Me 없음
- Email OTP/TOTP 성공 시 server-side SecurityContext Session 생성
- Session metadata에 실제 인증 방법과 scope 기록
- 매 인증 요청에서 idle/absolute expiry, invalidation, Account 상태 확인
- SUSPENDED 사용자 Session 거부 및 무효화
- TOTP 등록/Recovery Code 재발급은 5분 이내 fresh authentication 요구
- Cookie는 Secure, HttpOnly, SameSite=Lax 기본

주요 API:

```text
GET  /api/v1/csrf

POST /api/v1/signup/start
POST /api/v1/signup/resend
POST /api/v1/signup/verify

POST /api/v1/login/start
POST /api/v1/login/email/send
POST /api/v1/login/email/resend
POST /api/v1/login/email/verify
POST /api/v1/login/totp/verify

POST /api/v1/me/totp/enroll/start
POST /api/v1/me/totp/enroll/confirm
DELETE /api/v1/me/totp
POST /api/v1/me/recovery-codes/regenerate

POST /api/v1/totp/recovery/start
POST /api/v1/totp/recovery/enroll/confirm
DELETE /api/v1/me/session
```

모든 POST/DELETE mutation에 Spring Security CSRF를 유지했다. 공개 로그인 방법 시작 응답은 계정 존재/TOTP 등록 여부를 드러내지 않도록 두 선택지를 일반적으로 표시하고, 실제 가능 여부는 일반화된 인증 실패로 처리한다.

---

## 8. 테스트 변경

추가 Unit Test:

- `passwordless/totp/TotpAlgorithmTest.java`
- `passwordless/crypto/SensitiveCodeTest.java`
- `passwordless/api/SensitiveRequestRedactionTest.java`

추가 PostgreSQL Testcontainers Test:

- `PasswordlessPostgresqlIntegrationTest.java` — 8개 시나리오

검증 시나리오:

- Flyway V3 및 금지 평문 column
- Signup OTP 성공/재사용 차단
- Login Email OTP 성공/재사용 차단
- OTP 만료/5회 제한/60초 재발송 제한
- SUSPENDED 사용자 인증 차단
- TOTP 등록/암호화 저장/로그인/counter 재사용 차단
- TOTP 해제와 기존 Recovery Code 전체 무효화
- Recovery Code 10개/단일 사용/재사용 차단/제한 복구
- Session 생성/absolute 만료/명시 무효화/SUSPENDED 무효화
- RFC 6238 test vector와 ±1 window
- 요청/응답 보안 객체의 인증값 redaction

---

## 9. 로컬 검증

사용 도구:

- JDK 21: `C:\dev\sso-lab\.tooling\jdk21\jdk-21.0.12+8`
- Gradle 8.14.3: `C:\dev\sso-lab\.tooling\gradle-8.14.3`
- Repository 전용 Gradle cache

최종 결과:

| 항목 | 결과 |
|---|---|
| 전체 clean build | `BUILD SUCCESSFUL`, 33 tasks |
| TOTP 해제 추가 후 최종 build | `BUILD SUCCESSFUL`, 28 tasks |
| auth-server 전체 테스트 발견 | 25 |
| 로컬 성공 | 14 |
| 로컬 skipped | Docker 부재로 Testcontainers 12 |
| 로컬 실패/오류 | 0/0 |
| `git diff --check` | 통과 |
| 타 Backend Identity DB 접근 scan | 0건 |
| 인증 Secret/OTP logger pattern scan | 0건 |

---

## 10. Ubuntu VM 변경 및 검증

소스는 `/opt/sso-lab-test`에만 동기화했다. archive는 다음을 제외했다.

```text
.git
.env
.env.test
secrets/
node_modules/
build/
dist/
logs/
.tooling/
.gradle/
.gradle-user-home/
.pnpm-store/
.idea/
```

- archive entries: 309
- 금지 entries: 0
- `/opt/sso-lab-test/.env.test` 변경 전후 SHA-256 동일:
  `3a2bc19266c9b0a9ebc14fe299451d5f14f3d0ba99b412c3cf522bd8c5429ef2`
- 실제 값은 확인/기록하지 않음

VM Testcontainers 최종 결과:

| 항목 | 결과 |
|---|---|
| 전체 auth-server 테스트 | 26 |
| 성공 | 26 |
| skipped/failure/error | 0/0/0 |
| Phase 1/2 PostgreSQL regression | 4/4 성공 |
| Phase 3 PostgreSQL integration | 8/8 성공 |
| Unit Test | 14/14 성공 |

Docker Compose:

- 8개 application image build 성공
- 9개 service 모두 running/healthy
- Backend 4개 health JSON `status=UP`
- Flyway V1/V2/V3 success
- Phase 3 table 5개 실제 존재
- DB 금지 평문 column count 0
- 모든 container host `PortBindings={}`
- PostgreSQL은 db network만 사용
- auth-server만 db/internal/public network 사용
- admin-server는 internal/public network 사용
- 기타 server/web은 public network 사용
- auth-server log 인증값 assignment pattern count 0
- Compose stack은 테스트 경로에서 계속 실행 중

---

## 11. 검증 중 발견하고 해결한 문제

1. Spring 7 다중 생성자 선택
   - `TotpSecretCipher`, `RecoveryCodeService`에 운영/테스트 생성자가 함께 있어 context 생성 실패
   - 운영 생성자에 `@Autowired` 명시

2. TOTP 공유 PK JPA mapping
   - Hibernate 7 merge가 신규 `@MapsId` credential을 null identifier로 처리
   - DB의 `user_id` PK/FK 구조는 유지하고 Entity는 UUID 공유 PK를 직접 mapping

3. 만료 테스트와 DB CHECK
   - 만료시각만 과거로 바꾼 테스트 SQL이 `expires_at > created_at` 제약 위반
   - 안전한 DB 제약은 유지하고 테스트의 생성/만료시각을 함께 이동

4. VM PATH
   - 최초 host-side Gradle 명령이 tool path만 지정해 `uname`, `xargs` 누락
   - 표준 Ubuntu system path를 함께 지정

5. DB 사용자 가정
   - 최종 조회에서 `.env.example` 기본 사용자명을 가정해 조회 실패
   - 실제 값을 출력하지 않고 PostgreSQL container 환경변수로 조회하도록 변경

---

## 12. 보호한 운영 영역과 남은 TODO

변경하지 않음:

- `/opt/sso-lab`
- 운영 `.env`, `secrets/`, DB, container
- UFW, DDNS, VM network
- SSH `authorized_keys`
- `/opt/sso-lab-test/.env.test`

운영 전 준비 필요:

- 기존 Email AES key 및 Email lookup HMAC key
- 별도의 OTP HMAC-SHA256 key
- 별도의 TOTP AES-256-GCM key
- 승인된 SecretProvider reference
- Gmail SMTP/App Password와 실제 `VerificationMailSender` 구현/설정
- Phase 7의 Turnstile, Rate Limit 및 추가 보안 hardening
- Production Secure Cookie/TLS

현재 Production profile의 mail sender는 운영 Gmail이 구성되지 않은 상태에서 OTP를 버리거나 로그에 출력하지 않고 fail-closed 오류를 반환한다. Test profile만 endpoint가 없는 in-memory sink를 사용한다.

Phase 4 요청 전까지 OIDC Provider, Client 등록, Token, Claims, SSO를 구현하지 않는다.
