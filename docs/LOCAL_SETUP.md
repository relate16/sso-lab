# Local Setup

이 절차는 네 Frontend를 Vite 개발 서버로 실행하고, Backend만 Docker Compose로 실행하는 로컬 개발 기준입니다. 실제 Gmail, Turnstile 또는 Production Secret은 사용하지 않습니다.

## 1. 도구와 로컬 설정

- JDK 21 (`java -version`)
- Node.js 24 LTS와 npm (`node --version`, `npm --version`)
- Docker Engine/Desktop와 Compose v2 (`docker version`, `docker compose version`)
- Git

Gradle 8.14.3은 Wrapper가 사용합니다. Windows에서는 `gradlew.bat`, Linux/macOS에서는 `./gradlew`를 사용합니다.

Repository root에서 예제 파일을 Git-ignored 로컬 파일로 복사합니다.

```powershell
Copy-Item .env.example .env
Copy-Item .env.frontend.local.example .env.frontend.local
```

```sh
cp .env.example .env
cp .env.frontend.local.example .env.frontend.local
```

`.env`에는 Docker Compose와 Backend용 로컬 설정 및 개발 전용 Secret을 넣습니다. `.env.frontend.local`은 네 Vite port와 loopback Backend target만 관리합니다. 두 파일 모두 Git 대상이 아니며 Production 값을 복사하지 않습니다.

Backend가 별도 승인된 공유 서버 DB를 사용해야 할 때만 `.env.backend.local.example`을 `.env.backend.local`로 복사합니다. 이 파일은 Git에서 제외되며 DB password와 모든 local-only Secret을 보관합니다. 공유 DB mode를 사용하지 않을 때에는 만들 필요가 없습니다.

기본 local/test 정책은 다음과 같습니다. `docker-compose.local.yml`이 이를 명시적으로 적용하며 CSRF는 비활성화하지 않습니다.

```text
SPRING_PROFILES_ACTIVE=test
SESSION_COOKIE_SECURE=false
TURNSTILE_ENABLED=false
GMAIL_SMTP_ENABLED=false
SSO_TEST_SUPPORT_ENABLED=false
```

키는 목적별로 독립된 로컬 테스트 값으로 준비합니다. OIDC private key는 Base64 PKCS#8 RSA DER, public key는 Base64 X.509 DER 형식입니다. 값을 terminal history나 문서에 출력하지 말고 `secrets/`에만 둡니다. 상세 형식은 [Secret Management](SECRET_MANAGEMENT.md)를 따릅니다.

## 2. Backend 실행

기본 Compose 파일은 변경하지 않습니다. 로컬에서는 `docker-compose.local.yml`을 함께 적용해 Backend 네 개만 host loopback에 공개합니다.

```powershell
docker compose --env-file .env --env-file .env.frontend.local `
  -f docker-compose.yml -f docker-compose.local.yml config

docker compose --env-file .env --env-file .env.frontend.local `
  -f docker-compose.yml -f docker-compose.local.yml `
  up -d --build --wait postgres auth-server admin-server hr-server approval-server
```

```sh
docker compose --env-file .env --env-file .env.frontend.local \
  -f docker-compose.yml -f docker-compose.local.yml config

docker compose --env-file .env --env-file .env.frontend.local \
  -f docker-compose.yml -f docker-compose.local.yml \
  up -d --build --wait postgres auth-server admin-server hr-server approval-server
```

기본 port mapping은 다음과 같습니다. 모두 `127.0.0.1`에만 bind되므로 LAN에서 직접 접근할 수 없습니다.

| Backend | Host endpoint | Container endpoint |
|---|---|---|
| Auth | `http://127.0.0.1:18080` | `auth-server:8080` |
| Admin | `http://127.0.0.1:18081` | `admin-server:8080` |
| HR | `http://127.0.0.1:18082` | `hr-server:8080` |
| Approval | `http://127.0.0.1:18083` | `approval-server:8080` |

PostgreSQL은 host에 publish하지 않습니다. Auth Server만 Identity DB에 직접 접근하는 서비스 경계도 그대로 유지됩니다.

### 선택 사항: SSH tunnel을 통한 공유 서버 DB mode

기본 개발 mode는 위의 격리된 local PostgreSQL입니다. 개인 시연 환경에서 기존 서버 PostgreSQL을 의도적으로 공유할 때에는 두 번째 PowerShell에서 tunnel을 먼저 엽니다.

```powershell
Copy-Item .env.backend.local.example .env.backend.local
./scripts/local/start-shared-db-tunnel.ps1
```

스크립트는 SSH hostname으로 접속해 현재 `sso-lab-postgres-1` container의 private bridge 주소를 동적으로 조회합니다. 주소를 파일에 저장하거나 하드코딩하지 않으며 `127.0.0.1:15432`만 listen합니다. 서버의 PostgreSQL 5432는 Host/Public interface에 publish하지 않습니다. PowerShell에서 `Ctrl+C`를 누르거나 SSH 연결이 끊기면 DB 접근도 종료됩니다.

`.env.backend.local`의 `POSTGRES_USER`, `POSTGRES_PASSWORD`와 DB 이름을 승인된 공유 DB credential에 맞추고, 나머지 `CHANGE_ME` 값은 Production과 독립된 local-only key/secret으로 교체합니다. Production secret 파일을 Windows로 복사하지 않습니다. 준비 후 다음과 같이 local Backend만 기동합니다.

```powershell
docker compose --env-file .env --env-file .env.frontend.local --env-file .env.backend.local `
  -f docker-compose.yml -f docker-compose.local.yml -f docker-compose.local-shared-db.yml config

docker compose --env-file .env --env-file .env.frontend.local --env-file .env.backend.local `
  -f docker-compose.yml -f docker-compose.local.yml -f docker-compose.local-shared-db.yml `
  up -d --build --wait auth-server admin-server hr-server approval-server
```

Docker Desktop 없이 Windows에서 빌드된 Spring Boot JAR를 직접 실행하려면 먼저
`./gradlew.bat --no-daemon clean build`를 완료한 뒤 별도의 PowerShell에서 다음 도우미를
사용합니다. 이 도우미도 같은 ignored env 파일을 읽고 네 Backend를
`127.0.0.1:18080`~`18083`에만 bind하며, Auth datasource의 tunnel host를
`127.0.0.1:15432`로 변환합니다.

```powershell
./scripts/local/shared-backends.ps1

# 종료
./scripts/local/shared-backends.ps1 -Stop
```

이 override가 적용되면 local PostgreSQL service는 시작되지 않고 Auth datasource는 Docker Desktop Host의 SSH tunnel(`host.docker.internal:15432`)만 사용합니다. Windows에서 Auth Server를 직접 `bootRun`할 때에는 같은 ignored env에서 JDBC host만 `127.0.0.1:15432`로 바꿀 수 있습니다.

`local-shared-db` profile은 Flyway를 강제로 비활성화하고 Hibernate를 `ddl-auto=validate`로 유지합니다. 또한 기동 최우선 안전 점검에서 다음 조건이 하나라도 다르면 Auth Server를 실패시킵니다.

- 사용자가 공유 DB write 위험을 명시적으로 승인했는지
- Flyway, Bootstrap Admin, Turnstile, Gmail 및 test-support가 비활성인지
- JDBC endpoint가 `127.0.0.1` 또는 `host.docker.internal` tunnel인지
- issuer와 redirect가 local loopback URL인지
- OAuth client ID가 Production 기본 ID가 아닌 `sso-local-*`인지

Auth Server는 local-only HR/Approval/Admin client row를 별도 ID로 upsert합니다. BFF의 registration ID와 callback path는 기존 `hr-client`, `approval-client`, `admin-client`를 유지하므로 local UI 경로는 변하지 않습니다. Production client row는 수정하지 않습니다.

공유 mode에서도 Identity DB 직접 접근은 Auth Server에만 있습니다. Admin/HR/Approval BFF에는 datasource, JPA 또는 Flyway가 추가되지 않습니다. 다만 Spring Session, local OIDC authorization/token, OTP challenge와 Audit은 같은 `auth` schema에 기록됩니다. 특히 사용자 기준 global logout 또는 session 관리 작업은 Production Session/token에도 영향을 줄 수 있으므로 공유 mode에서 destructive logout, 전체 session 삭제, signup, email 변경, TOTP 등록/해제 및 자동 E2E를 실행하지 않습니다.

Local-only crypto key로는 기존 Production 암호문을 복호화할 수 없습니다. 기존 사용자 Email OTP에는 Production email AES key가, email lookup에는 Production HMAC key가, 기존 TOTP 로그인에는 Production TOTP AES key가 각각 필요합니다. 이 문서는 해당 Production key를 복사하도록 권장하지 않습니다. 따라서 Production key를 별도로 안전하게 공급하도록 명시 승인받지 않은 공유 mode의 기본 검증 범위는 기동, health, CSRF, OIDC discovery와 비인증 redirect까지입니다. OTP HMAC, OIDC RSA/client secret 및 Internal API secret은 local-only 값으로 사용할 수 있습니다.

### Flyway migration 실행과 검증

기본 local DB mode에서는 Auth Server가 기동될 때 Flyway가 `auth` schema에 V1-V8 migration을 순서대로 자동 적용하고, Hibernate는 `ddl-auto=validate`로 결과 schema만 검증합니다. 기존 migration 파일을 수정하거나 H2 또는 `ddl-auto=update`로 대체하지 않습니다. 앞의 공유 서버 DB mode에서는 이 절차를 실행하지 않으며 Flyway가 반드시 비활성 상태여야 합니다.

기동 후 Auth health와 PostgreSQL의 Flyway 이력을 확인합니다. 아래 명령은 비밀번호 원문을 host 명령행에 넣지 않고 PostgreSQL container에 이미 전달된 환경변수를 사용합니다.

```sh
docker compose --env-file .env --env-file .env.frontend.local \
  -f docker-compose.yml -f docker-compose.local.yml \
  exec -T postgres sh -c 'PGPASSWORD="$POSTGRES_PASSWORD" psql --set=ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --command "SELECT installed_rank, version, description, success FROM auth.flyway_schema_history ORDER BY installed_rank;"'
```

V1-V8이 모두 한 번씩 존재하고 `success=true`인지 확인합니다. 실제 PostgreSQL 17 migration 회귀는 Repository root에서 `./gradlew --no-daemon clean build`(Windows는 `.\gradlew.bat --no-daemon clean build`)를 실행해 Testcontainers test까지 확인합니다. Docker가 없어서 Testcontainers가 실행되지 않았다면 migration 검증 완료로 간주하지 않습니다.

## 3. Frontend 설치와 실행

Repository root의 일괄 명령은 로컬 도구 의존성을 root `devDependency`로 관리하므로 먼저 한 번 설치합니다. 전역 npm package는 필요하지 않습니다.

```powershell
npm ci
```

처음 실행하거나 lock file이 변경되었을 때 각 디렉터리에서 의존성을 설치합니다.

```powershell
cd frontend/auth-web; npm ci
cd ../admin-web; npm ci
cd ../hr-web; npm ci
cd ../approval-web; npm ci
```

그 후 터미널 네 개를 열고 각각 실행합니다.

네 개발 서버를 한 terminal에서 동시에 실행하려면 Repository root에서 다음을 사용합니다. 로그에는 각각 `[auth]`, `[admin]`, `[hr]`, `[approval]` prefix가 붙으며 `Ctrl+C` 한 번으로 네 프로세스를 함께 종료합니다.

```powershell
npm run dev:all
```

개별 앱만 실행해야 할 때에는 기존 명령을 그대로 사용합니다.

```powershell
cd frontend/auth-web
npm run dev
```

```powershell
cd frontend/admin-web
npm run dev
```

```powershell
cd frontend/hr-web
npm run dev
```

```powershell
cd frontend/approval-web
npm run dev
```

| Frontend | 접속 URL | Vite proxy target |
|---|---|---|
| Auth Web | `http://127.0.0.1:5173` | Auth Server `127.0.0.1:18080` |
| Admin Web | `http://127.0.0.1:5174` | Admin Server `127.0.0.1:18081` |
| HR Web | `http://127.0.0.1:5175` | HR Server `127.0.0.1:18082` |
| Approval Web | `http://127.0.0.1:5176` | Approval Server `127.0.0.1:18083` |

정확한 OIDC redirect URI와 cookie 동작을 위해 브라우저에서도 `localhost` 대신 표의 `127.0.0.1` URL을 사용합니다.

## 4. Vite proxy와 보안 경계

Frontend API 코드는 계속 `/api/...` 같은 상대경로만 사용합니다. Browser는 Vite와 same-origin으로 통신하고 Vite가 시작 시 확정한 Backend target으로 요청을 전달합니다. 따라서 localhost를 Backend CORS allow-list나 CSRF ignore 목록에 추가하지 않습니다.

명시적으로 허용하는 proxy path는 다음과 같습니다.

| Frontend | 허용 path |
|---|---|
| Auth Web | `/api/**`, `/.well-known/**`, `/oauth2/**`, `/userinfo`, `/connect/**`, `/error` |
| Admin/HR/Approval Web | `/api/**`, `/oauth2/**`, `/login/**`, `/logout`, `/error` |

`/internal`과 `/internal/**`는 Vite에서 404로 차단하며 Backend로 전달하지 않습니다. WebSocket proxy는 구성하지 않습니다. Vite와 Backend port는 모두 loopback-only이고 Vite는 `strictPort: true`이므로 지정 port가 사용 중이면 다른 port로 자동 변경되지 않습니다.

Proxy target이 `127.0.0.1`, `localhost`, `*.localhost` 또는 `::1`이면 기존 direct local 개발 동작을 유지하도록 upstream origin을 변경하지 않습니다. 그 외의 원격 target은 hostname 기반 HTTPS virtual host가 정확한 Host/SNI를 받도록 Vite가 검증된 고정 target origin으로만 `changeOrigin`을 적용합니다. TLS 인증서 검증은 두 경우 모두 유지하며 임의 요청 값으로 target이나 origin을 바꿀 수 없습니다.

CSRF cookie와 header는 같은 Vite origin을 통해 그대로 왕복합니다. 보호 대상 POST는 CSRF token이 있어야 성공하고 token이 없으면 기존 Spring Security 정책에 의해 계속 거부됩니다.

### 원격 Production target의 범위

`SSO_LOCAL_*_SERVER_URL`에 HTTPS Production hostname을 지정하면 Vite proxy를 통한 API 연결과 TLS 검증은 가능합니다. 그러나 `127.0.0.1`의 서로 다른 port는 cookie 관점에서 서로 다른 host가 아닙니다. Production의 Admin/HR/Approval BFF는 각 hostname으로 격리된 동일 이름의 `JSESSIONID`를 사용하므로, 세 BFF를 모두 `127.0.0.1` port로 proxy하면 브라우저에서 BFF Session cookie가 서로 덮어써집니다.

또한 Production OIDC client의 exact redirect URI와 post-logout redirect URI는 Production HTTPS hostname으로 등록되어 있습니다. 따라서 로컬 BFF에서 시작한 authorization 요청도 callback과 logout 완료 후에는 Production hostname으로 돌아가며, 로컬 Vite origin에 인증된 BFF Session을 만들지 않습니다. Production Turnstile도 Production Auth Web이 runtime에 주입하는 Site Key와 등록 hostname을 사용하므로 로컬 Auth Web의 기본 개발 설정만으로 실제 Production Email OTP 요청을 완료할 수 없습니다.

이 제약을 피하려고 localhost를 CORS/CSRF allow-list에 추가하거나, exact redirect URI를 완화하거나, Production cookie 보안을 낮추지 않습니다. 전체 signup/login/SSO/logout 개발은 이 문서의 `docker-compose.local.yml` 구성을 사용하고, 원격 Production target은 비파괴 API 연결 진단에만 사용합니다. Production 인증 흐름 검증은 Production HTTPS Frontend에서 수행합니다.

## 5. 로컬 OIDC/SSO URL

`docker-compose.local.yml`은 다음 exact URL을 Auth Server와 각 BFF에 함께 적용합니다.

```text
AUTH_PUBLIC_URL=http://127.0.0.1:5173
HR_REDIRECT_URI=http://127.0.0.1:5175/login/oauth2/code/hr-client
ADMIN_REDIRECT_URI=http://127.0.0.1:5174/login/oauth2/code/admin-client
APPROVAL_REDIRECT_URI=http://127.0.0.1:5176/login/oauth2/code/approval-client
HR_POST_LOGOUT_REDIRECT_URI=http://127.0.0.1:5175/
ADMIN_POST_LOGOUT_REDIRECT_URI=http://127.0.0.1:5174/
APPROVAL_POST_LOGOUT_REDIRECT_URI=http://127.0.0.1:5176/
```

네 앱이 같은 host를 사용해도 Session이 충돌하지 않도록 Auth는 기존 `SESSION`, 각 BFF는 `SSO_LAB_ADMIN_SESSION`, `SSO_LAB_HR_SESSION`, `SSO_LAB_APPROVAL_SESSION`을 사용합니다. Authorization Code + PKCE, exact redirect URI, 서버 측 token 저장, CSRF 정책은 유지됩니다.

## 6. 최초 사용자와 Bootstrap Admin

Bootstrap Admin은 관리자 row를 SQL로 직접 삽입하는 기능이 아니라, 검증된 지정 email로 최초 signup을 완료한 한 계정에만 `USER`와 `ADMIN` Role을 부여하는 one-shot claim입니다. Frontend는 Role 또는 Bootstrap 값을 전송하지 않으며 Auth Server가 서버 설정과 DB claim 상태를 판정합니다.

로컬에서 이 흐름을 확인할 때만 Git-ignored `.env`에 다음 값을 설정합니다. 실제 개인정보나 운영 email을 사용하지 않습니다.

```text
BOOTSTRAP_ADMIN_ENABLED=true
BOOTSTRAP_ADMIN_EMAIL=<local test email>
```

Auth Web의 회원가입 화면에서 설정한 email로 Email OTP signup을 완료하고, Admin Web OIDC 로그인과 Admin API 접근을 확인합니다. claim이 소비된 뒤에는 `BOOTSTRAP_ADMIN_ENABLED=false`로 되돌리고 Auth Server를 재생성합니다. 같은 flag를 다시 켜거나 서버를 재시작해도 이미 소비된 claim으로 추가 ADMIN이 생성되지 않습니다. DB 직접 수정으로 claim을 초기화하지 않으며 자세한 정책은 [Bootstrap Admin Guide](BOOTSTRAP_ADMIN_GUIDE.md)를 따릅니다.

Local/Test의 mail sender는 메모리 sink이며 OTP를 log에 출력하지 않습니다. OTP가 필요한 자동 검증은 아래 격리 E2E를 사용합니다. 수동 개발을 위해 test-support를 사용해야 한다면 `test` profile에서만 Git-ignored `.env`의 `SSO_TEST_SUPPORT_ENABLED=true`와 16자 이상의 로컬 전용 `TEST_SUPPORT_API_KEY`를 명시적으로 설정합니다. 기본값은 계속 `false`이며 Production에서는 절대 활성화하지 않습니다.

## 7. TOTP 로컬 검증

로그인한 Auth Web의 `내 정보`에서 `TOTP 등록`을 시작하고 다음을 확인합니다.

1. `otpauthUri` QR은 Auth Web의 로컬 SVG renderer로만 표시되며 외부 QR service로 전송되지 않습니다.
2. Authenticator에 QR을 등록하고 현재 6자리 code로 enrollment를 확정합니다.
3. Recovery Code 10개는 한 번만 표시되며 Browser Storage나 log에 남지 않습니다.
4. 로그아웃 후 TOTP 로그인이 성공하는지 확인합니다.
5. Recovery Code 재발급과 TOTP 해제에는 fresh re-authentication 및 CSRF가 계속 적용되는지 확인합니다.
6. TOTP 해제 후 credential과 Recovery Code가 무효화되고 `TOTP_DISABLED` Audit이 민감값 없이 기록되는지 확인합니다.

Test Mail Sink, Turnstile test double 및 controllable TOTP clock을 사용하는 전체 자동 검증은 운영 자원과 분리된 Linux/Docker 환경에서 실행합니다.

```sh
scripts/test/run-phase9-e2e.sh
```

이 스크립트의 격리 구성과 보안 조건은 [Test Guide](TEST_GUIDE.md)를 따릅니다. 실제 Gmail, 실제 Turnstile 또는 Production Secret을 로컬 검증에 사용하지 않습니다.

## 8. 검증

Repository root에서 네 Frontend의 test, TypeScript lint와 Production build를 병렬로 실행할 수 있습니다. 각 출력은 앱별 prefix로 구분되고 하나라도 실패하면 root 명령도 실패합니다.

```powershell
npm run test:all
npm run lint:all
npm run build:all
```

개별 Frontend 검증 명령도 그대로 유지됩니다. 각 앱 디렉터리에서 다음을 실행합니다.

```sh
npm test
npm run lint
npm run build
```

실제 Vite proxy, 지정 port, CSRF 전달/거부, `/internal/**` 차단 및 loopback bind 검증은 root에서 실행합니다.

```sh
node scripts/test/verify-local-vite-proxy.mjs
```

`npm run build`는 `.env.frontend.local`을 읽지 않으므로 개발 Backend target이 Production bundle에 포함되지 않습니다. Production Dockerfile, Caddy, Spring Security 및 Production `.env`는 이 로컬 구성의 영향을 받지 않습니다.

## 9. 종료

각 Vite terminal에서 `Ctrl+C`를 누른 뒤 Backend를 종료합니다.

```powershell
docker compose --env-file .env --env-file .env.frontend.local `
  -f docker-compose.yml -f docker-compose.local.yml down
```

공유 서버 DB mode를 종료할 때에는 동일한 세 파일을 지정하되 volume을 삭제하지 않습니다. 그 후 tunnel PowerShell에서 `Ctrl+C`를 눌러 DB 접근을 닫습니다.

```powershell
docker compose --env-file .env --env-file .env.frontend.local --env-file .env.backend.local `
  -f docker-compose.yml -f docker-compose.local.yml -f docker-compose.local-shared-db.yml down
```

데이터 삭제가 목적이 아니라면 `down -v`를 사용하지 않습니다.
