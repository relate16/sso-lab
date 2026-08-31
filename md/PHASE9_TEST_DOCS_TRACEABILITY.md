# Phase 9 — Test / Docs 추적표

작성일: 2026-08-28
기준 문서: `AI_AGENT_MASTER_SPEC_SSO_LAB.md`, `AI_AGENT_START_HERE.md`
기준 커밋: `f3340455bf9f98cffcce0d96c8477c3ee1390683`

## 1. 목적과 범위

이 문서는 Master Specification의 테스트·문서 Definition of Done과 현재 Repository의
자동화 증거를 연결하고, Phase 9에서 보강할 항목과 이전 기능 Phase의 결손을 구분한다.
Phase 9 자동화는 실제 Gmail, 실제 Turnstile, Production Secret 또는 Production
환경을 사용하지 않는다.

상태 표기:

- `충족`: 현재 자동 테스트 또는 문서로 검증됨
- `부분`: 하위 계층 검증은 있으나 전체 시스템/Browser 흐름 검증이 없음
- `Phase 9`: 승인된 Test / Docs 범위에서 보강 가능
- `기능 결손`: 테스트 대상 기능 자체가 없어 이전 기능 Phase 범위의 결정이 필요

## 2. 테스트 DoD 추적

| Master 요구사항 | 현재 증거 | 상태 | Phase 9 조치 |
|---|---|---|---|
| Domain/Service Unit Test | Identity/Crypto/TOTP/Config/RateLimit/Turnstile/LogMasking 단위 테스트 | 충족 | 전체 회귀 및 누락 경계 보강 |
| JPA Repository Integration | Auth/Passwordless/OIDC/Admin PostgreSQL 통합 테스트 | 충족 | Testcontainers 강제 실행 결과 기록 |
| PostgreSQL Testcontainers | PostgreSQL 17 기반 4개 통합 suite | 충족 | Ubuntu Docker에서 실제 실행 |
| Flyway Migration Test | V1~V6 실제 적용 및 schema assertion | 충족 | clean DB 재검증 |
| Email OTP | 가입·로그인·만료·시도·재전송·재사용·enumeration | 충족 | Test Mail Sink 사용 유지 |
| TOTP | RFC 6238, 등록, 검증, replay, 암호화 저장 | 충족 | controllable clock 기반 통합 시나리오 보강 |
| Recovery Code | Argon2id 저장, 1회 사용, 제한 Recovery Session | 충족 | Browser/API 흐름 보강 |
| OIDC | Code+PKCE, exact redirect, code 1회, token, refresh rotation, UserInfo, claims | 충족 | Client 간 시스템 E2E 보강 |
| SSO E2E | Auth 통합 테스트에서 동일 Auth Session으로 HR/Approval code 발급 | 부분 | 실제 BFF/Browser Email OTP 및 TOTP SSO |
| Back-Channel Logout E2E | Auth logout DB revoke와 BFF logout-token 단위 검증 | 부분 | Auth→HR/Approval BFF session 폐기 시스템 E2E |
| Admin Email Re-auth | Email OTP proof, reveal, 5분 만료 | 충족 | Admin Web 주요 흐름 테스트 |
| Account Suspend | 신규 인증/기존 Auth Session 차단 및 revoke | 충족 | BFF 시스템 흐름 회귀 |
| Hard Delete | 구현 endpoint/service 없음 | 기능 결손 | 테스트만으로 해결 불가 |
| Frontend 주요 Flow | build/typecheck만 존재 | Phase 9 | Vitest/Testing Library 기반 4개 Web 테스트 |
| GitHub Actions 성공 | Backend, 4 Frontend, Compose | 충족 | Phase 9 test/E2E job 포함하도록 보강 |

## 3. 핵심 E2E 추적

| Scenario | 현재 상태 | Phase 9 완료 조건 |
|---|---|---|
| Email OTP HR → Approval SSO | Auth 계층 통합 테스트만 존재 | Test Mail Sink에서 OTP 획득, Browser 로그인, 두 BFF Session 및 `amr=email_otp`, `acr=urn:jb:loa:1` 검증 |
| TOTP HR → Approval SSO | TOTP 서비스와 OIDC email OTP가 분리 검증됨 | 고정 test secret과 controllable clock으로 Browser TOTP 로그인 및 두 Client claim 검증 |
| Central / Back-Channel Logout | Auth revoke와 BFF token 검증이 분리됨 | HR/Approval 로그인 후 Auth logout, 두 BFF Session 폐기 및 재인증 요구 검증 |

실제 Gmail 또는 Production OTP를 읽는 자동화는 만들지 않는다. Turnstile은 test profile의
test double 또는 Cloudflare 공식 공개 테스트 값만 사용하며 Production Secret을 참조하지
않는다.

## 4. 기능 DoD 사전 점검

Phase 9 시작 시 정적 분석으로 다음 기능 결손을 확인했다.

| Master 기능 DoD | 현재 Repository | 판정 |
|---|---|---|
| 사용자 Profile 조회 | self-only API/UI와 no-store 응답 구현 | 선행 보완 완료, 격리 검증 성공 |
| username 변경 + Audit | Session 유지 변경 API/UI와 `USERNAME_CHANGED` 구현 | 선행 보완 완료, 격리 검증 성공 |
| 새 Email OTP 검증 변경 + 다른 Session/Refresh Token 폐기 + Audit | encrypted pending model, OTP, revoke orchestration 구현 | 선행 보완 완료, 격리 검증 성공 |
| User Hard Delete + credential/authorization/session 삭제 + back-channel logout + Audit 유지 | fresh re-auth, 삭제 orchestration, bootstrap/last Admin 보호 구현 | 선행 보완 완료, 격리 검증 성공 |

위 항목은 별도 승인된 Phase 9 선행 기능 결손 보완으로 구현했다. V7과 실제 PostgreSQL
Testcontainers, 전체 Backend 회귀, 4개 Frontend build 및 격리 Production-profile Docker
health 검증을 통과했으며 Production migration과 배포는 수행하지 않았다.

## 5. API 문서 DoD

| 요구사항 | 현재 상태 | Phase 9 조치 |
|---|---|---|
| 각 Backend OpenAPI | 미구현 | Auth/Admin/HR/Approval 사용자-facing API 문서 제공 |
| Auth public API와 Internal Admin API 구분 | Runtime route는 구분됨, OpenAPI 없음 | group/spec 분리 및 Production 외부 routing 차단 검증 |
| Internal Admin 문서 외부 비노출 | Caddy `/internal/**` 차단은 존재 | OpenAPI 경로에도 동일 경계 테스트 |

## 6. 필수 문서 DoD

| 파일 | 현재 상태 | Phase 9 조치 |
|---|---|---|
| `README.md` | Phase 1 내용으로 오래됨 | 전체 v1 기준 재작성 |
| `DEVELOPER_SETUP.txt` | Phase 1 내용으로 오래됨 | 짧은 최종 체크리스트로 재작성 |
| `docs/ARCHITECTURE.md` | 없음 | 작성 |
| `docs/LOCAL_SETUP.md` | 없음 | 작성 및 실행 가능한 명령 검증 |
| `docs/DEPLOYMENT.md` | 있음 | Master 필수 항목 감사·보강 |
| `docs/SECURITY.md` | 있음 | Master 필수 항목 감사·보강 |
| `docs/SSO_FLOW.md` | 없음 | Mermaid 흐름 포함 작성 |
| `docs/TEST_GUIDE.md` | 없음 | test doubles, 핵심 E2E, CI 포함 작성 |
| `docs/DOMAIN_CHANGE_GUIDE.md` | 있음 | 필수 체크리스트 감사·보강 |
| `docs/GMAIL_SMTP_SETUP.md` | 있음 | 실제 값 없는지 감사·보강 |
| `docs/TURNSTILE_SETUP.md` | 있음 | 공개 Site Key/서버 Secret 경계 감사·보강 |
| `docs/BOOTSTRAP_ADMIN_GUIDE.md` | 없음 | 작성 |
| `docs/SECRET_MANAGEMENT.md` | 있음 | VM/AWS 공급 방식 감사·보강 |
| `docs/VM_SETUP_GUIDE.md` | 없음 | 작성 및 확인 명령 제공 |
| `docs/AWS_SETUP_GUIDE.md` | 없음 | 비용 리소스 미생성, 절차/IAM/확인법 작성 |

## 7. Phase 9 실행 게이트

1. 이전 기능 Phase 결손의 처리 범위를 사용자와 확정한다.
2. 승인된 범위에서 Backend test 보강을 수행한다.
3. Frontend test와 isolated E2E를 추가한다.
4. OpenAPI와 필수 문서를 완성한다.
5. Local 및 `/opt/sso-lab-test`에서 전체 회귀를 실행한다.
6. Secret/PII/log/Browser storage/서비스 경계를 최종 감사한다.
7. Production에는 자동 반영하지 않고 결과와 영향을 먼저 보고한다.
