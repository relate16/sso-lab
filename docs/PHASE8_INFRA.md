# Phase 8 Infrastructure

이 문서는 Phase 8에서 구현하고 Production에 적용한 운영 인프라 구조, 배포 절차와
검증 결과를 정리한다. 실제 Secret 값은 기록하지 않으며 운영 변경은 승인된
deploy-only workflow를 통해서만 수행한다.

## 1. 공개 경계

```text
Internet
   |
   | TCP 80 / TCP+UDP 443
   v
Caddy (public-network, fixed IP)
   |-- auth hostname     -> auth-web / auth-server
   |-- admin hostname    -> admin-web / admin-server
   |-- hr hostname       -> hr-web / hr-server
   `-- approval hostname -> approval-web / approval-server

auth-server --------------------------> PostgreSQL
              internal/db networks
admin-server -> auth-server internal API
```

- 운영에서 Host에 publish되는 포트는 Caddy의 `80/tcp`, `443/tcp`,
  `443/udp`뿐이다.
- Backend `8080`, Frontend `8080`, PostgreSQL `5432`는 `expose`만 하며
  Host에는 publish하지 않는다.
- Caddy는 `public-network`에만 참여한다. PostgreSQL은 internal인
  `db-network`에만 참여한다.
- `/internal` 및 `/internal/**`는 Caddy에서 404로 차단한다.
- Identity DB 접근 코드는 계속 `auth-server`에만 존재한다.

## 2. Domain과 URL

운영은 다음 네 개의 서로 다른 DuckDNS hostname을 사용한다.

| 환경 변수 | Production 값 | 역할 |
|---|---|---|
| `AUTH_HOSTNAME` | `today-sso-auth.duckdns.org` | Auth Web, Passwordless API, OIDC Provider |
| `ADMIN_HOSTNAME` | `today-sso-admin.duckdns.org` | Admin Web/BFF |
| `HR_HOSTNAME` | `today-sso-hr.duckdns.org` | HR Web/BFF |
| `APPROVAL_HOSTNAME` | `today-sso-approval.duckdns.org` | Approval Web/BFF |

`today-sso.duckdns.org`는 SSH/서버 접근용이며 애플리케이션 service hostname으로
사용하지 않는다. 공인 IPv4는 DHCP로 변경될 수 있으므로 설정과 문서에서 IP를
서비스 식별자로 하드코딩하지 않는다.

Production URL은 다음 관계를 정확히 유지한다.

- `AUTH_PUBLIC_URL=https://${AUTH_HOSTNAME}`
- `ADMIN_PUBLIC_URL=https://${ADMIN_HOSTNAME}`
- `HR_PUBLIC_URL=https://${HR_HOSTNAME}`
- `APPROVAL_PUBLIC_URL=https://${APPROVAL_HOSTNAME}`
- Redirect URI는 각 BFF의
  `https://<hostname>/login/oauth2/code/<client-id>`
- Post logout Redirect URI는 `https://<hostname>/`

와일드카드는 허용하지 않는다. OIDC issuer, discovery, ID Token `iss`,
redirect URI 및 post logout redirect URI가 모두 위 HTTPS URL과 정확히
일치해야 한다.

## 3. TLS

운영 Caddyfile은 명시적인 self-signed 인증서나 검증 우회를 사용하지 않는다.
Caddy Automatic HTTPS/ACME로 네 hostname의 Let's Encrypt 인증서를 발급했고 HTTP를
HTTPS로 전환한다. 인증서 chain과 hostname SAN을 실제 외부 경로에서 검증했다.
인증서와 Caddy 상태는 named volume `caddy-data`, `caddy-config`에 보존한다.

테스트용 `Caddyfile.test`만 `local_certs`를 사용한다. 또한 테스트 Compose는
80/443을 `127.0.0.1`에만 bind하므로 VM 외부에 테스트 인증서를 공개하지 않는다.

## 4. Forwarded Header 신뢰 경계

Caddy는 인터넷과 직접 연결되는 유일한 edge proxy다. 현재 구성에서는 Caddy
앞에 신뢰할 다른 CDN/LB가 없다.

1. Caddy는 기본적으로 들어온 `X-Forwarded-For` 값을 신뢰하지 않는다.
2. upstream 전달 전에 `Forwarded`와 `X-Real-IP`를 제거한다.
3. Caddy가 직접 산출한 `X-Forwarded-For`, `X-Forwarded-Proto`,
   `X-Forwarded-Host`만 애플리케이션에 전달한다.
4. Caddy에는 Docker `public-network`의 고정 IP를 할당한다.
5. Spring/Tomcat은 `TRUSTED_PROXY_CIDR`의 Caddy `/32`만 internal proxy로
   신뢰한다. 전체 Docker 사설 대역을 신뢰하지 않는다.
6. Rate Limit과 Turnstile remote IP는 Header를 직접 읽지 않고 Tomcat이
   검증해 확정한 `request.getRemoteAddr()`를 사용한다.

Caddy 앞에 Cloudflare Proxy 또는 별도 load balancer를 추가하려면 이 모델을
그대로 사용할 수 없다. 해당 proxy의 공식 CIDR 관리, Caddy `trusted_proxies`,
`trusted_proxies_strict` 및 헤더 처리 정책을 별도 보안 변경으로 검토해야 한다.

## 5. Production Secret 공급

실제 Secret 디렉터리는 Git에서 제외된 `${PRODUCTION_SECRET_DIR}`이고 기본값은
`./secrets`이다. 파일 권한은 디렉터리 `0700`, 파일 `0600`을 권장한다.
Docker Compose의 file-backed Secret은 Host 파일의 numeric UID/GID를 그대로 bind
mount한다. 따라서 Production Backend는 `.env`의 `BACKEND_RUNTIME_UID`와
`BACKEND_RUNTIME_GID`로 실행하며 두 값은 모든 Secret 파일의 `stat -c '%u:%g'`와
일치해야 한다. UID/GID는 0이 아닌 전용 계정 값이어야 하며 권한을 `0644` 등으로
완화해서 해결하지 않는다.

| Docker Secret 파일 | 형식/소비자 |
|---|---|
| `email-encryption-key` | Base64 32-byte AES key / auth-server |
| `email-lookup-hmac-key` | Base64 독립 HMAC key / auth-server |
| `otp-hmac-key` | Base64 독립 OTP HMAC key / auth-server |
| `totp-encryption-key` | Base64 32-byte AES key / auth-server |
| `oidc-private-key` | Base64 PKCS#8 RSA private DER / auth-server |
| `oidc-public-key` | Base64 X.509 RSA public DER / auth-server |
| `hr-client-secret` | 독립 random client secret / auth-server, HR BFF |
| `approval-client-secret` | 독립 random client secret / auth-server, Approval BFF |
| `admin-client-secret` | 독립 random client secret / auth-server, Admin BFF |
| `admin-internal-api-secret` | 독립 internal API secret / auth/admin server |
| `turnstile-secret` | Cloudflare server-side secret / auth-server |
| `gmail-app-password` | Gmail app password / auth-server |

배포 전 `validate-phase8-production-secrets.py`가 Secret 원문을 출력하지 않고
권한/소유권, 대칭키 길이, OIDC private key의 PKCS#8 DER 형식, public key의
X.509 DER 형식 및 RSA key pair 일치를 검증한다. PKCS#1 private DER은 OpenSSL의
일반 RSA parser로 읽히더라도 Java `PKCS8EncodedKeySpec`과 호환되지 않으므로
배포 전에 거부한다.

Auth Server는 기존 `SecretProvider`의 `DOCKER_SECRET` 구현으로 파일을 읽는다.
BFF는 공통 `SecretFileValue`를 통해 mount된 파일만 읽으며 inline 값과 파일을
동시에 지정하면 시작을 거부한다. Secret 원문은 로그에 남기지 않는다.
PostgreSQL password는 Master가 허용한 ignored `.env`에서 공급하며 Compose
또는 소스에 하드코딩하지 않는다.

## 6. Gmail SMTP

운영 profile에서 `GMAIL_SMTP_ENABLED=true`일 때 Auth Server의 mail abstraction이
Gmail SMTP adapter를 선택한다. STARTTLS가 필수이고 기본 endpoint는
`smtp.gmail.com:587`이다. Gmail 계정, 검증된 From 주소 및 app password는
운영자가 준비한다. Local/Test에서는 adapter가 비활성화되어 실제 메일을
발송하지 않는다.

Turnstile의 공개 Site Key는 `auth-web` container 시작 시 runtime config로 생성한다.
Production image에는 Site Key를 굽지 않으며 `TURNSTILE_ENABLED`와
`TURNSTILE_SITE_KEY`를 Auth Server/Auth Web에 함께 전달해 활성화 상태가 어긋나지
않게 한다. 서버 전용 `turnstile-secret`은 계속 Auth Server에만 mount한다.

## 7. Compose 실행 모델

운영은 두 파일을 합성한다.

```sh
docker compose --env-file .env \
  -f docker-compose.yml -f docker-compose.prod.yml config --quiet
docker compose --env-file .env \
  -f docker-compose.yml -f docker-compose.prod.yml pull
docker compose --env-file .env \
  -f docker-compose.yml -f docker-compose.prod.yml up -d --wait --no-build
```

`IMAGE_TAG`는 `sha-<40 hex commit>` 또는 별도 immutable release tag를 사용하고
`latest`는 사용하지 않는다. 운영 시작 전 `docker compose config` 출력에 Secret
원문이 나타나지 않는지 운영자가 확인한다.

테스트는 `.env.test`, `.env.phase4.local`, `infra/test/phase8.env`를 순서대로
합성하고 `docker-compose.infra-test.yml`을 추가한다. 운영 Compose와 운영
Secret은 테스트에 사용하지 않는다.

## 8. CI/CD

- `.github/workflows/ci.yml`: Java 21 전체 Gradle Build/Test, Node 24 네 Frontend
  lint/build, Compose model 검증, 전체 image build를 수행한다.
- `.github/workflows/release-images.yml`: 수동 실행 시 기본 `sha-<commit>` 또는
  검증된 `vMAJOR.MINOR.PATCH` tag로 서비스별 image를 GHCR에 publish한다. 이미
  존재하는 tag는 덮어쓰지 않고 실패시켜 immutable release 규칙을 지키며 deploy는
  수행하지 않는다.
- `.github/workflows/deploy-existing-release.yml`: 입력된 기존 immutable tag의 8개
  manifest와 digest를 먼저 검증한다. `image_source_commit`은 기존 image provenance로
  기록하고, 운영 Compose/Caddy는 workflow를 실행한 `main`의 `GITHUB_SHA`를 exact
  checkout한다. build/push 단계 없이 protected `production` environment 승인을
  통과한 경우에만 운영 Compose를 실행하며 `latest`는 거부한다.
- deploy SSH host는 `today-sso.duckdns.org`, path는 `/opt/sso-lab`인지 workflow가
  검증한다. Host key는 `ssh-keyscan`으로 즉석 신뢰하지 않고
  `DEPLOY_KNOWN_HOSTS` secret의 pinned entry를 사용한다.

필요한 GitHub production environment 값:

- Variables: `DEPLOY_HOST=today-sso.duckdns.org`, `DEPLOY_USER=today`,
  `DEPLOY_PATH=/opt/sso-lab`,
  `DEPLOY_STATE_PATH=/home/today/.local/state/sso-lab`,
  `TEST_CADDY_CONTAINER=sso-lab-test-caddy-1`
- Secrets: `DEPLOY_SSH_PRIVATE_KEY`, `DEPLOY_KNOWN_HOSTS`
- Required reviewers: 운영 변경을 승인할 관리자

Workflow는 운영 서버의 `.env` 또는 Secret 파일을 생성·복사·출력하지 않으며 기존
서버 파일을 그대로 사용한다. 실행 전후 checksum만 서버의 권한 제한 state 경로에
보존한다. manifest 확인에는 workflow 기본 `GITHUB_TOKEN`의 `packages: read` 권한을
사용하므로 deploy SSH Secret과 GHCR credential의 책임도 분리된다.

## 9. Rollback

배포 전에 현재 source commit, image/container, volume/network 상태를 Git repository
밖의 `DEPLOY_STATE_PATH/<run-id>`에 보존한다. 실패 로그 역시 GitHub log가 아닌 이
권한 제한 경로에 남긴다. PostgreSQL 및 Caddy volume은 삭제하지 않는다.

애플리케이션 rollback은 직전 immutable tag와 image source commit으로 deploy-only
workflow를 다시 실행한다.

```sh
IMAGE_TAG=sha-<PREVIOUS_COMMIT> docker compose --env-file .env \
  -f docker-compose.yml -f docker-compose.prod.yml pull
IMAGE_TAG=sha-<PREVIOUS_COMMIT> docker compose --env-file .env \
  -f docker-compose.yml -f docker-compose.prod.yml up -d --wait --no-build
```

Caddy/TLS만 되돌릴 경우 Phase 8 적용 전 Compose 파일로 돌아가 Caddy를
중지하고 기존 서비스 상태를 복원한다. PostgreSQL volume은 삭제하지 않는다.
`docker compose down -v`, Secret 삭제, 방화벽/DNS 변경은 rollback 명령에
포함하지 않는다.

## 10. Phase 8 Production 완료 기록

검증일: 2026-08-28

Master Specification의 Phase 8 완료 범위인 Caddy, Domain, TLS, CI/CD, PC VM deploy를
다음과 같이 실제 Production에서 검증했다.

| 완료 기준 | Production 검증 결과 |
|---|---|
| Caddy / Reverse Proxy | Production Caddy healthy, 네 Frontend/BFF/Auth routing 정상, `/internal/**` 404 |
| Domain | Auth/Admin/HR/Approval DuckDNS hostname 네 개 모두 외부 HTTPS 200 |
| TLS | HTTP 308 HTTPS 전환, Let's Encrypt chain/SAN 검증 성공, 인증서 만료일 2026-11-26 |
| CI/CD | protected production environment를 사용하는 deploy-only workflow 성공 |
| PC VM deploy | PostgreSQL, Caddy, Backend/Frontend를 포함한 10개 container 모두 healthy |

Production source와 image provenance는 분리해 기록한다.

```text
deployment source: fad354887baf9a80ec7b7798e3dd1fe9f1835a4c
image source:      f7e6f4a590a5d76b248c7954bb5938cb8d3dfec2
image tag:         v1.0.0
```

Compose/Caddy/deploy 검증만 보완됐고 application binary는 변경되지 않았으므로 기존
`v1.0.0` image 8개를 rebuild하거나 overwrite하지 않고 그대로 재사용했다.

### 10.1 Secret runtime 계약

Docker Compose의 file-backed Secret은 Docker Swarm secret과 달리 Linux Host 파일을
read-only bind mount하고 numeric UID/GID를 그대로 유지한다. Host Secret은
`today:today` (`1000:1000`), mode `0600`이므로 Production의 Auth/Admin/HR/Approval
Backend도 명시적으로 non-root `1000:1000`으로 실행한다. 권한을 `0644`로 완화하지
않는다.

실제 Production inspect에서 Auth Server의 12개 Secret과 각 BFF client/internal
Secret이 `rw=false`, `readable=true`임을 확인했다.

OIDC signing key 형식은 다음을 강제한다.

```text
oidc-private-key = single-line Base64 PKCS#8 RSA private DER
oidc-public-key  = single-line Base64 X.509 SubjectPublicKeyInfo RSA DER
RSA key size     = 2048 bits 이상
```

기존 RSA key pair는 유지하면서 private key container만 PKCS#1 DER에서 PKCS#8 DER로
변환했다. 배포 preflight는 private/public key pair 일치, key size, Secret owner/mode와
대칭키 형식을 값 노출 없이 검사한다.

### 10.2 배포 후 검증 결과

- Production container 10개 모두 `running/healthy`
- Auth/Admin/HR/Approval Backend와 PostgreSQL/Caddy health 성공
- Caddy만 Host `80/tcp`, `443/tcp`, `443/udp` publish
- Backend `8080`, PostgreSQL `5432`, Caddy Admin `2019` Host 비공개
- 테스트 Caddy는 종료되어 Production 포트와 충돌하지 않음
- 네 hostname HTTPS 200, HTTP 308, TLS chain/SAN 성공
- OIDC discovery의 issuer/authorization/token/JWKS/UserInfo URL exact match
- JWKS RSA/RS256 key 및 `kid` 확인
- invalid client Token 요청과 invalid Bearer UserInfo 요청 401
- Production Backend 로그에서 Secret/PII/credential 원문 및 심각 오류 0건
- `.env`와 Production Secret checksum은 배포 전후 불변이며 Git ignore 유지
- 최초 실패 배포에서 보존된 PostgreSQL/Caddy named volume을 성공 배포가 재사용

Caddy의 정상 ACME 계정 등록 INFO 로그에는 ACME 연락처 이메일이 기록된다. 이는
Backend 개인정보/Secret 로그 노출은 아니지만, 엄격한 운영 로그 개인정보 정책에서는
별도 ACME 운영용 alias 사용을 고려한다.

위 검증을 기준으로 Phase 8 — Infra를 완료로 판정한다. Phase 9 — Test / Docs는 별도
계획 승인 전까지 시작하지 않는다.
