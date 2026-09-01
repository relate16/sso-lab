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
