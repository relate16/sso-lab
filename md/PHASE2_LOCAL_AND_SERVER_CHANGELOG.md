# Phase 2 로컬 및 Ubuntu VM 변경 이력

- 작성일: 2026-08-20 (Asia/Seoul)
- 로컬 Repository: `C:\dev\sso-lab`
- Ubuntu VM 테스트 경로: `/opt/sso-lab-test`
- 보호 대상 운영 경로: `/opt/sso-lab`
- 공식 Phase 기준: `AI_AGENT_MASTER_SPEC_SSO_LAB.md`의 9개 Phase
- 현재 상태: **Phase 2 — Identity 완료**
- 다음 구현 대상: **Phase 3 — Passwordless (미착수)**

이 문서는 Phase 2에서 로컬 Repository와 Ubuntu VM 테스트 환경에 적용한 변경을 나중에 추적하기 위한 기록이다. 실제 Secret, 비밀번호, 개인 키 및 `.env.test`의 값은 기록하지 않는다.

---

## 1. 작업 범위와 기준

Phase 2는 다음 문서를 다시 확인한 뒤 Master Specification의 Identity 범위로만 구현했다.

- `md/AI_AGENT_START_HERE.md`
- `md/AI_AGENT_MASTER_SPEC_SSO_LAB.md`

구현 범위:

- User, Role, 계층형 Group 데이터 모델
- User–Role 및 User–Group 다대다 관계
- Email AES-256-GCM 암호화
- Email lookup HMAC-SHA256
- `SecretProvider` 추상화와 Environment/Docker Secret 구현
- Typed System Config
- Flyway V2 Migration
- Repository/Service 계층 및 입력 검증
- Unit Test와 PostgreSQL Testcontainers Integration Test
- Ubuntu VM Docker Compose 통합 검증

이번 Phase에서 의도적으로 제외한 범위:

- Signup OTP
- Login Email OTP
- TOTP
- Recovery Code
- Passwordless 인증 흐름
- OIDC/SSO, Admin, Logout 등 후속 Phase 기능

`EMAIL_OTP_*` System Config 키는 Master Specification의 Phase 2 Typed System Config 기본값 정의에 포함되어 있어 설정 정의만 추가했으며, OTP 기능이나 흐름은 구현하지 않았다.

---

## 2. 로컬 Repository 변경 사항

### 2.1 루트 및 애플리케이션 설정

| 파일 | 변경 내용 |
|---|---|
| `.env.example` | Email 암호화/HMAC의 Secret provider, reference, key version용 placeholder 추가. 실제로 사용할 수 없는 `CHANGE_ME` 예시만 포함 |
| `docker-compose.yml` | `auth-server`에 Identity crypto provider/reference/version 설정 전달. 외부 publish port는 추가하지 않음 |
| `backend/auth-server/src/main/java/com/ssolab/auth/AuthServerApplication.java` | `@ConfigurationPropertiesScan` 활성화 |
| `backend/auth-server/src/main/java/com/ssolab/auth/config/IdentityTimeConfiguration.java` | 일관된 UTC 시각 사용을 위한 `Clock` Bean 추가 |
| `backend/auth-server/src/main/resources/application.yml` | Docker Secret base path, Email encryption key version map, HMAC provider/reference 설정 추가 |
| `md/AI_AGENT_START_HERE.md` | Phase 2 완료 및 Phase 3이 다음 구현 대상임을 진행 상태에 반영 |

`AI_AGENT_MASTER_SPEC_SSO_LAB.md`는 수정하지 않았다.

### 2.2 Flyway Migration

추가 파일:

- `backend/auth-server/src/main/resources/db/migration/V2__create_identity_model.sql`

생성한 `auth` schema 객체:

| 테이블 | 용도 및 주요 제약 |
|---|---|
| `auth.users` | UUID PK, user ID/normalized ID, username, 암호화 Email, HMAC lookup hash, 상태, UTC timestamp |
| `auth.roles` | `USER`, `ADMIN` 역할. 고정 UUID로 기본 데이터 입력 |
| `auth.user_roles` | User–Role 다대다 관계 |
| `auth.groups` | nullable self FK `parent_id`를 사용하는 계층형 Group |
| `auth.user_groups` | User–Group 다대다 관계 |
| `auth.system_config` | 타입 정의를 통해 접근하는 시스템 설정 저장소 |

주요 DB 규칙:

- `normalized_user_id` unique 및 lowercase 강제
- `email_lookup_hash` unique 및 정확히 32 bytes 강제
- AES-GCM IV는 정확히 12 bytes 강제
- Email encryption key version은 1 이상
- User 상태는 `ACTIVE`, `SUSPENDED`만 허용
- Role은 `USER`, `ADMIN`만 허용
- Group 자기 자신 parent 지정 차단
- PostgreSQL `UNIQUE NULLS NOT DISTINCT (parent_id, normalized_name)`로 같은 parent 아래 이름 중복 차단
- 관계별 FK와 delete 정책 및 조회 index 추가
- `auth.users`에 평문 `email` column을 만들지 않음

### 2.3 Identity Crypto

추가 파일:

- `backend/auth-server/src/main/java/com/ssolab/auth/identity/crypto/EmailCipher.java`
- `backend/auth-server/src/main/java/com/ssolab/auth/identity/crypto/EmailLookupHasher.java`
- `backend/auth-server/src/main/java/com/ssolab/auth/identity/crypto/EmailNormalizer.java`
- `backend/auth-server/src/main/java/com/ssolab/auth/identity/crypto/EncryptedEmail.java`
- `backend/auth-server/src/main/java/com/ssolab/auth/identity/crypto/IdentityCryptoException.java`
- `backend/auth-server/src/main/java/com/ssolab/auth/identity/crypto/IdentityCryptoProperties.java`
- `backend/auth-server/src/main/java/com/ssolab/auth/identity/crypto/SecretKeyMaterialLoader.java`

설계:

- Email은 trim/NFC/lowercase 정규화 후 처리
- 저장 암호화는 AES-256-GCM
- 매 암호화마다 `SecureRandom`으로 12-byte IV 생성
- GCM authentication tag는 128-bit이며 ciphertext에 포함
- AAD는 `sso-lab:email:v{keyVersion}` 형식
- 현재 쓰기 key version과 과거 복호화용 version map을 분리하여 key rotation 지원
- SecretProvider에서 읽은 Base64 key가 정확히 32 bytes인지 검증
- Lookup 값은 별도의 독립 key로 HMAC-SHA256 계산
- HMAC key는 최소 32 bytes를 요구하며 결과는 deterministic 32-byte hash
- 암호화 key와 lookup HMAC key를 재사용하지 않음
- 평문 Email은 DB에 저장하거나 lookup 조건으로 사용하지 않음

### 2.4 Identity Model

추가 파일:

- `backend/auth-server/src/main/java/com/ssolab/auth/identity/model/AccountStatus.java`
- `backend/auth-server/src/main/java/com/ssolab/auth/identity/model/IdentityGroupEntity.java`
- `backend/auth-server/src/main/java/com/ssolab/auth/identity/model/RoleEntity.java`
- `backend/auth-server/src/main/java/com/ssolab/auth/identity/model/RoleName.java`
- `backend/auth-server/src/main/java/com/ssolab/auth/identity/model/SystemConfigEntity.java`
- `backend/auth-server/src/main/java/com/ssolab/auth/identity/model/UserIdentityEntity.java`

관계:

- User–Role: 다대다
- User–Group: 다대다
- Group–Parent Group: nullable self reference
- Account status: `ACTIVE`, `SUSPENDED`

### 2.5 Repository 계층

추가 파일:

- `backend/auth-server/src/main/java/com/ssolab/auth/identity/repository/IdentityGroupRepository.java`
- `backend/auth-server/src/main/java/com/ssolab/auth/identity/repository/RoleRepository.java`
- `backend/auth-server/src/main/java/com/ssolab/auth/identity/repository/SystemConfigRepository.java`
- `backend/auth-server/src/main/java/com/ssolab/auth/identity/repository/UserIdentityRepository.java`

Identity 영속성은 `auth-server`에만 추가했다. `admin-server`, `hr-server`, `approval-server`에는 Identity DB 접근용 JPA/JDBC/DataSource 코드를 추가하지 않았다.

### 2.6 Service 및 Validation 계층

추가 파일:

- `backend/auth-server/src/main/java/com/ssolab/auth/identity/service/CreateUserIdentityCommand.java`
- `backend/auth-server/src/main/java/com/ssolab/auth/identity/service/GroupHierarchyService.java`
- `backend/auth-server/src/main/java/com/ssolab/auth/identity/service/IdentityConflictException.java`
- `backend/auth-server/src/main/java/com/ssolab/auth/identity/service/IdentityInputNormalizer.java`
- `backend/auth-server/src/main/java/com/ssolab/auth/identity/service/IdentityMembershipService.java`
- `backend/auth-server/src/main/java/com/ssolab/auth/identity/service/IdentityNotFoundException.java`
- `backend/auth-server/src/main/java/com/ssolab/auth/identity/service/InvalidGroupHierarchyException.java`
- `backend/auth-server/src/main/java/com/ssolab/auth/identity/service/UserIdentityService.java`

주요 동작과 보호 규칙:

- user ID: trim/lowercase, 길이 4–30, 문자 `a-z`, `0-9`, `.`, `_`, `-`만 허용
- username: trim/NFC, 길이 1–100
- email: command 생성 시 trim한 뒤 Bean Validation, 이후 NFC/lowercase 정규화
- User 생성 시 필수 `USER` role 자동 부여
- `USER` role 제거 금지
- 마지막 active `ADMIN`의 ADMIN role 제거 방지
- Group 생성, 이동, 삭제, full path 계산 제공
- Group 자기 참조와 순환 구조 차단
- 같은 parent 아래 대소문자 차이만 있는 Group 이름 중복 차단
- child 또는 member가 존재하는 Group 삭제 차단
- Group 경로 예: `/회사/개발본부/백엔드팀`

Controller/API 및 Passwordless 인증 endpoint는 이번 Phase에 추가하지 않았다.

### 2.7 SecretProvider

추가 파일:

- `backend/auth-server/src/main/java/com/ssolab/auth/secret/DockerSecretProperties.java`
- `backend/auth-server/src/main/java/com/ssolab/auth/secret/DockerSecretProvider.java`
- `backend/auth-server/src/main/java/com/ssolab/auth/secret/EnvironmentSecretProvider.java`
- `backend/auth-server/src/main/java/com/ssolab/auth/secret/SecretProvider.java`
- `backend/auth-server/src/main/java/com/ssolab/auth/secret/SecretProviderRegistry.java`
- `backend/auth-server/src/main/java/com/ssolab/auth/secret/SecretProviderType.java`
- `backend/auth-server/src/main/java/com/ssolab/auth/secret/SecretReference.java`
- `backend/auth-server/src/main/java/com/ssolab/auth/secret/SecretUnavailableException.java`
- `backend/auth-server/src/main/java/com/ssolab/auth/secret/SecretValue.java`

구조와 보안 조치:

- `ENVIRONMENT`, `DOCKER_SECRET` provider 구현
- registry를 통해 provider 선택 및 향후 확장 가능
- Docker Secret reference allow-list: `[A-Za-z0-9._-]{1,128}`
- path traversal이 가능한 reference 차단
- `SecretValue.toString()`은 실제 값을 출력하지 않음
- `SecretValue.close()` 시 내부 `char[]`을 zeroing
- 실제 key와 실제 Secret 파일은 생성하거나 Git에 포함하지 않음
- AWS SSM provider는 Phase 2에 구현하지 않았으며 후속 Infrastructure/AWS 작업 대상으로 남김

### 2.8 Typed System Config

추가 파일:

- `backend/auth-server/src/main/java/com/ssolab/auth/systemconfig/IdentityPolicyConfig.java`
- `backend/auth-server/src/main/java/com/ssolab/auth/systemconfig/SystemConfigDefinition.java`
- `backend/auth-server/src/main/java/com/ssolab/auth/systemconfig/SystemConfigDefinitions.java`
- `backend/auth-server/src/main/java/com/ssolab/auth/systemconfig/TypedSystemConfigService.java`

Master Specification 기본값:

| Key | 기본값 |
|---|---:|
| `SSO_SESSION_IDLE_TIMEOUT` | 30m |
| `SSO_SESSION_ABSOLUTE_TIMEOUT` | 8h |
| `ACCESS_TOKEN_TTL` | 5m |
| `ID_TOKEN_TTL` | 5m |
| `REFRESH_TOKEN_TTL` | 8h |
| `ADMIN_REAUTH_TTL` | 5m |
| `EMAIL_OTP_TTL` | 5m |
| `EMAIL_OTP_MAX_ATTEMPTS` | 5 |
| `EMAIL_OTP_RESEND_INTERVAL` | 60s |

구현 특성:

- 설정 key, parser, 기본값, 유효 범위를 definition에 집중
- duration은 `s`, `m`, `h`, `d` 단위 또는 ISO-8601 형식 지원
- DB 값이 없으면 code default 사용
- DB 값이 잘못되면 원문 값을 log에 남기지 않고 warning 후 safe default 사용
- 저장 시 validation 후 `saveAndFlush()`로 즉시 DB 반영
- 임의의 Secret을 System Config에 저장하는 API는 제공하지 않음

### 2.9 테스트 코드

수정:

- `backend/auth-server/src/test/java/com/ssolab/auth/AuthServerPostgresqlIntegrationTest.java`
  - 기존 Phase 1 PostgreSQL/Flyway 검증을 유지하고 Phase 2 통합 시나리오를 총 4개로 확장

추가:

- `backend/auth-server/src/test/java/com/ssolab/auth/identity/crypto/EmailCryptoTest.java`
- `backend/auth-server/src/test/java/com/ssolab/auth/identity/service/CreateUserIdentityCommandValidationTest.java`
- `backend/auth-server/src/test/java/com/ssolab/auth/identity/service/GroupHierarchyServiceTest.java`
- `backend/auth-server/src/test/java/com/ssolab/auth/identity/service/IdentityInputNormalizerTest.java`
- `backend/auth-server/src/test/java/com/ssolab/auth/secret/DockerSecretProviderTest.java`
- `backend/auth-server/src/test/java/com/ssolab/auth/systemconfig/TypedSystemConfigServiceTest.java`

최종 auth-server 테스트 수:

- Unit Test: 10
- PostgreSQL Testcontainers Integration Test: 4
- 합계: 14

---

## 3. 로컬 Build/Test 환경과 결과

Host PC 재시작 후 전역 `JAVA_HOME`이 이전 Java 8의 `bin` 경로를 가리키는 상태여서, 전역 설정이나 실제 환경 파일을 변경하지 않고 command scope에서 Repository 내 도구를 사용했다.

사용한 로컬 도구:

- JDK: `C:\dev\sso-lab\.tooling\jdk21\jdk-21.0.12+8`
- Gradle: `C:\dev\sso-lab\.tooling\gradle-8.14.3\bin\gradle.bat`
- Gradle cache: `C:\dev\sso-lab\.gradle-user-home`

결과:

| 검증 | 결과 |
|---|---|
| Root `gradle build --no-daemon` | `BUILD SUCCESSFUL`, 28 tasks |
| auth-server Unit Test | 10 passed |
| auth-server Testcontainers Test | 로컬 Docker 부재로 4 skipped |
| 전체 auth-server test | 14 total, 10 passed, 4 skipped, 0 failed, 0 errors |
| `git diff --check` | 통과 |
| 평문 Email column 정적 확인 | 없음 |
| 실제 Secret/고정 key 정적 확인 | 없음 |
| 타 Backend의 Identity DB 직접 접근 정적 확인 | 없음 |

로컬에서 skipped된 4개 통합 테스트는 Ubuntu VM Docker 환경에서 실제로 실행하여 모두 통과했다.

---

## 4. Ubuntu VM 테스트 환경 변경 사항

### 4.1 접속 및 보호 범위

- SSH host: `today-sso.duckdns.org`
- SSH user: `today`
- 테스트 전용 경로: `/opt/sso-lab-test`
- 운영 보호 경로: `/opt/sso-lab`

이번 작업은 기본 Windows SSH 인증으로 수행했다. 테스트 전용 로컬 key 파일을 사용한 인증은 실패했으나, `authorized_keys`를 수정하거나 기존 key를 삭제하지 않았다.

### 4.2 소스 동기화

로컬 최신 소스를 archive로 만들어 `/opt/sso-lab-test`에 동기화했다.

제외한 항목:

- `.git`
- `.env`
- `.env.test`
- `secrets/`
- `node_modules/`
- `build/`
- `dist/`
- `logs/`
- `.tooling/`
- `.gradle/`
- `.gradle-user-home/`
- `.pnpm-store/`
- `.idea/`

동기화 archive 검증:

- 총 223 entries
- 금지 항목 포함 수: 0
- 전송 후 로컬 임시 archive 삭제
- 추출 후 VM `/tmp`의 임시 archive 삭제

기존 `/opt/sso-lab-test/.env.test`는 덮어쓰거나 수정하지 않았다.

- 검증 전후 SHA-256: `3a2bc19266c9b0a9ebc14fe299451d5f14f3d0ba99b412c3cf522bd8c5429ef2`
- 확인한 key 이름만 기록: `AUTH_DB_URL`, `COMPOSE_PROJECT_NAME`, `POSTGRES_DB`, `POSTGRES_PASSWORD`, `POSTGRES_USER`, `SPRING_PROFILES_ACTIVE`
- 값은 이 문서에 기록하지 않음

`.env.test`에는 새 Identity crypto key를 추가하지 않았다. Compose health smoke test는 key를 실제 암호화 시점에 lazy load하므로 정상 기동했고, crypto 동작은 Testcontainers 테스트에서 실행 시 생성한 독립 테스트 key로 검증했다.

### 4.3 VM 테스트 전용 도구

VM에 Java/Gradle을 system-wide 설치하지 않고 공식 `gradle:8.14.3-jdk21` image에서 필요한 runtime을 테스트 경로로 복사했다.

생성/유지한 경로:

- `/opt/sso-lab-test/.test-tools/jdk` — Java 21.0.9
- `/opt/sso-lab-test/.test-tools/gradle` — Gradle 8.14.3
- `/opt/sso-lab-test/.gradle-test-home` — 테스트 전용 Gradle cache

Docker image cache:

- `gradle:8.14.3-jdk21` pull 후 cache에 유지

임시 Gradle container는 도구 복사 후 삭제했다. Docker socket을 build container에 mount하여 daemon 제어 권한을 주는 방식은 사용하지 않고, `today` 계정에서 host-side Gradle Testcontainers 테스트를 실행했다.

### 4.4 Docker Compose 및 DB

수행 내용:

- Backend/Frontend를 포함한 8개 application image build
- PostgreSQL 17 Alpine container 기동
- Flyway V1/V2 실제 적용
- 전체 `docker compose up --wait` smoke test
- Backend health, network membership, published port 확인

Flyway 실제 적용 결과:

| 항목 | 결과 |
|---|---|
| Schema creation | Success |
| V1 | Success |
| V2 `create identity model` | Success |

DB 확인 결과:

- 기본 Role: `ADMIN`, `USER`
- User 수: 0
- `auth.users`에 `email_ciphertext`, `email_iv`, `email_key_version`, `email_lookup_hash` 존재
- 평문 `email` column 없음

최종 Compose 상태:

- 9개 service 모두 running/healthy
- PostgreSQL 1개
- Backend 4개: auth/admin/hr/approval
- Frontend 4개: auth/admin/hr/approval
- 테스트 종료 후 Compose stack은 계속 실행 중
- DB volume에는 Flyway V2가 적용된 상태로 유지

### 4.5 Network와 Port 경계

Network membership:

| Service | Network |
|---|---|
| `postgres` | `sso-lab-test_db-network`만 사용 |
| `auth-server` | db/internal/public network |
| `admin-server` | internal/public network |
| `hr-server`, `approval-server` | public network |
| 모든 web service | public network |

외부 publish port 확인:

- 9개 service 모두 host published port 없음
- PostgreSQL `5432`, Backend `8080`은 container 내부 expose만 사용
- 불필요한 외부 port를 새로 열지 않음

---

## 5. Ubuntu VM 통합 검증 결과

### 5.1 Testcontainers

VM host에서 JDK 21/Gradle 8.14.3으로 실행했다.

| 테스트 분류 | 실행 | 성공 | Skipped | 실패 |
|---|---:|---:|---:|---:|
| `AuthServerPostgresqlIntegrationTest` | 4 | 4 | 0 | 0 |
| Email crypto | 2 | 2 | 0 | 0 |
| Command validation | 2 | 2 | 0 | 0 |
| Group hierarchy | 1 | 1 | 0 | 0 |
| Input normalization | 2 | 2 | 0 | 0 |
| Docker Secret provider | 1 | 1 | 0 | 0 |
| Typed System Config | 2 | 2 | 0 | 0 |
| **합계** | **14** | **14** | **0** | **0** |

Testcontainers PostgreSQL image:

- `postgres:17-alpine`

검증된 통합 항목:

- Flyway V1/V2 적용
- JPA schema validation
- PostgreSQL 기반 User/Role/Group 관계
- Email AES-GCM 암호화와 HMAC lookup
- Typed System Config DB 저장/조회
- Application health

### 5.2 Docker Compose smoke test

| 검증 항목 | 결과 |
|---|---|
| Docker image build | 성공 |
| PostgreSQL container 기동 | 성공/healthy |
| Flyway V1/V2 실제 적용 | 성공 |
| 전체 Compose up | 9개 service 성공/healthy |
| auth-server health | `UP` |
| admin-server health | `UP` |
| hr-server health | `UP` |
| approval-server health | `UP` |
| Docker network 연결 | 설계한 network membership과 일치 |
| 외부 불필요 port publish | 없음 |
| Identity DB 서비스 경계 | `auth-server`만 persistence 소유 |

각 Backend health 응답은 liveness/readiness group과 `status: UP`을 반환했다.

---

## 6. 검증 중 발견하고 수정한 문제

### 6.1 로컬 Java 환경

- 문제: PC 재시작 후 전역 `JAVA_HOME`이 Java 8 `bin` 경로를 가리켜 Gradle 실행 불가
- 처리: 전역 환경을 바꾸지 않고 command scope에서 Repository의 JDK 21 사용

### 6.2 Email Crypto Unit Test assertion

- 문제: ciphertext 전체 배열 비교가 아니라 개별 byte 포함 여부를 검사하는 잘못된 assertion
- 처리: byte array 전체 동등성/비동등성 검증으로 수정

### 6.3 Windows 임시 디렉터리 권한

- 문제: JUnit 임시 경로 cleanup에서 `AccessDeniedException`
- 처리: Repository의 `build/tmp` 아래 테스트 경로 사용

### 6.4 Spring constructor injection

- 문제: `EmailCipher`에 생성자가 두 개 있어 Spring 7이 사용할 생성자를 결정하지 못함
- 처리: production constructor에 `@Autowired`를 명시

### 6.5 Email 공백 Validation 순서

- 문제: 앞뒤 공백이 있는 Email이 정규화 전에 Bean Validation에서 실패
- 처리: record compact constructor에서 먼저 입력을 trim한 뒤 validation하도록 수정

### 6.6 Typed System Config 즉시 DB 확인

- 문제: JPA persist 후 직접 JDBC로 조회하는 통합 테스트에서 flush 전이라 row가 보이지 않음
- 처리: 설정 저장을 `saveAndFlush()`로 변경

### 6.7 Testcontainers 실행 방식

- 문제: Docker socket을 Gradle container에 mount하면 해당 container가 host Docker daemon을 제어할 수 있어 검증 방식으로 부적절
- 처리: 공식 Gradle image에서 JDK/Gradle만 추출하고, 제한된 `today` 계정에서 host-side로 테스트 실행

---

## 7. 변경하지 않은 보호 대상

다음 항목은 생성·수정·삭제하지 않았다.

- `/opt/sso-lab` 운영 배포 디렉터리
- 운영 `.env`
- 운영 `secrets/`
- VM의 UFW 규칙
- VM host network 설정
- 운영 DB와 운영 Docker resource
- 기존 SSH `authorized_keys`
- 실제 서버 Secret 또는 암호화 key
- 로컬/VM의 실제 `.env` 값

`/opt/sso-lab-test/.env.test`도 기존 파일과 값을 그대로 유지했다.

---

## 8. 현재 상태와 남은 TODO

현재 공식 진행 상태:

| Phase | 상태 |
|---|---|
| Phase 1 — Skeleton | 완료 |
| Phase 2 — Identity | 완료 |
| Phase 3 — Passwordless | 다음 구현 대상, 미착수 |
| Phase 4 — OIDC | 미착수 |
| Phase 5 — Admin | 미착수 |
| Phase 6 — Logout | 미착수 |
| Phase 7 — Security | 미착수 |
| Phase 8 — Infra | 미착수 |
| Phase 9 — Test / Docs | 미착수 |

남은 설정/TODO:

- 실제 Identity 기능을 사용하는 환경에는 AES-256 encryption key와 독립된 HMAC-SHA256 key를 SecretProvider reference로 별도 준비해야 함
- Git에 넣지 말고 Environment 또는 Docker Secret 등 승인된 Secret source로 제공해야 함
- 기존 `/opt/sso-lab-test/.env.test`는 이번 작업에서 변경하지 않았으므로 Compose smoke test에서는 실제 Email 암호화 operation을 호출하지 않음
- Email crypto의 실제 operation은 Testcontainers에서 테스트 전용 key를 runtime에 주입해 검증 완료
- AWS SSM 기반 SecretProvider는 후속 Infrastructure/AWS 범위에서 검토
- Phase 3 요청 전까지 OTP/TOTP/Recovery Code/Passwordless 구현을 시작하지 않음

---

## 9. 변경 추적용 명령

로컬 변경 파일 확인:

```powershell
git -c safe.directory=C:/dev/sso-lab status --short
```

Phase 2 Identity 소스 확인:

```powershell
rg --files backend/auth-server/src/main/java/com/ssolab/auth/identity
rg --files backend/auth-server/src/main/java/com/ssolab/auth/secret
rg --files backend/auth-server/src/main/java/com/ssolab/auth/systemconfig
```

Migration 확인:

```powershell
Get-Content backend/auth-server/src/main/resources/db/migration/V2__create_identity_model.sql
```

VM 테스트 경로의 현재 Compose 상태 확인:

```bash
cd /opt/sso-lab-test
docker compose --env-file .env.test ps
```

Secret 보호를 위해 `.env`, `.env.test`, `secrets/` 또는 key 파일 내용을 화면에 출력하거나 이 문서에 붙여 넣지 않는다.
