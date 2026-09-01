# Phase 9 — Local and Test Server Changelog

작성일: 2026-08-31

이 문서는 Phase 9 Test / Docs에서 변경된 로컬 파일과 `/opt/sso-lab-test` 검증 흔적을
찾기 위한 기록이다. Production `/opt/sso-lab`, DB, Secret, container에는 접근하거나
변경하지 않았다.

## 로컬 변경

### Backend와 보안 회귀

- Auth Foundation/BFF security config: OIDC saved request와 SPA session probe 격리
- Passwordless session authentication: issued-at을 가진 factor authority 추가
- `testsupport` package: test-only mail 조회와 controllable clock API
- `sso.test-support.enabled`: test profile만으로는 활성화되지 않는 opt-in gate
- OIDC PostgreSQL integration: 익명 SPA probe 뒤 authorization continuation 검증
- TestSupport unit test: header key/no-store/clock/unsafe key 검증

### Frontend와 Browser E2E

- 네 Web의 API adapter와 Node test runner unit test 11개
- Auth Profile/username/email change/Hard Delete UI
- Admin empty response 처리 보강
- `e2e/`, `docker-compose.e2e.yml`, E2E Caddy/test env/runner
- test mail sink, Turnstile test flag, controllable TOTP clock 기반 Playwright 4개

### OpenAPI와 자동 감사

- `docs/openapi/auth-public.json`
- `docs/openapi/auth-internal.json`
- `docs/openapi/admin-server.json`
- `docs/openapi/hr-server.json`
- `docs/openapi/approval-server.json`
- `scripts/test/validate-openapi.py`
- `scripts/test/validate-phase9-docs.py`
- `scripts/test/audit-phase9-repository.py`
- `.github/workflows/ci.yml`의 Frontend test/OpenAPI/docs/audit step

### 문서

- 갱신: `README.md`, `DEVELOPER_SETUP.txt`, Deployment/Security/Secret Management
- 생성: Architecture, Local Setup, SSO Flow, Test Guide, Bootstrap Admin, VM/AWS Guide
- 추적/완료: `md/PHASE9_TEST_DOCS_TRACEABILITY.md`, `docs/PHASE9_TEST_DOCS.md`
- `md/AI_AGENT_START_HERE.md`: Phase 9 완료 상태 반영

기존 Flyway V1~V7은 Phase 9 본 작업에서 수정하지 않았다. V7은 앞선 prerequisite
feature-gap commit에 additive migration으로 포함되어 있다.

## 테스트 서버 변경

승인된 다음 7개 비민감 파일만 로컬에서 `/opt/sso-lab-test` 동일 상대 경로로 전송했다.

1. `TestSupportProperties.java`
2. `AdjustableTestClock.java`
3. `TestSupportController.java`
4. `IdentityTimeConfiguration.java`
5. `application-test.yml`
6. `TestSupportControllerTest.java`
7. `docker-compose.e2e.yml`

전송 후 일곱 파일의 local/remote SHA-256 일치를 확인했다. 앞선 검증 준비에서 test
경로의 `.tooling`에는 VM에 이미 cache된 공식 `gradle:8.14.3-jdk21` image로부터 JDK와
Gradle 실행 파일을 복사했다. 이는 Git 제외 test toolchain이며 Production과 공유하지 않는다.

Gradle/Testcontainers가 생성한 `build/`와 test cache가 test 경로에 남을 수 있다.
Playwright runner는 별도 `sso-lab-phase9-e2e` project를 사용했고 종료 후 container,
network, PostgreSQL volume을 모두 제거했다.

## 실제 검증 결과

- Ubuntu Backend: 64 tests, 0 failure, 0 error, 0 skip
- PostgreSQL Testcontainers: 5 suites/21 tests 실제 실행
- Browser E2E: 4/4 pass
- Production Compose/Caddy static model: pass
- Local Frontend: 11 tests, 네 lint/build pass
- OpenAPI 5개, 필수 문서 15개, repository audit: pass

실제 Gmail 메일, 실제 Turnstile verification, AWS resource, Production migration/deploy,
release image publish, commit/push는 수행하지 않았다.

## 후속 v1.1.0 Production 배포 기록

위 문장은 Phase 9 구현과 격리 검증 시점의 경계를 기록한다. 이후 별도 승인으로 다음
Production 승격을 수행했다.

- release: `v1.1.0`
- source commit: `32604d9bfb8d56b9dad6ef4af02741af7c188900`
- deploy workflow run: `33461384791`
- Production DB: Flyway V1~V7, failed migration 0
- pre-deploy backup: `20260901T014319Z-pre-v1.1.0`
- backup path:
  `/home/today/.local/state/sso-lab/backups/postgres/20260901T014319Z-pre-v1.1.0/`

V1~V6 custom-format dump는 격리 PostgreSQL 17 full restore와 row-count/Flyway 일치까지
검증했다. V7 적용 후에는 pending Email 변경과 새 Audit event/lifecycle을 구버전이 알지
못하므로 `v1.0.0` application-only rollback을 안전하다고 보지 않는다. 필요 시 상태를
보존하고 별도 승인 후 검증된 backup restore + `v1.0.0` 또는 forward-fix를 선택한다.

Production smoke에서는 실제 Gmail/Turnstile, 사용자 생성, Profile 변경, Email 변경 및
Hard Delete를 실행하지 않았다. Phase 9 격리 E2E가 이 기능들의 전체 흐름을 담당하고,
Production은 TLS/OIDC/client 인증/보안 경계와 비파괴 음성 경로를 확인했다. 최초 HR/Admin
token smoke 401은 Secret 불일치가 아니라 RFC 6749 `client_secret_basic` 생성 시
client ID와 Secret의 form-urlencoded 처리가 누락된 진단 오류였다. 올바른 인코딩 후
세 client 모두 `400 invalid_grant`였고 token은 발급되지 않았다.

최종 검증 시 실제 사용자, credential, OTP, OAuth authorization, pending Email 변경 및
Audit data는 0이었다. 상세 운영 기준은 `docs/PRODUCTION_STATUS.md`를 따른다.
