# Phase 5 — Admin 로컬 및 Ubuntu VM 변경 이력

작성일: 2026-08-21 (Asia/Seoul)

이 문서는 Phase 5 구현 중 `C:\dev\sso-lab`과 Ubuntu VM 테스트 전용 경로 `/opt/sso-lab-test`에서 변경하거나 검증한 내용을 추적하기 위한 기록이다. 실제 배포 경로 `/opt/sso-lab`, 운영 `.env`, 운영 `secrets/`, UFW, DDNS 및 네트워크 설정은 변경하지 않았다.

## 1. Phase 4 변경 이력 문서

Phase 5 시작 전 요청에 따라 기존 Phase 4 로컬·서버 변경 내용을 다음 파일로 보존하고 테스트 VM에도 동기화했다.

```text
md/PHASE4_LOCAL_AND_SERVER_CHANGELOG.md
```

## 2. 로컬 애플리케이션 변경

### Auth Server — Audit

추가 디렉터리:

```text
backend/auth-server/src/main/java/com/ssolab/auth/admin/audit/
```

주요 파일과 내용:

- `AdminAuditEntity`, `AdminAuditRepository`, `AdminAuditService`: 관리자 이벤트 영구 감사 저장과 최근 내역 조회
- `AdminAuditEvent`: Bootstrap claim, suspend/resume, Role/Group 변경, 재인증 성공/실패, Email reveal 이벤트
- `AdminAuditSource`: Admin Web, Internal API, Bootstrap 출처 구분
- Actor/Target은 UUID로만 기록하고 Identity FK를 만들지 않음
- OTP, Token, Secret, 복호화된 Email을 Audit 세부정보로 저장하지 않음

### Auth Server — Bootstrap Admin

추가 디렉터리:

```text
backend/auth-server/src/main/java/com/ssolab/auth/admin/bootstrap/
```

주요 내용:

- `BOOTSTRAP_ADMIN_ENABLED`, `BOOTSTRAP_ADMIN_EMAIL` typed binding
- 시작 시 지정 Email의 HMAC lookup 값만 일회성 상태로 저장
- 정상 Signup Email OTP 검증을 완료한 정확한 사용자에게 한 번만 `ADMIN` 부여
- claim 이후 다른 사용자나 다른 설정 Email로 재사용/교체 불가
- 소스에 기본 관리자 계정, 고정 Email, 고정 OTP를 추가하지 않음

변경 파일:

```text
backend/auth-server/src/main/java/com/ssolab/auth/passwordless/otp/EmailOtpTransactionService.java
```

Signup 검증 완료 시 Bootstrap claim 가능 여부를 확인하도록 연결했다. Passwordless 검증 흐름 자체는 우회하지 않는다.

### Auth Server — Internal Admin API

추가 디렉터리:

```text
backend/auth-server/src/main/java/com/ssolab/auth/admin/internal/
```

구현 경로:

```text
/internal/admin/v1/**
```

주요 내용:

- `admin-server` 전용 Client ID allow-list
- 독립 service credential을 `SecretProvider`로 조회하고 상수 시간 비교
- Actor UUID를 service credential과 분리해 전달하고 매 요청마다 현재 ACTIVE ADMIN인지 재검증
- Internal API에만 적용되는 별도 stateless SecurityFilterChain
- 브라우저 API의 CSRF는 유지하고 machine-to-machine Internal API 경로만 좁게 제외
- User 조회/상세, suspend/resume, Role/Group 교체, Group CRUD/move, Audit, 관리자 재인증, Email reveal 제공

### Auth Server — Admin Service

추가 디렉터리:

```text
backend/auth-server/src/main/java/com/ssolab/auth/admin/service/
```

주요 내용:

- Email은 기본적으로 `m***@example.com` 형식으로 masking
- User/Role/Group 관리와 기존 Identity Service/Repository 재사용
- 모든 User의 필수 `USER` Role 유지
- 마지막 ACTIVE ADMIN의 suspend 및 ADMIN Role 제거 거부
- suspend/Role 변경 시 Passwordless session metadata, Spring Session, OAuth authorization/refresh credential 폐기
- Group 변경은 Master 정책에 따라 강제 global session revoke 대상에서 제외
- 복호화 Email은 유효한 관리자 재인증 proof가 있을 때만 반환

Identity model에 추가된 제한적 변경:

```text
backend/auth-server/src/main/java/com/ssolab/auth/identity/model/UserIdentityEntity.java
backend/auth-server/src/main/java/com/ssolab/auth/identity/model/IdentityGroupEntity.java
backend/auth-server/src/main/java/com/ssolab/auth/identity/repository/UserIdentityRepository.java
backend/auth-server/src/main/java/com/ssolab/auth/identity/service/GroupHierarchyService.java
```

- User suspend/activate 상태 전이
- Group rename
- 관리 변경 충돌 방지를 위한 User row lock 조회

### 관리자 Email Re-authentication

추가 디렉터리:

```text
backend/auth-server/src/main/java/com/ssolab/auth/admin/reauth/
```

주요 내용:

- Email OTP 또는 등록된 TOTP로 재인증
- `ADMIN_REAUTH_TTL` typed system config 사용, 기본 5분
- 256-bit random opaque proof 발급
- DB에는 proof 평문 대신 SHA-256 digest만 저장
- proof는 Actor와 TTL에 binding되며 ACTIVE ADMIN 상태를 다시 검사
- Auth Server가 반환한 proof는 Admin Server의 HttpSession에만 저장하고 Browser에 반환하지 않음

Passwordless 연동 변경 파일:

```text
backend/auth-server/src/main/java/com/ssolab/auth/passwordless/mail/OtpMailPurpose.java
backend/auth-server/src/main/java/com/ssolab/auth/passwordless/otp/EmailOtpChallengeEntity.java
backend/auth-server/src/main/java/com/ssolab/auth/passwordless/otp/EmailOtpChallengeRepository.java
backend/auth-server/src/main/java/com/ssolab/auth/passwordless/otp/EmailOtpTransactionService.java
backend/auth-server/src/main/java/com/ssolab/auth/passwordless/otp/PasswordlessEmailOtpService.java
backend/auth-server/src/main/java/com/ssolab/auth/passwordless/totp/TotpService.java
```

OTP/TOTP 후보 값은 `char[]` 사용 후 덮어쓰며 DTO의 `toString()`은 값을 redaction한다.

### Auth Server Security/Configuration

변경 파일:

```text
backend/auth-server/src/main/java/com/ssolab/auth/config/FoundationSecurityConfig.java
backend/auth-server/src/main/resources/application.yml
```

- Authorization Server, Internal Admin API, 일반 Foundation 체인의 명시적 우선순위 유지
- Bootstrap 및 Internal Admin service credential 설정 추가

### Flyway V5

추가 파일:

```text
backend/auth-server/src/main/resources/db/migration/V5__create_admin_management.sql
```

추가 테이블:

```text
auth.bootstrap_admin_state
auth.admin_reauth_proofs
auth.audit_logs
```

보안 제약:

- Bootstrap Email은 lookup HMAC만 저장
- Re-auth proof는 32-byte digest만 저장
- Audit Actor/Target에는 Identity FK를 두지 않음
- 허용된 event/source/method와 TTL 순서에 DB CHECK 적용

### Admin Server — OIDC BFF 및 Internal API Proxy

추가 파일/디렉터리:

```text
backend/admin-server/src/main/java/com/ssolab/admin/api/
backend/admin-server/src/main/java/com/ssolab/admin/internal/
backend/admin-server/src/main/java/com/ssolab/admin/session/ElevatedAdminSessionService.java
```

주요 내용:

- 기존 Authorization Code + PKCE OIDC BFF 유지
- 보호된 `/api/v1/admin/**`는 서버가 검증한 `ROLE_ADMIN`과 `acr=urn:jb:loa:1`을 모두 요구
- Client/React가 보낸 Role 값을 인가 근거로 사용하지 않음
- 모든 mutation은 기존 CSRF 보호를 통과해야 함
- Internal API credential 및 Actor context header를 Auth Server에 전달
- Auth Server 재인증 proof는 서버측 HttpSession에만 저장
- Email reveal 응답 외에는 복호화 Email을 Browser에 노출하지 않음
- `RestClient.Builder` 자동 Bean에 의존하지 않고 명시적으로 `RestClient.builder()`를 사용해 production image 기동 보장

변경 파일:

```text
backend/admin-server/src/main/java/com/ssolab/admin/config/OidcBffSecurityConfig.java
backend/admin-server/src/main/resources/application.yml
```

`admin-server`에는 JPA, JDBC, PostgreSQL dependency, Identity Entity 또는 Repository를 추가하지 않았다.

### Admin Web

변경 파일:

```text
frontend/admin-web/src/App.tsx
frontend/admin-web/src/styles.css
```

구현 UI:

- Admin dashboard 및 세션 상태
- User 목록/상세와 masked Email
- User suspend/resume
- ADMIN Role 부여/제거
- User Group membership 편집
- 계층 Group 목록, 생성, rename, move, delete
- Audit 조회
- Email OTP/TOTP 관리자 재인증
- 재인증 상태와 Email reveal
- mutation 요청의 CSRF header 처리

Token을 `localStorage`, `sessionStorage`, `indexedDB`에 저장하는 코드는 없다.

### 환경/Compose placeholder

변경 파일:

```text
.env.example
docker-compose.yml
```

추가 placeholder:

```text
ADMIN_INTERNAL_CLIENT_ID
ADMIN_INTERNAL_SECRET_PROVIDER
ADMIN_INTERNAL_SECRET_REF
ADMIN_INTERNAL_API_SECRET
BOOTSTRAP_ADMIN_ENABLED
BOOTSTRAP_ADMIN_EMAIL
```

`auth-server`와 `admin-server`에 동일한 독립 Internal API credential을 주입하고, 두 서비스만 `internal-network`에서 통신한다. 실제 운영 Secret 값은 로컬 파일이나 Git에 추가하지 않았다.

## 3. 테스트 변경

추가 파일:

```text
backend/auth-server/src/test/java/com/ssolab/auth/AdminPostgresqlIntegrationTest.java
```

검증 범위:

- Flyway V5 실제 적용
- Bootstrap Admin의 정상 OTP claim과 일회성 상태
- Internal API service credential 거부/허용 및 Actor 재검증
- User 조회의 Email masking
- User Group/Role 변경
- 마지막 ACTIVE ADMIN 보호
- suspend/resume와 기존 session invalidate
- Email OTP 관리자 재인증
- opaque proof, Email reveal, TTL 만료
- Admin Audit 이벤트와 민감 plaintext 컬럼 부재

변경 파일:

```text
backend/admin-server/src/test/java/com/ssolab/admin/AdminServerApplicationTest.java
backend/auth-server/src/test/java/com/ssolab/auth/OidcTestProperties.java
```

- 일반 USER 403
- ADMIN + loa:1 정상 접근
- loa:1 미달 403
- mutation CSRF 거부/허용
- 재인증 proof가 Browser 응답에 없는지 검증
- Test JVM 외부 env와 무관하도록 exact OIDC redirect URI 고정

## 4. 추가 문서

```text
md/BOOTSTRAP_ADMIN_GUIDE.md
```

최초 claim, claim 후 bootstrap 비활성화, 추가 관리자 정책, 오류/복구 주의사항을 기록했다.

`md/AI_AGENT_START_HERE.md`의 진행 상태는 Phase 5 완료, Phase 6 다음 구현 대상으로 변경했다. Master Specification은 수정하지 않았다.

## 5. 로컬 검증 결과

### Backend

```text
Gradle 8.14.3 / Java 21
clean build
결과: BUILD SUCCESSFUL
```

로컬 PC에는 Docker daemon이 없으므로 Testcontainers 통합 테스트 14건은 skip되었고, 나머지 Unit/BFF 테스트는 성공했다. Testcontainers는 아래 Ubuntu VM에서 실제 실행했다.

### Frontend

```text
auth-web      npm run build 성공
admin-web     npm run build 성공
hr-web        npm run build 성공
approval-web  npm run build 성공
```

Admin Web TypeScript strict check와 Vite production build가 성공했다.

### 정적 경계/노출 확인

- Admin Web/Admin Server에서 Browser storage Token 사용 문자열 없음
- Admin Server에 JPA/JDBC/PostgreSQL/Identity Repository 없음
- Backend logger/System.out에서 OTP/Code/Token/Secret/Email 출력 패턴 없음
- `docker-compose.yml`에 Host published port 없음

## 6. Ubuntu VM 테스트 경로 변경

변경 대상은 다음 경로뿐이다.

```text
/opt/sso-lab-test
```

소스 동기화 제외:

```text
.git
.env
secrets/
node_modules/
build/
dist/
logs/
```

추가로 개발 cache/tooling 디렉터리도 전송 대상에서 제외했다.

보존한 테스트 설정:

```text
/opt/sso-lab-test/.env.test
/opt/sso-lab-test/.env.phase4.local
```

Phase 5에 필요한 `ADMIN_INTERNAL_API_SECRET`이 없어서 `.env.phase4.local`에 테스트 전용 random 값을 값 노출 없이 한 번 추가했다. 파일 권한은 `600`으로 유지했다. 기존 값을 덮어쓰지 않았으며 문서/로그/Git에는 기록하지 않았다.

동기화 중 최초 staging을 `/opt/sso-lab-test` 내부에 둔 탓에 `rsync --delete`가 staging 원본을 지우는 경고가 발생했다. 명령은 Secret 추가 전에 중단됐고 운영 경로 및 테스트 env에는 영향이 없었다. staging을 고정 `/tmp/sso-lab-phase5-staging`으로 옮긴 뒤 전체 소스를 다시 동기화해 해결했다.

## 7. Ubuntu VM 테스트 결과

### Gradle/Testcontainers

```text
전체 테스트: 37
skipped: 0
failures: 0
errors: 0
결과: BUILD SUCCESSFUL
```

PostgreSQL 17 Testcontainers에서 Phase 1~5 회귀 테스트를 모두 실행했다.

### Docker Compose

```text
docker compose config --quiet  성공
전체 8개 application image build 성공
docker compose up --wait       성공
```

실제 상태:

```text
postgres         healthy
auth-server      healthy
admin-server     healthy
hr-server        healthy
approval-server  healthy
auth-web         healthy
admin-web        healthy
hr-web           healthy
approval-web     healthy
```

### PostgreSQL/Flyway

```text
PostgreSQL server: 17.11
Flyway version 5: success=true
```

### Docker network/service boundary

```text
db-network       auth-server, postgres만 연결 / internal=true
internal-network auth-server, admin-server만 연결 / internal=true
public-network   application services만 연결 / postgres 없음
```

모든 컨테이너의 Host `PortBindings`는 빈 값이었다. PostgreSQL 5432, Backend 8080, Frontend 8080은 Host/Internet에 publish되지 않았다.

Admin Server 컨테이너에서 Auth Server Internal API까지 실제 network 연결을 확인했다. 두 서비스의 test credential 일치도 값을 출력하지 않고 비교했으며, credential 통과 후 존재하지 않는 Actor가 400으로 거부되어 service credential과 Actor revalidation이 모두 작동함을 확인했다.

### 로그

테스트 Compose 전체 로그와 `.env.test`/`.env.phase4.local`의 `SECRET`, `KEY`, `PASSWORD` 값을 실제 값 출력 없이 비교했다.

```text
sensitive-env-values-not-found-in-compose-logs
```

## 8. 검증 중 발견하고 해결한 문제

1. 통합 테스트가 `roles.role_name`을 조회했으나 실제 컬럼은 `roles.name`이었다. 테스트 query를 수정했다.
2. VM shell의 redirect URI env가 OIDC 테스트로 유입되어 localhost allow-list 테스트가 실패했다. `OidcTestProperties`에 테스트용 exact redirect URI를 명시해 외부 env와 격리했다.
3. TTL 만료 fixture가 `expires_at > issued_at` DB CHECK를 위반했다. 과거 issued/expiry 순서를 유지하도록 fixture를 수정했다.
4. Production profile image에는 `RestClient.Builder` 자동 Bean이 없어 Admin Server가 재시작했다. `InternalAdminClient`가 명시적 `RestClient.builder()`를 사용하도록 수정했고 local test와 Docker health를 재검증했다.
5. 최초 VM staging 위치가 rsync 대상 내부여서 source vanish 경고가 발생했다. `/tmp` 외부 staging으로 변경하고 전체 재동기화했다.

## 9. 운영 전에 사용자가 준비할 설정

- 운영용 `ADMIN_INTERNAL_API_SECRET`: OIDC Client Secret과 다른 강한 random 값. 가능하면 Docker Secret 등 운영 SecretProvider 사용
- 최초 Bootstrap 수행 시에만 `BOOTSTRAP_ADMIN_ENABLED=true`와 실제 OTP 수신 가능한 `BOOTSTRAP_ADMIN_EMAIL` 설정
- Bootstrap claim과 Audit 확인 직후 `BOOTSTRAP_ADMIN_ENABLED=false`, Email 값 제거
- Admin OIDC Client Secret과 exact production redirect URI 유지
- 실제 Gmail SMTP 설정/검증은 아직 완료하지 않았으며 Master의 후속 범위에서 처리
- 최종 Browser E2E는 Phase 9에서 수행

자세한 Bootstrap 절차는 `md/BOOTSTRAP_ADMIN_GUIDE.md`를 따른다.

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

Phase 6 Logout 기능은 구현하지 않았다.

## 11. 다음 Phase

Phase 6 — Logout에서 Master Specification에 따라 다음 범위를 구현할 예정이다.

```text
RP-Initiated Logout
Back-Channel Logout
Global Logout
Session UI
```

Phase 6은 사용자의 명시적 요청 전까지 시작하지 않는다.
