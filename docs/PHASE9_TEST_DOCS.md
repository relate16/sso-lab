# Phase 9 — Test / Docs Completion

검증일: 2026-08-31

Phase 9는 요구사항 추적, Backend/Frontend 테스트, 격리 Browser E2E, OpenAPI,
필수 문서와 최종 보안 감사를 완료했다. Production 환경과 실제 Gmail/Turnstile은
변경하거나 호출하지 않았다.

## 산출물

- Master §72~77, §96~109, §122~127, §134 추적표
- Auth public/internal, Admin, HR, Approval OpenAPI 3.1 contract 5개
- 네 Frontend API unit test 총 11개
- Playwright 격리 Browser E2E 4개
- 필수 문서 15개와 README/Developer Setup
- OpenAPI, 문서, Secret/PII/service-boundary CI validator

## 최종 테스트 결과

| 계층 | 결과 |
|---|---|
| Local Backend | `clean test` 성공; Docker 부재로 TC 21개 skip, 완료 근거에서는 제외 |
| Ubuntu Backend | 64 tests, failure 0, error 0, skip 0 |
| PostgreSQL Testcontainers | 5 suites, 21 tests 모두 실제 실행 |
| Frontend | Auth 3 + Admin 4 + HR 2 + Approval 2 = 11 pass |
| Frontend lint/build | 네 앱 모두 성공 |
| Browser E2E | 4/4 pass |
| OpenAPI | 5/5 pass |
| 필수 문서 | 15/15 pass |
| Production Compose/Caddy model | port/network/Secret/exact URI 정책 pass |
| Repository audit | Secret/PII/Browser storage/DB ownership pass |

PostgreSQL suite는 `AdminPostgresqlIntegrationTest` 1, `AuthServerPostgresqlIntegrationTest`
4, `OidcPostgresqlIntegrationTest` 3, `PasswordlessPostgresqlIntegrationTest` 10,
`UserSelfServicePostgresqlIntegrationTest` 3개다. Flyway V1~V7, 실제 FK/cascade,
Hard Delete 후 개인정보·credential·OAuth·Session 삭제와 `ACCOUNT_DELETED` Audit 보존을
포함한다.

E2E는 Email OTP signup/HR→Approval SSO/logout, controllable-clock TOTP SSO,
Profile/username/email 변경·Session 폐기·Hard Delete, Admin suspend/email re-auth를
검증했다. test mail sink, Turnstile disabled test flag, 임시 random Secret과 별도
PostgreSQL volume/network만 사용했다. 종료 후 E2E container/network/volume은 모두 0이었다.

## 검증 중 해결한 문제

1. SPA의 익명 API probe가 OIDC saved request를 덮어쓰는 문제를 Auth Foundation chain의
   `NullRequestCache`로 격리했다.
2. Spring Security/Spring Authorization Server가 요구하는 인증 factor issued-at를
   Passwordless Authentication에 추가해 `auth_time` 발급을 정상화했다.
3. HR/Approval/Admin BFF session probe가 callback target이 되는 문제를 각 BFF의
   `NullRequestCache`로 차단했다.
4. Admin Web이 성공한 empty HTTP 200을 JSON parse error로 처리하던 문제를 수정했다.
5. E2E test clock/controller가 일반 `test` profile 통합 테스트까지 영향을 주던 문제를
   `sso.test-support.enabled` opt-in으로 격리했다. 일반 test는 system UTC clock을 사용한다.

## CI와 Production 영향

CI workflow에는 Frontend test, OpenAPI, 필수 문서와 repository audit step이 추가됐다.
사용자 지시에 따라 Phase 9 변경은 아직 commit/push하지 않았으므로 이 변경에 대한 새
GitHub Actions run은 없다. 기준 commit의 기존 CI는 성공 상태다.

Production source, DB, Secret, container와 release image는 변경하지 않았다. Profile/email
변경/Hard Delete의 V7과 Phase 9 변경을 운영에 반영하려면 별도 승인 후 새 immutable
release, CI 성공, DB backup과 V7 migration 계획이 필요하다. 기존 `v1.0.0` image에는
이 변경이 포함되지 않으므로 재사용할 수 없다.

AWS는 v1 Future Work이다. 현재 `SecretProvider` 구현은 Environment/Docker이며
`AwsSsmSecretProvider`는 구현되어 있지 않다는 사실을 AWS 가이드에 명시했다.
