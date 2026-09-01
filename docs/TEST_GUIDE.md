# Test Guide

자동 테스트는 운영 DB/Secret, 실제 Gmail, 실제 Turnstile endpoint를 사용하지 않습니다.

## Backend Unit / Integration / Testcontainers

```powershell
.\gradlew.bat clean test
```

Docker가 없으면 Testcontainers test가 skip될 수 있으므로 완료 판정에는 Docker가 있는 Linux/CI에서 같은 명령을 다시 실행하고 XML의 failure/error/skip 수를 확인합니다. PostgreSQL 17 Testcontainers는 Flyway V1-V7, FK/cascade, repository, OIDC, logout, admin과 self-service를 실제 DB에서 검증합니다. H2로 대체하지 않습니다.

주요 suite:

- Identity/Crypto/System Config domain·service unit
- Passwordless OTP/TOTP/Recovery/Session PostgreSQL integration
- Authorization Code + PKCE, token rotation, exact URI, UserInfo/claim integration
- Admin authorization/re-auth/audit integration
- RP/Back-Channel/Global Logout integration
- Profile/username/email 변경/Hard Delete PostgreSQL integration
- Rate limit, Turnstile, enumeration, log redaction security test

## Frontend

각 Web 디렉터리에서:

```sh
npm ci
npm test
npm run lint
npm run build
```

Node 내장 test runner로 API adapter의 credential 포함, CSRF header, empty response와 오류 처리를 검증하고 TypeScript/Vite production build를 수행합니다.

## OpenAPI

```sh
python3 scripts/test/validate-openapi.py
```

`docs/openapi`의 Auth public/internal, Admin, HR, Approval OpenAPI 3.1 문서가 JSON으로 parse되고 필수 operation, public/internal 분리, 민감 예시 금지를 검사합니다. Production Caddy는 Auth Internal Admin endpoint와 문서를 외부에 노출하지 않습니다.

## 격리 Browser E2E

Linux와 Docker에서:

```sh
scripts/test/run-phase9-e2e.sh
```

스크립트는 별도 Compose project, PostgreSQL volume, network, 임시 0600 Secret과 Playwright `1.62.0` image를 사용하고 종료 시 격리 자원을 정리합니다. `/opt/sso-lab`, 운영 volume/Secret 및 실제 외부 서비스를 사용하지 않습니다.

Test support는 `test` profile에서만 bean/endpoint가 생기고 임시 header key가 필요합니다.

- Test Mail Sink: challenge별 OTP를 메모리로 보관하며 application log에 쓰지 않음
- Test Turnstile: disabled flag/test double 또는 Cloudflare 공식 공개 dummy key만 사용
- TOTP Test Clock: test endpoint로 clock을 고정/이동하여 window와 만료를 재현

핵심 E2E:

1. Email OTP signup → HR 로그인 → Approval SSO → central/back-channel logout
2. controllable clock TOTP 로그인 → HR/Approval SSO
3. Profile username 변경 → email 변경과 Session/Refresh/BFF 폐기 → fresh re-auth Hard Delete
4. Admin login → 일반 USER 403 → suspend/resume → email re-auth reveal/remask

Assert는 `amr=email_otp|totp`, `acr=urn:jb:loa:1`, 새 `preferred_username`, Session 폐기와 API authorization을 확인합니다. Token/OTP/Secret 원문을 report에 첨부하지 않습니다.

## Compose / Caddy 회귀

```sh
docker compose --env-file <isolated-test-env> config
docker compose --env-file <isolated-test-env> up -d --build --wait
docker compose ps
```

검증 항목은 모든 health, Caddy routing, `/internal/**` 차단, Backend/PostgreSQL/Caddy Admin host publish 없음, internal network, trusted proxy, security header입니다. Production compose는 실제 `.env`/Secret 없이 placeholder test fixture로 render만 하며 운영을 변경하지 않습니다.

## GitHub Actions

CI는 Backend `./gradlew --no-daemon clean build`, 네 Frontend test/lint/build, OpenAPI validator와 Compose/Caddy 검증을 수행합니다. Release workflow는 test와 별개이며 immutable tag를 publish합니다. Production deploy는 protected environment 승인과 deploy-only workflow가 필요합니다.

## 최종 감사

변경 공유 전 최소:

```sh
git diff --check
git status --short
git grep -n -I -E 'localStorage|sessionStorage|indexedDB'
git grep -n -I -E 'BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY|ghp_|github_pat_'
```

추가로 실제 email/PII, `.env*`, `secrets/`, PEM/certificate, DB password, Turnstile/Gmail/OIDC/client/internal Secret, token/OTP log, build/cache/IDE/log/temp 후보를 검사합니다. `.env.example`의 `CHANGE_ME`, `example.invalid`, 테스트 전용 random fixture와 Cloudflare 공식 공개 test key는 운영 Secret과 구분합니다. 검색 결과에 실제 값이 나타나면 복사하지 말고 즉시 commit을 중단합니다.
