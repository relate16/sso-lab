# Phase 9 — Test / Docs 요구사항 추적표

작성일: 2026-08-31

기준 문서: `AI_AGENT_MASTER_SPEC_SSO_LAB.md`, `AI_AGENT_START_HERE.md`
기준 커밋: `a2d1d06eb5095bcbe1c0da2227adea255efbc907`

## 1. 목적과 검증 원칙

이 문서는 Master Specification §72~77, §96~109, §122~127, §134의 완료 조건과
Repository의 자동화·문서 증거를 연결한다. Phase 9에서 발견되어 별도 승인된
Profile/username/Email 변경/Hard Delete 기능 결손은 기준 커밋에 포함되어 있으며,
이제부터는 Test / Docs 보강만 수행한다.

- `충족`: 자동 테스트, 운영 검증 또는 문서 증거가 이미 존재한다.
- `부분`: 하위 계층 증거는 있으나 Master가 요구하는 시스템/Browser 수준 증거가 부족하다.
- `진행`: Phase 9에서 구현 또는 검증 중이다.
- `미검증`: 실제 외부 계정·과금·Production 변경이 필요해 승인 없이 실행하지 않는다.

자동 테스트는 Test Mail Sink/InMemory sender, Turnstile test double 또는 공식 공개 테스트
키, 고정 test secret과 controllable clock만 사용한다. 실제 Gmail, 실제 Turnstile 요청,
Production Secret 및 Production DB를 사용하지 않는다.

## 2. 기능 Definition of Done (§122)

| 기능 | 구현/검증 증거 | 상태 | Phase 9 완료 증거 |
|---|---|---|---|
| Passwordless 가입, Email OTP 가입 인증 | `PasswordlessPostgresqlIntegrationTest` | 충족 | Backend 전체 회귀 + Browser 가입 흐름 |
| userId/email unique | V2 unique constraint, Identity/Passwordless PostgreSQL test | 충족 | Testcontainers 회귀 |
| Email AES-256-GCM/HMAC lookup | `EmailCryptoTest`, PostgreSQL ciphertext assertion | 충족 | 보안 최종 감사 |
| Email OTP Login | `PasswordlessPostgresqlIntegrationTest` | 충족 | E2E-1 |
| TOTP Login/Enrollment | `TotpAlgorithmTest`, `PasswordlessPostgresqlIntegrationTest` | 충족 | controllable clock E2E-2 |
| Recovery Code/TOTP Recovery | `PasswordlessPostgresqlIntegrationTest` | 충족 | Frontend 및 Browser recovery flow |
| 사용자 Profile | self-only API/UI, `UserSelfServicePostgresqlIntegrationTest` | 충족 | Frontend test, OpenAPI, Browser E2E |
| username 변경 + Audit | self-service API/UI, `USERNAME_CHANGED` DB assertion | 충족 | 현재 Session 유지/새 claim Browser E2E |
| 새 Email OTP 기반 Email 변경 | V7 pending model, self-service API/UI, DB integration | 충족 | 다른 Auth/BFF Session 및 모든 Refresh Token 폐기 E2E |
| Session 목록/개별 종료 | Session Management API/UI, logout integration test | 충족 | Frontend test, Browser session flow |
| 전체 기기 로그아웃 | Logout coordinator 및 DB/BFF tests | 충족 | Browser global logout |
| Hard Delete | fresh re-auth, cascade cleanup, audit preservation DB test | 충족 | Browser E2E, OpenAPI, 문서 |
| Role USER/ADMIN | V2, Identity/Admin integration tests | 충족 | 전체 회귀 |
| Hierarchical Group/User N:M Group | V2, `GroupHierarchyServiceTest`, Admin integration | 충족 | Admin frontend test, group claim E2E |
| Admin Group/User/Suspend | Admin API/BFF/UI, Admin PostgreSQL integration | 충족 | 일반 USER 403, Admin Browser flow |
| Admin masked Email/re-auth reveal | Admin service/BFF/UI integration | 충족 | reveal 및 5분 뒤 remask Browser E2E |
| Audit | V5 + username/email/delete/admin event assertions | 충족 | 개인정보/Secret 최종 감사 |
| HR/Approval/Admin OIDC clients | V4 bootstrap 및 OIDC integration | 충족 | 3개 BFF frontend/E2E |
| HR → Approval SSO | Auth 계층 integration + Email OTP/TOTP Browser E2E | 충족 | Playwright E2E 1/2 통과 |
| RP-Initiated/Back-Channel/Global Logout | Auth/BFF integration/unit + Browser central/back-channel 검증 | 충족 | Playwright E2E 1 통과 |

## 3. 보안 Definition of Done (§123)

| 보안 요구사항 | 현재 증거 | 상태 | Phase 9 조치 |
|---|---|---|---|
| Browser Token Storage 미사용 | BFF session 구조, 정적 검색 | 충족 | Frontend/E2E 및 최종 정적 감사 |
| HttpOnly/Secure/SameSite Cookie | Production profile, Compose/Caddy 운영 검증 | 충족 | 설정 회귀 |
| CSRF | Auth/Admin/BFF CSRF endpoint와 tests | 충족 | Frontend test 및 Browser mutation 검증 |
| TLS | 4개 Production hostname Public TLS 검증 | 충족 | Production 무변경, 문서화 |
| Secret source 미포함/DB 원문 미저장 | SecretProvider, Docker Secrets, commit audit | 충족 | 최종 Git 후보/로그 감사 |
| Email/TOTP AES-256-GCM, HMAC-SHA256 | Crypto unit/integration tests | 충족 | 전체 회귀 |
| Recovery Code Argon2id | Recovery service DB assertions | 충족 | 전체 회귀 |
| JWT asymmetric signing/key separation | OIDC key loader 및 OIDC integration | 충족 | 문서/설정 감사 |
| OTP/Token/Secret log 미출력 | request redaction, masker tests | 충족 | test log 및 source scan |
| Account enumeration 완화 | timing guard와 generic response tests | 충족 | Backend abuse 회귀 |
| Rate Limit/Turnstile | unit/integration + Phase 7 Docker 검증 | 충족 | double 기반 회귀 |
| Internal Admin API/PostgreSQL 외부 미노출 | Caddy/Compose network tests 및 Production 검증 | 충족 | OpenAPI 경계·Compose 회귀 |

## 4. 테스트 Definition of Done (§72~77, §124)

| 테스트 계층/시나리오 | 현재 증거 | 상태 | Phase 9 작업 |
|---|---|---|---|
| Domain/Service Unit | Identity/Crypto/TOTP/Config/RateLimit/Turnstile/Masking tests | 충족 | 전체 Gradle 회귀 |
| JPA Repository Integration | Auth/Passwordless/OIDC/Admin/Self-service PostgreSQL suites | 충족 | PostgreSQL 17 Testcontainers 강제 실행 |
| Flyway Migration | V1~V7 clean DB 적용 및 schema/FK assertions | 충족 | migration history 검증 |
| Security | CSRF, enumeration, rate limit, Turnstile, redaction tests | 충족 | abuse 및 service-boundary audit |
| OIDC Integration | Code+PKCE, exact redirect, token/refresh/UserInfo/claims | 충족 | Browser SSO 연결 |
| Email OTP/TOTP/Recovery | service 및 PostgreSQL integration | 충족 | test sink/clock 기반 Browser 흐름 |
| Admin re-auth/Suspend | Admin PostgreSQL integration | 충족 | Browser admin flow |
| Hard Delete | 실제 PostgreSQL FK/cascade/audit preservation assertions | 충족 | Browser E2E |
| Frontend 주요 Flow | 네 Web Node test runner API test 11개 + typecheck/build | 충족 | Auth 3, Admin 4, HR 2, Approval 2 통과 |
| E2E-1 Email OTP HR→Approval SSO | `amr=email_otp`, `acr=urn:jb:loa:1` Browser 검증 | 충족 | Playwright 통과 |
| E2E-2 TOTP HR→Approval SSO | test-only controllable clock으로 Browser SSO 검증 | 충족 | Playwright 통과 |
| E2E-3 Central/Back-Channel Logout | HR logout 후 Approval BFF Session 폐기 검증 | 충족 | Playwright 통과 |
| 권장 E2E | Profile/username/email revoke/Hard Delete/Admin suspend/re-auth | 충족 | 추가 Playwright 2개 통과 |
| GitHub Actions | 기준 커밋 CI run `33358447150` 성공, Phase 9 workflow에 test/OpenAPI/docs/audit 추가 | 충족 | 사용자 지시상 미commit이므로 새 원격 run은 commit 승인 후 수행 |

## 5. OpenAPI (§109)

| Backend | 문서 범위 | 외부 공개 정책 | 상태 |
|---|---|---|---|
| auth-server | Passwordless, Profile, username, Email change, Hard Delete, Session/OIDC public API | public spec만 사용자-facing; Internal Admin spec은 별도 파일/경로 | 충족 |
| admin-server | OIDC BFF session, User/Role/Group/Suspend/Re-auth proxy API | Admin hostname 인증 경계 내부 | 충족 |
| hr-server | OIDC login/session/logout BFF API | HR hostname 사용자-facing | 충족 |
| approval-server | OIDC login/session/logout BFF API | Approval hostname 사용자-facing | 충족 |
| Auth Internal Admin | Admin Server 전용 `/internal/**` | Production Caddy에서 문서와 endpoint 모두 외부 차단 | 충족 |

모든 spec은 wildcard redirect, 실제 hostname의 Secret/credential, 예시 Token/OTP/Email을
포함하지 않는다. CI/로컬 검증에서 YAML parse와 endpoint drift를 확인한다.

## 6. 문서 Definition of Done (§96~109, §126, §134)

| 파일 | 현재 상태 | 완료 조건 |
|---|---|---|
| `README.md` | 완료 | §96의 14개 항목과 새 self-service 기능 반영 |
| `DEVELOPER_SETUP.txt` | 완료 | 처음 받는 개발자의 실행/테스트 체크리스트 |
| `docs/ARCHITECTURE.md` | 완료 | 서비스/DB/OIDC/BFF/React/Admin/Redis 결정 기록 |
| `docs/LOCAL_SETUP.md` | 완료 | Secret 없는 Local/Test 실행 절차와 확인 명령 |
| `docs/DEPLOYMENT.md` | 완료 | immutable release/deploy-only/rollback/V7 영향 보강 |
| `docs/SECURITY.md` | 완료 | self-service re-auth/revocation/delete/audit 보강 |
| `docs/SSO_FLOW.md` | 완료 | Email OTP/TOTP SSO 및 3종 logout 흐름 |
| `docs/TEST_GUIDE.md` | 완료 | unit/integration/E2E/doubles/CI 실행법 |
| `docs/DOMAIN_CHANGE_GUIDE.md` | 완료 | issuer/exact URI/Caddy/TLS 변경 검증 |
| `docs/GMAIL_SMTP_SETUP.md` | 완료 | Gmail App Password와 test sink 경계 |
| `docs/TURNSTILE_SETUP.md` | 완료 | 공개 Site Key/서버 Secret/test double 경계 |
| `docs/BOOTSTRAP_ADMIN_GUIDE.md` | 완료 | one-shot/delete 비복원/마지막 Admin 정책 |
| `docs/SECRET_MANAGEMENT.md` | 완료 | VM Docker Secret/AWS SSM Future Work/rotation 목록 |
| `docs/VM_SETUP_GUIDE.md` | 완료 | §134.1 VM network/Docker/Secret/TLS/운영/rollback 절차 |
| `docs/AWS_SETUP_GUIDE.md` | 완료 | §134.2 IAM/SSM/RDS/비용 및 현재 provider 경계 |

AWS 가이드는 실행 가능한 명령과 필요한 권한을 문서화하되, 과금 리소스를 실제 생성하지
않는다. VM과 AWS는 같은 source/image를 사용하고 환경 차이는 profile/env/SecretProvider/
Caddy/Compose override로만 처리한다.

## 7. 배포 DoD 보존 (§125)

Phase 8에서 서비스별 image, Compose, Caddy, Public HTTPS, DuckDNS, domain env, VM health,
CI/CD, deploy/rollback/domain guide가 검증되었다. Phase 9는 Production을 변경하거나
재배포하지 않으며, 문서와 격리 테스트가 기존 외부 공개 경계 및 PostgreSQL volume 보존
정책을 훼손하지 않는지 회귀 검증한다.

## 8. Phase 9 실행 게이트와 산출물

1. 요구사항 추적표를 현재 기준으로 확정한다.
2. Backend unit/integration/Testcontainers 보강과 전체 회귀를 수행한다.
3. 4개 Frontend 주요 flow 테스트를 추가한다.
4. 격리 Browser E2E를 Test Mail Sink, Turnstile double, controllable TOTP clock으로 수행한다.
5. 각 Backend OpenAPI와 public/internal 경계를 검증한다.
6. 필수 15개 문서와 README/Developer Setup을 완성한다.
7. Local 및 `/opt/sso-lab-test`에서 전체 회귀를 수행한다.
8. Secret/PII/log/Browser storage/service boundary를 최종 감사한다.

각 단계의 명령과 결과는 `docs/TEST_GUIDE.md`와 최종 Phase 9 변경 기록에 남긴다. Phase 9
변경은 사용자 검토 전 commit/push하지 않으며, Production migration/deploy도 수행하지 않는다.

## 9. 최종 격리 검증 결과

- Local Backend `clean test`: 성공. Docker 부재로 Testcontainers 21개는 skip되었으며
  완료 근거로 사용하지 않았다.
- Ubuntu VM Backend `clean test`: 64 tests, failures/errors/skipped 모두 0.
- PostgreSQL 17 Testcontainers: Admin 1, Identity 4, OIDC 3, Passwordless 10,
  User Self-service 3 tests 모두 실제 실행.
- Frontend: 총 11 tests와 네 앱 lint/production build 성공.
- Playwright Browser E2E: 4 tests 모두 성공, test container/network/volume 정리 확인.
- OpenAPI 3.1: 5개 contract validator 성공.
- 필수 문서: 15개 문서 validator 성공.
- Production Compose/Caddy 정적 모델: image/port/network/Secret/exact HTTPS URI 정책 성공.
- 최종 repository audit: Secret/PII, Browser token storage, Identity DB ownership 경계 성공.

실제 Gmail, 실제 Turnstile, AWS 과금 리소스 및 Production 재배포는 수행하지 않았다.
