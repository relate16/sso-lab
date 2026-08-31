# Phase 9 선행 기능 결손 보완 계획

작성일: 2026-08-28
상태: 구현 및 격리 검증 완료 — Production migration, commit/push 미수행
연관 문서: `PHASE9_TEST_DOCS_TRACEABILITY.md`

## 1. 작업 성격과 공통 경계

이 작업은 Phase 9 추적 분석에서 발견한 Master 기능 DoD 결손을 보완하기 위한
**선행 기능 보완**이다. Phase 1~8 완료 이력을 다시 정의하지 않으며, 보완이 검증된 뒤
Phase 9 Test / Docs 본 작업을 재개한다.

공통 불변 조건:

- Identity와 관련 데이터의 유일한 Owner 및 DB 직접 접근자는 `auth-server`이다.
- `admin-server`, `hr-server`, `approval-server`에는 JPA/JDBC/PostgreSQL 접근을 추가하지 않는다.
- Browser는 OAuth/OIDC Token을 저장하지 않으며 기존 BFF 서버 측 Token 저장을 유지한다.
- 모든 변경 API는 현재 `SCOPE_NORMAL` Auth Session과 CSRF 보호를 요구한다.
- email/OTP/Token/Secret 원문을 log, Audit, exception response에 기록하지 않는다.
- email은 AES-256-GCM ciphertext/IV/key version으로, lookup은 HMAC-SHA256으로만 저장한다.
- Test Mail Sink, test Turnstile, controllable clock만 자동 테스트에서 사용한다.
- Production 설정, Secret, DB 및 container는 이 보완 구현/검증 단계에서 변경하지 않는다.

예상 migration은 `V7__add_identity_self_service.sql` 한 개로 묶되, 실제 이름과 SQL은
구현 승인 후 기존 migration naming 규칙에 맞춰 확정한다. 기존 V1~V6는 수정하지 않는다.

## 2. 공통 Audit 구조 보완

### Master 요구사항

Master §42는 `USERNAME_CHANGED`, `EMAIL_CHANGED`, `ACCOUNT_DELETED`를 필수 Audit event로
정의하고 Audit의 무기한 보존을 요구한다. OTP, TOTP secret, Recovery Code, key, Token,
전체 email 원문은 Audit에 남길 수 없다. §38~39는 Hard Delete 뒤에도 actor/target의 내부
UUID만 보존하고 User FK 또는 별도 역조회 table을 두지 않도록 한다.

### 현재 결손

현재 `audit_logs`에는 User FK가 없어 삭제 후 보존 조건은 충족하지만 event check
constraint와 Java enum이 Admin event만 허용하고, source도 `ADMIN_WEB`, `INTERNAL_API`,
`BOOTSTRAP`만 허용한다. 클래스 이름 또한 `AdminAudit*`로 한정되어 있다.

### 계획

- `AdminAuditEvent` → `AuditEvent`, `AdminAuditSource` → `AuditSource`,
  `AdminAuditService` → `AuditService`, `AdminAuditEntity/Repository` → 일반 Audit 명칭으로
  이동한다.
- 기존 Admin event 값은 그대로 유지하고 세 event를 추가한다.
- `AUTH_WEB` source를 추가한다.
- 기존 Admin API response contract의 event/source 문자열은 바꾸지 않는다.
- self-service 성공 Audit에는 `actor_id=target_id=현재 내부 UUID`, `success=true`,
  `source=AUTH_WEB`, 서버에서 만든 trace id와 UTC 시각만 기록한다.
- username/email 원문과 변경 전/후 값은 Audit에 기록하지 않는다.
- Hard Delete 성공 Audit은 User 삭제와 같은 transaction에 기록한다. Audit에는 User FK가
  없으므로 User row 삭제 뒤에도 남고, 삭제와 Audit 중 하나만 commit되는 상태를 방지한다.

예상 변경 파일:

- `backend/auth-server/src/main/java/com/ssolab/auth/admin/audit/*`
- 해당 Audit 타입을 참조하는 Bootstrap/Admin service 및 DTO mapping
- `backend/auth-server/src/main/resources/db/migration/V7__add_identity_self_service.sql`
- 기존 Admin 통합 테스트와 신규 self-service 통합 테스트

DB 영향은 check constraint 허용값과 source 허용값 확장뿐이며 기존 Audit row를 수정하지
않는다.

## 3. 사용자 Profile 조회

### Master의 정확한 요구사항/DoD

Master §25, §6.1, §122:

- 일반 사용자는 자기 정보만 조회할 수 있다.
- 타 사용자 목록/검색을 제공하지 않는다.
- userId, username, 복호화된 본인 email, Role, Group, TOTP 등록 여부, 현재 로그인
  Session을 표시한다.
- Group이 없으면 UI에 `할당된 그룹 없음`을 표시한다.

### 현재 결손

- `/api/v1/me/sessions`만 존재하고 자기 Identity Profile endpoint가 없다.
- `auth-web`은 로그인 및 Session UI만 제공하며 Profile/Group/TOTP 상태를 표시하지 않는다.
- Repository/entity에는 필요한 데이터가 있으나 self-only DTO/service가 없다.

### Backend 계획

제안 endpoint:

```text
GET /api/v1/me/profile
```

- Path/query에 user UUID를 받지 않고 `PasswordlessPrincipal.userId`만 사용한다.
- `SCOPE_NORMAL` 및 ACTIVE account를 요구한다.
- 응답 DTO는 `userId`, `username`, `email`, 정렬된 `roles`, 정렬된 group full path,
  `totpEnrolled`, 현재 session 요약을 포함한다.
- ciphertext/IV/key version/email lookup hash, TOTP credential, Recovery Code, 내부 Entity를
  노출하지 않는다.
- 개인정보 응답은 `Cache-Control: no-store` 대상으로 검증한다.
- Session 원문 ID 대신 기존 public session ID/SessionView만 재사용한다.

예상 파일:

- 신규 `identity/api/UserProfileController.java`
- 신규 `identity/api/UserProfileResponse.java`
- 신규 `identity/service/UserSelfService.java`
- `TotpCredentialRepository.java`의 존재 여부 query
- 기존 `SessionManagementService.java`의 owner-scoped 조회 재사용 또는 읽기 전용 facade

Flyway 변경은 없다. 조회 자체는 Audit 대상이라고 Master가 명시하지 않았으므로 Audit을
추가하지 않는다.

### Frontend 계획

- `frontend/auth-web/src/App.tsx`를 Profile/인증/Session 영역으로 분리한다.
- 신규 `src/profile/ProfilePanel.tsx`와 API type/client를 추가한다.
- Group이 비었을 때 정확히 `할당된 그룹 없음`을 표시한다.
- 타 사용자 검색/ID 입력 UI는 만들지 않는다.

### 인증, Session, OIDC 영향

- 현재 정상 Auth Session만으로 조회하며 추가 재인증은 요구하지 않는다.
- Session/Refresh Token을 변경하지 않는다.
- OIDC claim 생성 로직도 변경하지 않는다.

### 테스트

- Unit: group path/정렬, TOTP status 및 민감 필드 미노출 DTO mapping.
- MockMvc security: 미인증/Recovery-only 차단, 다른 userId를 지정할 surface가 없음, CSRF가
  read-only GET에는 불필요함을 확인.
- PostgreSQL Testcontainers: 암호화 저장 상태에서 본인 email만 복호화되고 Role/계층 Group/
  TOTP/current session이 정확한지 검증.
- Frontend: Group 있음/없음, Profile loading/error, ciphertext/Token/Session 원문 미표시.
- E2E: 로그인 후 Profile과 현재 Session 표시.

회귀 위험은 lazy relation 조회, group hierarchy 순회, session public ID mapping 및 Profile에
email 원문을 표시하는 Browser privacy이다. `no-store`, self-only principal binding,
Entity 직접 직렬화 금지로 완화한다.

## 4. username 변경 및 Audit

### Master의 정확한 요구사항/DoD

Master §26, §42, §122:

- 본인 username을 변경할 수 있다.
- 인증 Session은 유지한다.
- `USERNAME_CHANGED` Audit을 기록한다.
- userId는 변경 대상이 아니다.

### 현재 결손

- `UserIdentityEntity`에 username 변경 method가 없다.
- self-service 변경 API/service/UI/Audit event가 없다.

### Backend 계획

제안 endpoint:

```text
PATCH /api/v1/me/profile/username
Content-Type: application/json
{"username":"..."}
```

- `PasswordlessPrincipal.userId`로만 대상을 결정한다.
- 기존 `IdentityInputNormalizer.normalizeUsername`과 길이/blank validation을 재사용한다.
- Entity method가 username과 `updated_at`만 atomic update한다.
- 성공 후 `USERNAME_CHANGED` Audit을 남긴다. 변경 전/후 username은 Audit/log에 넣지 않는다.
- 응답은 갱신된 Profile DTO 또는 username만 반환하며 Entity는 반환하지 않는다.

예상 파일:

- `UserIdentityEntity.java`
- `UserSelfService.java`
- `UserProfileController.java`
- 신규 validation request DTO
- 공통 Audit 타입과 V7 Audit constraint
- `auth-web` Profile form/component

별도 username column/schema 변경은 없다. Audit constraint 확장은 공통 V7에 포함한다.

### 인증, Session, OIDC 영향

- 정상 Auth Session + CSRF를 요구한다.
- Master가 명시적으로 Session 유지를 요구하므로 추가 재인증, Session revoke,
  Refresh Token revoke를 하지 않는다.
- 새로 발급되는 ID Token/UserInfo는 DB에서 최신 username을 읽으므로 새 값이 반영된다.
- 이미 발급된 ID/Access Token과 기존 BFF Session claim이 즉시 갱신되어야 하는지는 Master에
  명시되지 않았다. Session 유지를 우선하면 기존 BFF 표시값은 다음 OIDC 인증까지 낡을 수
  있다. 이 즉시 동기화 요구는 **결정 필요**이다.

### 테스트

- Unit: normalize/validation, entity timestamp, Audit payload에 username 미포함.
- PostgreSQL Testcontainers: 본인만 변경, userId/email/roles/groups 불변, Audit 1건,
  Auth Session 유지.
- OIDC integration: 변경 후 새 authorization의 `username`/`name` claim 반영.
- Frontend: CSRF header, 성공 Profile refresh, 실패 시 기존 표시 유지.
- E2E: username 변경 후 Auth 페이지 로그인 유지 및 새 OIDC 로그인 claim 확인.

주요 회귀 위험은 기존 BFF의 claim stale 상태와 Entity lazy loading이다. Token TTL이나
Session 정책을 임의로 바꾸지 않는다.

## 5. 새 Email OTP 기반 Email 변경

### Master의 정확한 요구사항/DoD

Master §16, §27~28, §42, §90~91, §122:

1. 새 email 입력
2. 중복 검사
3. 새 email로 OTP 발송
4. OTP 검증
5. `email_ciphertext`, `email_lookup_hash` 및 관련 crypto metadata 갱신
6. 현재 Auth Session을 제외한 다른 Session 폐기
7. Refresh Token 폐기
8. `EMAIL_CHANGED` Audit 기록

OTP는 기본 5분, 최대 실패 5회, 재발송 60초, 성공 즉시 소비하며 typed system config
override를 사용한다. 원문 OTP/email을 DB의 평문 column이나 log/Audit에 저장하지 않는다.
unique race는 DB constraint가 최종 차단한다.

### 현재 결손

- `EMAIL_CHANGE` enum/DB purpose는 예약되어 있으나 challenge factory, mail purpose,
  발급/재발송/검증 service가 없다.
- 새 email의 encrypted pending state를 저장할 table/entity가 없다.
- User email crypto fields를 atomic 교체하는 domain method가 없다.
- 현재 Session 제외 logout/revoke orchestration이 없다.
- Auth Web email 변경 UI가 없다.

### 제안 API

```text
POST /api/v1/me/email-change/start
POST /api/v1/me/email-change/{challengeId}/resend
POST /api/v1/me/email-change/{challengeId}/verify
```

- start body는 `newEmail`만 받으며 정상 Auth Session + CSRF를 요구한다.
- verify body는 OTP code만 받고 challenge owner와 현재 principal UUID를 함께 검증한다.
- 응답은 email 존재/중복 여부를 과도하게 상세히 노출하지 않는 일반화된 오류를 사용한다.
- OTP code/new email을 request `toString`, exception, access log, Audit에 남기지 않는다.

### DB/Flyway 계획

V7에 `pending_email_changes`를 추가하는 방식을 권장한다.

```text
id UUID PK
user_id UUID FK users(id) ON DELETE CASCADE
email_ciphertext BYTEA
email_iv BYTEA(12)
email_key_version INTEGER
email_lookup_hash BYTEA(32)
created_at / expires_at / consumed_at
```

또한 `email_otp_challenges.pending_email_change_id` nullable FK를 추가하고 purpose context
constraint를 다음처럼 강화한다.

- SIGNUP: pending registration만 존재
- LOGIN/ADMIN_REAUTH: user만 존재
- EMAIL_CHANGE: user와 pending email change가 존재하고 두 owner가 일치

대상 email 평문 column은 만들지 않는다. 최종 commit 시 기존 `users_email_lookup_hash_uk`가
동시 변경 race를 차단한다. 한 사용자에게 동시에 허용할 active pending change 수와 새
요청 시 이전 challenge를 무효화할 방식은 Master에 명시되지 않아 **결정 필요**이다.
권장안은 새 start가 기존 미소비 EMAIL_CHANGE challenge를 소비/폐기하고 최신 1개만
유효하게 하는 것이다.

예상 파일:

- 신규 `passwordless/emailchange/PendingEmailChangeEntity.java`
- 신규 `PendingEmailChangeRepository.java`
- 신규 `EmailChangeService.java`
- `EmailOtpChallengeEntity.java`, `EmailOtpTransactionService.java`
- `OtpMailPurpose.java`, mail subject/template mapping
- `UserIdentityEntity.java`, `UserIdentityRepository.java`, `UserSelfService.java`
- request/response DTO와 `UserProfileController.java`
- `LogoutStateService.java`, `LogoutCoordinator.java`
- `RateLimitAction.java` 또는 기존 email OTP action의 명시적 재사용
- 공통 Audit 타입, V7 migration, `auth-web` email 변경 UI

### Audit

- 성공한 최종 교체만 `EMAIL_CHANGED`로 기록한다.
- actor/target은 현재 내부 UUID, source는 `AUTH_WEB`이다.
- old/new email, lookup hash, challenge ID, OTP는 Audit에 기록하지 않는다.
- 실패 원인을 사용자 식별 가능하게 Audit/log에 남기는 확장은 하지 않는다.

### 인증/재인증 및 abuse 방어

- Master 흐름이 새 email 소유권 OTP를 요구하므로 정상 Auth Session + CSRF + 새 email OTP를
  필수로 한다.
- 기존 email/TOTP를 이용한 별도 fresh re-authentication은 Master에 필수로 명시되지 않아
  추가 여부가 **결정 필요**이다.
- OTP TTL/attempt/resend와 DB row lock을 기존 정책대로 재사용한다.
- IP + 현재 user UUID/email lookup hash + challenge 기준 rate limit을 적용한다.
- Production Turnstile 필수 대상 목록에는 email 변경이 명시되지 않았으므로 Turnstile을
  추가하지 않는다. 추가하려면 별도 정책 결정이 필요하다.

### Session/Refresh Token/OIDC 처리

- 현재 Auth Server Spring Session과 metadata는 유지한다.
- 다른 Auth Session metadata/Spring Session은 invalidate/delete한다.
- 해당 사용자의 `oauth2_authorization`을 삭제해 모든 Refresh Token과 저장된 authorization을
  폐기한다.
- 기존 BFF Session에는 old email claim이 남을 수 있다. 모든 연결 Client에 back-channel
  logout을 전송하고 현재 Auth Session으로 재인가하게 할지, 다른 Auth Session에 연결된
  Client만 종료할지는 Master 문구만으로 확정되지 않아 **결정 필요**이다.
- 보안상 권장안은 현재 Auth Session만 유지하고, 모든 OAuth authorization 및 HR/Approval/
  Admin BFF Session은 종료하는 것이다. 재인가 시 새 email claim이 발급된다.
- 이미 발급된 stateless JWT는 서명상 TTL(현재 5분)까지 유효할 수 있다. Server-side BFF
  Session과 authorization은 즉시 폐기하되 JWT deny-list를 새로 추가하지 않는다.

### 테스트

- Unit: email normalize/encrypt/hash, pending entity lifecycle, owner mismatch, fixed clock expiry,
  OTP zeroization/redaction.
- PostgreSQL Testcontainers: 새 email 평문 column 없음, duplicate와 race 최종 차단,
  expiry/attempt/resend/reuse, old hash 조회 불가/new hash 조회 가능, Audit 무민감정보.
- Session/OIDC integration: current Auth Session 유지, 다른 Session 종료, 모든 Refresh Token
  재사용 거부, 선택한 정책에 맞는 back-channel logout, 새 token/UserInfo email 반영.
- Frontend: CSRF, Test Mail Sink OTP, 성공 후 Profile 갱신, code/new email storage 미사용.
- E2E: 두 Auth Session과 HR/Approval BFF Session을 만든 뒤 email 변경 및 폐기 범위 검증.

### Production migration 영향

- nullable FK column, 새 table/index, check constraint 교체가 필요한 additive migration이다.
- 기존 user email ciphertext를 재암호화하거나 rewrite하지 않는다.
- constraint validation 중 `email_otp_challenges`에 짧은 schema lock/scan이 발생할 수 있다.
- 배포 전 DB backup과 Testcontainers/운영 복제 데이터의 Flyway dry run이 필요하다.

## 6. User Hard Delete

### Master의 정확한 요구사항/DoD

Master §38~39, §42, §90, §117, §122/124:

- 회원탈퇴는 irreversible Hard Delete이다.
- 최소 현재 로그인 Session과 CSRF 검증이 필요하다.
- UI에 명확한 확인 단계를 둔다.
- user row, role/group membership, TOTP, Recovery Code, OTP challenge, OIDC Authorization,
  Refresh Token, SSO Session을 삭제한다.
- 관련 Client Session에 back-channel logout 요청을 보낸다.
- 현재 Session 종료 및 auth-web logout을 수행한다.
- `ACCOUNT_DELETED` Audit은 무기한 보존하며 actor/target에는 내부 UUID만 둔다.
- 삭제 UUID와 실제 사용자를 다시 연결하는 lookup table을 만들지 않는다.

### 현재 결손

- 자기 account 삭제 API/service/UI가 없다.
- DB cascade는 일부 credential을 삭제하지만 Spring Session과 OAuth table에는 User FK가 없다.
- back-channel command를 보존한 채 User를 삭제하는 orchestration이 없다.
- 가입 시 생성된 `pending_registrations`는 User FK가 없어 삭제 후에도 같은 userId/email의
  ciphertext/hash를 보유할 수 있다.
- `bootstrap_admin_state`는 claimed UUID와 bootstrap email hash를 보유한다.

### 제안 API와 확인

```text
DELETE /api/v1/me/account
```

- 정상 `SCOPE_NORMAL` Auth Session + CSRF를 최소 요건으로 한다.
- Frontend는 irreversible임을 표시하고 명시적 확인 입력/checkbox 후에만 호출한다.
- 확인 문자열 또는 request body 형태는 Master에 정해져 있지 않아 **결정 필요**이다.
- Master는 추가 재인증을 선택사항으로 두므로 Email OTP/TOTP fresh re-auth를 강제할지는
  **결정 필요**이다. 구현 전 사용자가 선택해야 한다.
- 삭제 후 204 응답과 Session cookie 만료를 사용하고 auth-web 비인증 화면으로 이동한다.

### 삭제 transaction/orchestration 계획

DB transaction 내부에서 다음 순서를 사용한다.

1. User row를 pessimistic lock하고 현재 principal owner 및 ACTIVE 상태를 확인한다.
2. 내부 UUID, 현재 email lookup hash, 모든 Auth session ID, 연결된 OIDC client session의
   signed logout command 정보를 메모리에 수집한다. email 원문은 수집하지 않는다.
3. `oauth2_authorization`과 `oauth2_authorization_consent`를 `principal_name=내부 UUID`로
   명시 삭제한다. 이 row 안의 authorization code/access/id/refresh token도 함께 제거된다.
4. 가입 당시 남은 `pending_registrations` 중 동일 normalized userId 또는 email lookup hash를
   삭제한다. 이는 Hard Delete 뒤 email ciphertext가 남지 않기 위한 보완이며 Master 삭제
   목록에 table명이 명시되지는 않아 **결정 필요**이다. 권장안은 삭제이다.
5. `ACCOUNT_DELETED` Audit을 같은 transaction에 기록하고, OIDC client session과 Auth
   session metadata를 명시 삭제한 뒤 User row를 삭제한다. 나머지 User FK 데이터는
   cascade로 삭제한다.
6. transaction commit 후 수집한 ID로 `SPRING_SESSION` row를 삭제하고 현재 HttpSession/
   SecurityContext/cookie를 무효화한다.
7. 수집한 signed back-channel logout을 HR/Approval/Admin BFF로 전송한다.

삭제 대상:

| 데이터 | 방식 |
|---|---|
| `users` | 명시 삭제 |
| `user_roles`, `user_groups` | FK cascade |
| `totp_credentials`, `recovery_codes` | FK cascade |
| `email_otp_challenges` | User FK cascade |
| `pending_email_changes` | User FK cascade |
| 관련 `pending_registrations` | userId/email hash 기준 명시 삭제 — 결정 필요 |
| `user_session_metadata`, `oidc_client_sessions` | FK cascade, command는 사전 수집 |
| `SPRING_SESSION`, attributes | session ID로 명시 삭제, attributes는 cascade |
| `oauth2_authorization`, refresh/access/id/code 값 | principal UUID로 명시 삭제 |
| `oauth2_authorization_consent` | principal UUID로 명시 삭제 |
| `admin_reauth_proofs` | FK cascade |

반드시 보존:

- `audit_logs` 전체와 새 `ACCOUNT_DELETED` row
- `roles`, `groups`, `system_config`, Registered Client 및 다른 사용자의 모든 데이터
- Audit에는 내부 UUID/시간/event/source/success만 남고 userId/email은 남지 않는다.

### Bootstrap/마지막 Admin 결정

- 마지막 ACTIVE ADMIN의 Role 제거 금지와 Bootstrap guide의 마지막 Admin 보호를 유지하려면
  마지막 ACTIVE ADMIN의 자기 Hard Delete도 차단하는 것이 권장된다.
- Master는 Hard Delete 문단에서 이를 직접 명시하지 않으므로 **결정 필요**이다.
- `bootstrap_admin_state`의 `claimed_by` UUID와 `email_lookup_hash`를 삭제 후 어떻게
  비연결 상태로 만들면서 재-bootstrap도 막을지는 Master에 세부 규칙이 없다.
  **결정 필요**이며 권장안은 claimed 상태/시각은 유지하되 email hash를 비가역 random
  tombstone으로 교체하고 삭제된 User를 복구·역조회할 수 없게 하는 것이다. 실제 구현
  전 이 정책을 확정해야 한다.

### back-channel 실패 결정

삭제 transaction을 외부 BFF network call과 묶지 않는다. 기존 dispatcher를 재사용하면
local credential 삭제는 확정되고 BFF 호출 실패는 log로 남는다. Durable retry/outbox는
Master에 명시되지 않았으므로 추가 여부가 **결정 필요**이다. v1 권장안은 기존 signed
logout token 검증과 bounded timeout을 유지하며, BFF Session 자체 만료를 최종 경계로 둔다.

### 예상 파일

- 신규 `identity/service/UserAccountDeletionService.java`
- 신규 transactional state worker/result DTO
- `UserProfileController.java` 및 confirmation request DTO
- `UserIdentityRepository.java`
- `LogoutStateService.java`, `LogoutCoordinator.java`, `BackChannelLogoutDispatcher.java`
- `PasswordlessSessionAuthenticationService.java`
- `BootstrapAdminStateEntity/Repository/Service.java`
- 공통 Audit 타입 및 V7 migration
- `frontend/auth-web` 탈퇴 confirmation UI

### 테스트

- Unit: deletion plan이 Secret/PII를 담지 않음, 마지막 Admin guard, confirmation validation,
  back-channel command 생성.
- PostgreSQL Testcontainers: 모든 연관 table에 fixture를 만든 뒤 삭제 대상 0건, 다른 User
  불변, Audit row 잔존, Audit FK 없음, pending ciphertext 제거를 검증.
- OAuth integration: authorization code/access/id/refresh 저장 row 제거, refresh 재사용 거부,
  UserInfo/새 authorization 실패, Spring Session 제거.
- BFF integration/E2E: HR/Approval/Admin Session이 back-channel로 종료되고 재접속 시 인증을
  요구함을 검증.
- Frontend: 명시적 확인 전 버튼 비활성, CSRF 포함, 성공 후 Profile/Session/Token 표시 없음.
- Security: 타 User 삭제 endpoint가 구조적으로 없음, UUID tampering 불가, 민감정보 log 없음.

### Production DB migration 영향

- Hard Delete 자체의 FK는 기존 V2~V6 cascade를 이용하며 새 삭제 column은 없다.
- V7 Audit constraint와 pending email change schema가 먼저 적용되어야 한다.
- 새 Audit enum 값이 실제 저장된 뒤 이전 `v1.0.0` Auth image로 application만 rollback하면
  구버전 enum이 새 row를 읽지 못할 수 있다. 따라서 migration 적용 후 rollback은
  **DB backup 복원 또는 새 event를 이해하는 forward-compatible rollback image**가 필요하다.
- 실제 Production migration은 별도 승인, backup 확인, maintenance 영향 보고 후에만 실행한다.

## 7. 결정이 필요한 보안/동작 정책

구현 전에 다음 결정을 받아야 한다.

1. username 변경 후 기존 HR/Approval/Admin BFF claim의 즉시 갱신 필요 여부.
2. Email 변경에 새 email OTP 외 기존 인증수단의 fresh re-auth를 추가할지.
3. Email 변경 시 모든 BFF Session을 종료할지, 다른 Auth Session에 연결된 BFF만 종료할지.
4. 한 사용자에게 허용할 동시 pending email change 수와 새 start의 이전 challenge 처리.
5. Hard Delete에 fresh Email OTP/TOTP 재인증을 필수화할지.
6. Hard Delete UI/API의 명시적 확인 형식.
7. 마지막 ACTIVE ADMIN의 자기 Hard Delete를 차단할지.
8. 삭제된 Bootstrap Admin의 `bootstrap_admin_state` hash/claimed UUID 비연결 처리 방식.
9. 동일 userId/email hash의 과거 `pending_registrations`도 Hard Delete 범위에 포함할지.
10. Back-channel 전달 실패에 durable retry/outbox를 추가할지, 기존 bounded best-effort를
    유지할지.

Master에 없는 정책은 승인 없이 구현하지 않는다.

## 8. 구현 승인 후 순서

1. 결정 필요 항목 확정 및 API contract 고정
2. V7 migration과 generic Audit model 보강
3. Profile 조회와 username 변경 Backend/Frontend/테스트
4. Email change pending model, OTP, revoke orchestration, UI/테스트
5. Hard Delete state transaction, back-channel, UI/테스트
6. 전체 Backend unit/integration/Testcontainers 회귀
7. 4개 Frontend unit/build 회귀
8. `/opt/sso-lab-test` isolated Compose/E2E 검증
9. Secret/PII/log/Browser storage/Identity DB 경계 감사
10. 결과와 Production migration 영향 보고 후 별도 배포 승인 대기

이 계획 단계에서는 Production 변경, Production DB migration, 실제 Gmail/Turnstile 호출,
commit/push를 수행하지 않는다.
