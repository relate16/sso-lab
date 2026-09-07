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

## 3. Frontend 설치와 실행

처음 실행하거나 lock file이 변경되었을 때 각 디렉터리에서 의존성을 설치합니다.

```powershell
cd frontend/auth-web; npm ci
cd ../admin-web; npm ci
cd ../hr-web; npm ci
cd ../approval-web; npm ci
```

그 후 터미널 네 개를 열고 각각 실행합니다.

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

CSRF cookie와 header는 같은 Vite origin을 통해 그대로 왕복합니다. 보호 대상 POST는 CSRF token이 있어야 성공하고 token이 없으면 기존 Spring Security 정책에 의해 계속 거부됩니다.

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

## 6. 검증

각 Frontend에서 다음을 실행합니다.

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

## 7. 종료

각 Vite terminal에서 `Ctrl+C`를 누른 뒤 Backend를 종료합니다.

```powershell
docker compose --env-file .env --env-file .env.frontend.local `
  -f docker-compose.yml -f docker-compose.local.yml down
```

데이터 삭제가 목적이 아니라면 `down -v`를 사용하지 않습니다.
