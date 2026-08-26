# SSO Lab Phase 1 로컬 및 서버 변경 기록

- 작성일: 2026-08-20 (Asia/Seoul)
- 로컬 Repository: `C:\dev\sso-lab`
- Ubuntu 테스트 경로: `/opt/sso-lab-test`
- 운영 보호 경로: `/opt/sso-lab`
- 현재 구현 범위: Phase 1 — Project Foundation
- Phase 2 상태: 시작하지 않음

이 문서는 Phase 1 구현과 원격 Docker 검증 과정에서 생성·수정한 파일과 서버 리소스를 나중에 추적하기 위한 기록이다. Secret 원문, 비밀번호, 개인 키 본문은 의도적으로 기록하지 않는다.

## 1. 기준과 변경 범위

Phase 1 시작 전 Repository에는 Spring Initializr 기반 Spring Boot 프로젝트가 없었다. 다음 네 문서를 우선순위에 따라 읽고 구현 기준으로 사용했다.

- `md/AI_AGENT_START_HERE.md`
- `md/AI_AGENT_MASTER_SPEC_SSO_LAB.md`
- `md/SSO_LAB_VM_ENVIRONMENT_SETUP_GUIDE.md`
- `md/ubuntu_vm_network_environment.md`

위 네 파일은 사용자가 제공한 기준 문서다. Git 상태에서는 untracked로 표시되지만 Phase 1 애플리케이션 구현 과정에서 생성한 파일로 분류하지 않는다.

구현 및 검증에 적용한 핵심 버전은 다음과 같다.

| 구분 | 적용 버전 |
|---|---:|
| Java | 21 |
| Spring Boot | 4.1.0 |
| Gradle Wrapper | 8.14.3 |
| PostgreSQL | 17 (`17.11` 런타임 확인) |
| React | 19.2.8 |
| Vite | 8.2.2 |
| Node Docker build image | 24-alpine |

## 2. 로컬 Repository 변경 파일

### 2.1 기존 tracked 파일 수정

현재 Git 기준 tracked 수정 파일은 다음 5개다.

| 파일 | 변경 내용 |
|---|---|
| `.env.example` | Compose project, PostgreSQL, JDBC URL, runtime profile, 공개 URL, Phase 2+용 비밀이 아닌 placeholder를 추가했다. 모든 Secret은 `CHANGE_ME` 또는 placeholder이며 실제 값은 없다. |
| `.gitignore` | `.gradle-user-home/`, `.tooling/`, `.pnpm-store/`, `backups/`를 추가하고 파일 끝 줄바꿈을 정리했다. 기존 `.env`, `secrets/`, `build/`, `node_modules/`, `dist/`, `logs/` 제외 정책은 유지했다. |
| `DEVELOPER_SETUP.txt` | Java 21, Node.js 24, Docker/Compose, Backend/Frontend build, `.env` 작성 원칙 및 Phase 1 실행 절차를 기록했다. |
| `README.md` | Phase 1 범위, 기술 스택, 서비스 경계, build 명령, Secret 및 port 공개 원칙을 추가했다. |
| `docker-compose.yml` | PostgreSQL과 Backend 4개, Frontend 4개 등 9개 서비스를 정의했다. DB 직접 접근은 auth-server에만 부여하고 public/internal/db network를 분리했다. 모든 서비스는 host port를 publish하지 않고 healthcheck를 가진다. |

### 2.2 최상위 및 Gradle 신규 파일

| 파일 | 내용/역할 |
|---|---|
| `.dockerignore` | Git, 문서, 로컬 캐시, build 결과, 실제 `.env`, `secrets/`, backup 및 로그가 Docker build context에 들어가지 않도록 제외한다. `.env.example`만 예외로 허용한다. |
| `.gitattributes` | Gradle/쉘/Docker/YAML/SQL/config 파일은 LF, Windows batch 파일은 CRLF로 고정한다. |
| `build.gradle.kts` | Spring Boot 4.1.0, dependency-management 1.1.7, Java 21 toolchain, JUnit Platform과 공통 Backend 설정을 정의한다. |
| `settings.gradle.kts` | `sso-lab` root project와 Backend 4개 subproject, Foojay toolchain resolver를 정의한다. |
| `gradle.properties` | build cache, configuration cache, parallel build, warning mode를 설정한다. |
| `gradle/wrapper/gradle-wrapper.jar` | Gradle Wrapper 실행 바이너리다. |
| `gradle/wrapper/gradle-wrapper.properties` | Gradle 8.14.3 distribution URL, SHA-256, timeout 및 URL 검증 설정을 포함한다. |
| `gradlew` | Unix용 Gradle Wrapper 실행 스크립트다. |
| `gradlew.bat` | Windows용 Gradle Wrapper 실행 스크립트다. |

### 2.3 Backend 신규 파일

#### auth-server

| 파일 | 내용/역할 |
|---|---|
| `backend/auth-server/build.gradle.kts` | Web MVC, Security, Actuator, Validation, JPA, Flyway, JDBC Session, PostgreSQL 및 Testcontainers 의존성을 정의한다. 네 Backend 중 DB/JPA/Flyway 의존성이 있는 유일한 서비스다. |
| `backend/auth-server/src/main/java/com/ssolab/auth/AuthServerApplication.java` | auth-server Spring Boot main class다. |
| `backend/auth-server/src/main/java/com/ssolab/auth/config/FoundationSecurityConfig.java` | health endpoint만 permit하고 나머지는 deny한다. form login과 HTTP Basic을 끄며 Phase 1 로그인 자격 증명을 만들지 않는다. |
| `backend/auth-server/src/main/resources/application.yml` | datasource, Flyway `auth` schema, Hibernate validate, Spring Session JDBC, graceful shutdown 및 health probe를 설정한다. DB 비밀번호는 환경변수로만 받는다. |
| `backend/auth-server/src/main/resources/db/migration/V1__initialize_auth_foundation.sql` | `SPRING_SESSION`, `SPRING_SESSION_ATTRIBUTES`, 관련 PK/FK/index를 만든다. Flyway default schema가 `auth`이므로 실제 테이블은 `auth` schema에 생성된다. |
| `backend/auth-server/src/test/java/com/ssolab/auth/AuthServerPostgresqlIntegrationTest.java` | Testcontainers `postgres:17-alpine`을 기동해 Flyway V1, `spring_session` 테이블 및 Actuator `UP` 상태를 확인한다. Docker가 없으면 skip하도록 설정돼 있으므로 원격 검증에서 XML의 `skipped=0`을 별도로 확인했다. |

#### admin-server

| 파일 | 내용/역할 |
|---|---|
| `backend/admin-server/build.gradle.kts` | Web MVC, Security, Actuator, Validation과 test 의존성을 정의한다. DB/JPA/Flyway 의존성은 없다. |
| `backend/admin-server/src/main/java/com/ssolab/admin/AdminServerApplication.java` | admin-server Spring Boot main class다. |
| `backend/admin-server/src/main/java/com/ssolab/admin/config/FoundationSecurityConfig.java` | health endpoint만 permit하고 나머지는 deny한다. |
| `backend/admin-server/src/main/resources/application.yml` | 포트 8080, graceful shutdown, forwarded header 및 health probe를 설정한다. |
| `backend/admin-server/src/test/java/com/ssolab/admin/AdminServerApplicationTest.java` | 최소 Spring context 기동 테스트다. |

#### hr-server

| 파일 | 내용/역할 |
|---|---|
| `backend/hr-server/build.gradle.kts` | Web MVC, Security, Actuator와 test 의존성을 정의한다. DB/JPA/Flyway 의존성은 없다. |
| `backend/hr-server/src/main/java/com/ssolab/hr/HrServerApplication.java` | hr-server Spring Boot main class다. |
| `backend/hr-server/src/main/java/com/ssolab/hr/config/FoundationSecurityConfig.java` | health endpoint만 permit하고 나머지는 deny한다. |
| `backend/hr-server/src/main/resources/application.yml` | 포트 8080, graceful shutdown, forwarded header 및 health probe를 설정한다. |
| `backend/hr-server/src/test/java/com/ssolab/hr/HrServerApplicationTest.java` | 최소 Spring context 기동 테스트다. |

#### approval-server

| 파일 | 내용/역할 |
|---|---|
| `backend/approval-server/build.gradle.kts` | Web MVC, Security, Actuator와 test 의존성을 정의한다. DB/JPA/Flyway 의존성은 없다. |
| `backend/approval-server/src/main/java/com/ssolab/approval/ApprovalServerApplication.java` | approval-server Spring Boot main class다. |
| `backend/approval-server/src/main/java/com/ssolab/approval/config/FoundationSecurityConfig.java` | health endpoint만 permit하고 나머지는 deny한다. |
| `backend/approval-server/src/main/resources/application.yml` | 포트 8080, graceful shutdown, forwarded header 및 health probe를 설정한다. |
| `backend/approval-server/src/test/java/com/ssolab/approval/ApprovalServerApplicationTest.java` | 최소 Spring context 기동 테스트다. |

### 2.4 Frontend 신규 파일

다음 네 독립 Vite/React 프로젝트를 생성했다.

- `frontend/auth-web`
- `frontend/admin-web`
- `frontend/hr-web`
- `frontend/approval-web`

각 프로젝트에는 아래 파일이 존재한다. `<service>`는 `auth`, `admin`, `hr`, `approval` 중 하나다.

| 파일 패턴 | 내용/역할 |
|---|---|
| `frontend/<service>-web/.dockerignore` | 해당 Frontend build context에서 `node_modules`, `dist` 등을 제외한다. |
| `frontend/<service>-web/package.json` | React 19.2.8, React DOM 19.2.8, Vite 8.2.2, TypeScript 6.0.3 및 `dev/lint/build/preview` script를 정의한다. |
| `frontend/<service>-web/package-lock.json` | npm 의존성 버전을 고정한다. |
| `frontend/<service>-web/tsconfig.json` | TypeScript compiler 설정이다. |
| `frontend/<service>-web/vite.config.ts` | React plugin과 Vite build 설정이다. |
| `frontend/<service>-web/index.html` | 각 서비스의 HTML entry point다. |
| `frontend/<service>-web/nginx.conf` | unprivileged nginx가 8080에서 정적 파일과 SPA fallback을 제공하도록 설정한다. |
| `frontend/<service>-web/src/main.tsx` | React root bootstrap이다. |
| `frontend/<service>-web/src/App.tsx` | Phase 1 서비스 식별용 foundation 화면이다. 인증/OIDC 기능은 없다. |
| `frontend/<service>-web/src/styles.css` | Phase 1 공통 형태의 화면 스타일이다. |
| `frontend/<service>-web/src/vite-env.d.ts` | Vite TypeScript type 선언이다. |

### 2.5 Docker image 신규 파일

| 파일 | 내용/역할 |
|---|---|
| `infra/docker/backend.Dockerfile` | Java 21 JDK multi-stage build로 지정 Backend의 `bootJar`를 만들고 Java 21 JRE Alpine 이미지에서 non-root `ssolab` 사용자로 실행한다. |
| `infra/docker/frontend.Dockerfile` | Node 24 Alpine에서 `npm ci`와 Vite build를 수행하고 `nginxinc/nginx-unprivileged:1.29-alpine`에서 non-root로 정적 파일을 제공한다. |

### 2.6 로컬 Git 제외 파일 및 도구

아래 항목은 Repository 소스가 아니라 로컬 build/검증용이며 `.gitignore` 또는 `.dockerignore` 대상이다.

| 경로 | 상태/용도 |
|---|---|
| `.tooling/gradle-8.14.3/` | 로컬 Backend 검증용 Gradle runtime. |
| `.tooling/gradle-8.14.3-bin.zip` | Gradle runtime 다운로드 파일. |
| `.tooling/jdk21/` | 로컬 Java 21 runtime. |
| `.tooling/temurin-jdk21.zip` | Java 21 runtime 다운로드 파일. |
| `.tooling/docker-compose.exe` | 로컬 Compose config 확인을 위해 준비했던 바이너리. 로컬 Docker Engine은 설치되지 않았다. |
| `.tooling/sso-lab-test-ed25519` 및 `.pub` | 초기 SSH 연결용으로 생성한 테스트 키 쌍. 현재 서버 `authorized_keys`에는 이 키가 없으며 기본 SSH 접속에도 사용하지 않는다. 개인 키 본문은 이 문서에 기록하지 않는다. |
| `.tooling/ssh-known-hosts` | 테스트 VM host key 기록. |
| `.tooling/install-sso-lab-test-key.ps1` | 비밀번호를 화면에서 직접 입력받아 SSH 공개 키를 등록하기 위해 만든 ASCII 전용 helper. |
| `.tooling/register-sso-lab-test-key-remote.sh` | 공개 키 등록 및 SSH 파일 권한 진단용 remote helper. |
| `.tooling/prepare-sso-lab-test-dir.ps1` | sudo prompt를 통해 `/opt/sso-lab-test`만 생성하기 위한 helper. |
| `.tooling/ssh-key-registration-diagnostic.txt` | 공개 키 본문과 비밀번호를 제외한 SSH 권한/지문 진단 결과. |
| `frontend/*-web/node_modules/` | 로컬 npm install 결과. Git 및 서버 소스 복사에서 제외한다. |
| `frontend/*-web/dist/` | 로컬 Vite build 결과. Git 및 서버 소스 복사에서 제외한다. |
| `backend/*/build/`, root `build/`, `.gradle/` | 로컬 Gradle build/cache 결과. Git 및 서버 소스 복사에서 제외한다. |

로컬 Windows 환경에는 WSL 2.7.12.0이 설치돼 있다. Docker CLI/Docker Desktop은 최종 확인 시 로컬에서 발견되지 않았고, Docker 검증은 Ubuntu VM에서 수행했다.

### 2.7 변경 기록 문서

| 파일 | 내용/역할 |
|---|---|
| `md/PHASE1_LOCAL_AND_SERVER_CHANGELOG.md` | 이 문서다. Phase 1 로컬 파일, 서버 테스트 경로, Docker 리소스, 검증 결과와 보호 대상을 한곳에서 추적한다. Secret 원문은 포함하지 않는다. |

## 3. Ubuntu 서버 변경 사항

### 3.1 SSH 사용자 파일

| 경로 | 현재 상태 | 변경 이력 |
|---|---|---|
| `/home/today/.ssh/authorized_keys` | `today:today`, 권한 `600`, 현재 1행 | 초기 테스트 키 등록 과정이 있었으나 현재는 사용자가 직접 등록한 ED25519 키 1개만 존재한다. 현재 지문은 `SHA256:dFo+tVsbX5cZg48y3+F9KR1kDJ+zX5E499ALbVXa6rU`이며 테스트용 Codex 키는 서버에 남아 있지 않다. 기존 파일 전체 내용과 공개 키 본문은 문서에 복사하지 않았다. |

초기 PowerShell helper의 한글 안내가 Windows PowerShell 5.1에서 깨진 원인은 UTF-8 BOM 없는 파일을 ANSI로 읽은 것이었다. helper 안내를 ASCII 영문으로 바꾸고 원격 전달 script도 ASCII/LF로 제한했다. 비밀번호는 파일, 명령 인자, 진단 로그에 저장하지 않았다.

### 3.2 테스트 작업 디렉터리

| 경로 | 현재 상태/내용 |
|---|---|
| `/opt/sso-lab-test` | 테스트 전용 디렉터리. 소유자 `today:today`, 현재 권한 `775`. 처음 생성 시 `750`이었으나 PC 재시작 후 재확인 시 `775`였으며 이후 임의로 되돌리지 않았다. |
| `/opt/sso-lab-test/.env.test` | 테스트 전용 Compose 환경 파일. 소유자 `today:today`, 권한 `600`, 실제 비밀번호 포함. Git에는 없고 Secret 값은 이 문서에서 redacted 처리한다. |
| `/opt/sso-lab-test/backend`, `frontend`, `gradle`, `infra` 및 root 설정 파일 | 로컬 Phase 1 소스 snapshot. Git push/pull 없이 tar/scp로 복사했다. |

소스 전송 시 로컬과 서버 아카이브 SHA-256이 모두 아래 값으로 일치했다.

```text
6fbee0525b35e1332df605a30272bde296935cddb9974de7f4ef2fa483ac9e59
```

복사에서 제외한 경로는 다음과 같다.

```text
.git
.env
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

현재 `.env.test`의 구성 형태는 다음과 같다. 실제 비밀번호는 표시하지 않는다.

```dotenv
COMPOSE_PROJECT_NAME=sso-lab-test
POSTGRES_DB=sso_lab_test
POSTGRES_USER=sso_lab_test
POSTGRES_PASSWORD=<REDACTED: user-specified test value>
AUTH_DB_URL=jdbc:postgresql://postgres:5432/sso_lab_test
SPRING_PROFILES_ACTIVE=prod
```

실제 `.env`와 `secrets/`는 `/opt/sso-lab-test`에 생성하지 않았다.

### 3.3 Docker 리소스

Compose project name은 `sso-lab-test`로 지정해 운영 리소스와 분리했다.

#### 컨테이너 및 이미지

| 서비스 | 이미지 | 검증 시 상태 | Host publish |
|---|---|---|---|
| postgres | `postgres:17-alpine` | healthy | 없음 (`5432/tcp`는 container expose만 존재) |
| auth-server | `sso-lab-test-auth-server:latest` | healthy | 없음 (`8080/tcp` expose만 존재) |
| admin-server | `sso-lab-test-admin-server:latest` | healthy | 없음 |
| hr-server | `sso-lab-test-hr-server:latest` | healthy | 없음 |
| approval-server | `sso-lab-test-approval-server:latest` | healthy | 없음 |
| auth-web | `sso-lab-test-auth-web:latest` | healthy | 없음 |
| admin-web | `sso-lab-test-admin-web:latest` | healthy | 없음 |
| hr-web | `sso-lab-test-hr-web:latest` | healthy | 없음 |
| approval-web | `sso-lab-test-approval-web:latest` | healthy | 없음 |

전체 컨테이너에서 Docker `HostConfig.PortBindings={}`를 확인했다.

#### Network

| Docker network | `internal` | 연결 컨테이너 | 목적 |
|---|---:|---:|---|
| `sso-lab-test_public-network` | false | 8 | Backend/Frontend 공개 계층 연결. PostgreSQL은 연결하지 않는다. |
| `sso-lab-test_internal-network` | true | 2 | auth-server와 admin-server 내부 연결. |
| `sso-lab-test_db-network` | true | 2 | auth-server와 PostgreSQL만 연결. |

실제 연결 검증에서 `auth-server -> postgres:5432`는 성공했고 `admin-server -> postgres:5432`는 실패했다.

#### Volume

| Docker volume | 목적 |
|---|---|
| `sso-lab-test_postgres-data` | 테스트 PostgreSQL 데이터 영속화. Host mountpoint는 `/var/lib/docker/volumes/sso-lab-test_postgres-data/_data`다. |

### 3.4 검증용 임시 서버 파일

Testcontainers를 host 사용자 권한으로 실행하기 위해 `/opt/sso-lab-test/.verification-runtime` 아래에 Java 21과 Gradle cache를 임시 준비했다. 테스트 완료 후 다음 항목을 경로 검증 뒤 삭제했다.

- `/opt/sso-lab-test/.verification-runtime` — 약 1.1GB, 삭제 완료
- `/opt/sso-lab-test/.gradle` — 삭제 완료
- `/opt/sso-lab-test/backend/auth-server/build` — 테스트 결과 확인 후 삭제 완료
- `/opt/sso-lab-test/build` — configuration-cache report 확인 후 삭제 완료
- `/tmp/sso-lab-phase1-source-20260820.tar` — source extract 후 삭제 완료

위 항목은 재생성 가능한 검증 산출물이며 삭제본은 복구되지 않는다. 실행 중인 Compose 컨테이너, image, network, PostgreSQL volume 및 `.env.test`는 유지했다.

## 4. 변경하지 않은 서버 보호 대상

다음 대상은 작업 내내 변경하지 않았다.

| 대상 | 확인 내용 |
|---|---|
| `/opt/sso-lab` | 실제 배포 경로. inode `1839623`, 확인된 수정 시각 `2026-08-20 12:49:39.043470722 +0900`가 작업 전후 동일했다. |
| `/opt/sso-lab/.env` 또는 기존 운영 `.env` | 읽거나 수정하거나 복사하지 않았다. |
| `/opt/sso-lab/secrets/` 및 기존 운영 secrets | 생성·수정·삭제하지 않았다. |
| UFW | 변경하지 않았다. |
| Ubuntu host network 설정 | 변경하지 않았다. |
| 운영 Docker Compose/volume/container | 변경하지 않았다. 테스트 project `sso-lab-test`만 사용했다. |
| 운영 DB 비밀번호 | 변경하지 않았다. 사용자가 지정한 값은 `/opt/sso-lab-test/.env.test`에만 적용했다. |

## 5. 수행한 검증과 결과

| 검증 항목 | 결과 |
|---|---|
| 전체 애플리케이션 Docker image build | Backend 4개, Frontend 4개 모두 성공 |
| PostgreSQL container 기동 | `postgres:17-alpine`, PostgreSQL 17.11, healthy |
| Flyway 실제 적용 | `auth` schema 생성, V1 성공, history의 `success=true` 확인 |
| DB 테이블 | `auth.flyway_schema_history`, `auth.spring_session`, `auth.spring_session_attributes` 확인 |
| `AuthServerPostgresqlIntegrationTest` | tests=1, skipped=0, failures=0, errors=0 |
| 전체 `docker compose up -d --wait` | 9개 서비스 모두 성공 |
| Backend health | auth/admin/hr/approval 모두 Actuator `status=UP` |
| Frontend smoke | auth/admin/hr/approval 모두 container 내부 HTTP에서 production HTML 응답 |
| Docker network | public/internal/db 연결이 Compose 정의와 일치 |
| DB network 격리 | auth 접근 성공, admin 접근 차단 |
| 불필요한 port publish | 모든 컨테이너에서 0개 |
| 제외 경로 최종 검사 | `.git`, `.env`, `secrets`, `node_modules`, `build`, `dist`, `logs` 모두 0개 |

Docker 검증 과정에서 Phase 1 애플리케이션 코드 결함은 발견되지 않아 로컬 소스 추가 수정은 없었다.

## 6. 현재 상태와 후속 작업

- `/opt/sso-lab-test`의 Compose 9개 서비스는 이 문서 작성 시점에 모두 실행 중이며 healthy다.
- 테스트 PostgreSQL volume은 유지돼 Flyway 적용 결과가 남아 있다.
- 로컬 Repository는 아직 commit되지 않은 Phase 1 변경 상태다.
- Phase 2 기능 구현은 시작하지 않았다.
- 다음 Phase는 사용자의 명시적 요청 후 `AI_AGENT_START_HERE.md`의 순서에 따라 진행해야 한다.
- 테스트와 운영에서 같은 비밀번호를 재사용하지 않는 것이 안전하다. 운영 변경이 필요하면 별도의 강한 고유 Secret으로 명시적으로 작업해야 한다.

## 7. 나중에 변경 파일을 찾는 명령

로컬 Git 변경 전체:

```powershell
git -c safe.directory=C:/dev/sso-lab status --short -uall
git -c safe.directory=C:/dev/sso-lab diff
```

Phase 1 Backend 파일:

```powershell
rg --files C:\dev\sso-lab\backend
```

Phase 1 Frontend 파일:

```powershell
rg --files C:\dev\sso-lab\frontend -g '!node_modules/**' -g '!dist/**'
```

서버 테스트 서비스 상태:

```bash
cd /opt/sso-lab-test
docker compose --env-file .env.test ps
docker compose --env-file .env.test images
```

서버 테스트 network와 port binding:

```bash
docker network inspect sso-lab-test_public-network
docker network inspect sso-lab-test_internal-network
docker network inspect sso-lab-test_db-network
docker inspect sso-lab-test-auth-server-1 --format '{{json .HostConfig.PortBindings}}'
```

> 주의: `.env.test`, 실제 `.env`, `secrets/`, SSH 개인 키를 `Get-Content`, `cat`, 로그 수집 또는 Git 명령으로 출력하지 않는다.
