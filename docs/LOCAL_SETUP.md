# Local Setup

이 절차는 실제 Gmail, 실제 Turnstile 및 운영 Secret 없이 개발/검증하는 기준입니다.

## 1. 도구

- JDK 21 (`java -version`)
- Node.js 24 LTS와 npm (`node --version`, `npm --version`)
- Docker Engine/Desktop와 Compose v2 (`docker version`, `docker compose version`)
- Git과 Python 3

Gradle 8.14.3은 Wrapper가 사용합니다. Windows에서는 `gradlew.bat`, Linux/macOS에서는 `./gradlew`를 사용합니다.

## 2. 환경과 Secret

`.env.example`을 Git-ignored `.env`로 복사하고 `CHANGE_ME`를 로컬 값으로 교체합니다. localhost 기본 URL은 Auth 8080, HR 8081, Approval 8082, Admin 8083입니다. hosts 파일을 쓰는 경우 네 이름을 명시적으로 127.0.0.1에 연결하되 운영 DuckDNS 이름은 재사용하지 않습니다.

Local/Test에서는 다음을 사용합니다.

```text
SPRING_PROFILES_ACTIVE=test
SESSION_COOKIE_SECURE=false
TURNSTILE_ENABLED=false
GMAIL_SMTP_ENABLED=false
```

운영 profile에 이 값을 적용하지 않습니다. 키는 목적별로 독립된 32-byte random 값을 Base64로 준비합니다. OIDC private key는 Base64 PKCS#8 RSA DER, public key는 Base64 X.509 DER 형식입니다. 값을 terminal history나 문서에 출력하지 말고 `secrets/`를 0700, 파일을 0600으로 둡니다. 파일명과 형식은 [Secret Management](SECRET_MANAGEMENT.md)와 [Phase 8 Infra](PHASE8_INFRA.md)를 따릅니다.

## 3. Build와 migration

```powershell
.\gradlew.bat clean test
```

```sh
docker compose --env-file .env config
docker compose --env-file .env up -d --build --wait
docker compose --env-file .env ps
```

Auth가 기동되면 Flyway V1-V7이 빈 PostgreSQL에 자동 적용됩니다. Production과 같은 PostgreSQL 17을 사용하며 H2나 `ddl-auto=update`로 대체하지 않습니다. `auth.flyway_schema_history`와 Auth `/actuator/health`를 확인합니다.

## 4. Frontend

각 `frontend/auth-web`, `admin-web`, `hr-web`, `approval-web`에서 다음을 실행합니다.

```sh
npm ci
npm test
npm run lint
npm run build
```

## 5. 최초 사용자와 Bootstrap Admin

일반 signup은 Auth Web에서 userId, username, email을 제출하고 test mail adapter가 받은 OTP로 완료합니다. 실제 OTP를 log에 출력하는 adapter는 사용하지 않습니다.

최초 Admin이 필요하면 `.env`의 `BOOTSTRAP_ADMIN_ENABLED=true`와 `BOOTSTRAP_ADMIN_EMAIL=<local test address>`를 의도적으로 설정합니다. 그 email의 signup이 성공하면 one-shot claim이 소비됩니다. 이후 flag를 false로 되돌립니다. 상세 정책은 [Bootstrap Admin Guide](BOOTSTRAP_ADMIN_GUIDE.md)를 따릅니다.

## 6. TOTP와 SSO 확인

1. Auth Profile에서 TOTP enrollment를 시작합니다.
2. QR/otpauth secret을 authenticator에 등록하고 현재 code로 확정합니다.
3. Recovery Code는 한 번만 표시되므로 안전하게 보관하고 일반 로그인에 사용하지 않습니다.
4. HR에서 로그인한 뒤 Approval을 열어 추가 OTP 없이 SSO되는지 확인합니다.
5. 두 BFF `/api/v1/session`에서 `sub`, `preferred_username`, `amr`, `acr`을 확인하되 token 원문은 노출되지 않아야 합니다.
6. Global Logout 후 두 BFF Session이 모두 인증되지 않는지 확인합니다.

자동화된 전체 흐름은 [Test Guide](TEST_GUIDE.md)의 격리 E2E를 사용합니다.

## 7. Gmail과 Turnstile

Local/Test의 기본은 fake mail과 Turnstile disabled/test double입니다. 실제 Gmail App Password나 Turnstile Secret을 요구하지 않습니다. 운영 준비 시에만 [Gmail Setup](GMAIL_SMTP_SETUP.md)과 [Turnstile Setup](TURNSTILE_SETUP.md)을 따릅니다.

## 8. 종료

```sh
docker compose --env-file .env down
```

데이터 삭제가 목적이 아니라면 `down -v`를 사용하지 않습니다.
