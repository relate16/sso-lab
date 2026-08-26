# Phase 8 — Infra 로컬 및 서버 변경 이력

작성일: 2026-08-24

로컬 저장소: `C:\dev\sso-lab`

Ubuntu VM 테스트 경로: `/opt/sso-lab-test`

이 문서는 Phase 8 구현 중 로컬 저장소와 테스트 전용 Ubuntu VM에서 변경하거나
검증한 내용을 추적한다. 실제 배포 경로 `/opt/sso-lab`, 운영 `.env`, 운영
`secrets/`, UFW, DDNS, SSH 및 VM 네트워크 설정은 변경하지 않았다.

## 1. 현재 상태

- Phase 1~7: 완료
- Phase 8: 코드/테스트 환경 검증 완료, 운영 적용 승인 대기
- Phase 9: 미착수

실제 서비스별 hostname, public DNS, 외부 80/443, Let's Encrypt certificate 및
운영 Compose 적용은 승인 전이므로 미적용/미검증 상태다.

## 2. 로컬 변경 파일과 내용

### Caddy / Compose

```text
infra/caddy/Caddyfile
infra/caddy/Caddyfile.test
infra/caddy/sites.caddy
docker-compose.yml
docker-compose.prod.yml
docker-compose.infra-test.yml
infra/test/phase8.env
infra/test/phase8-prod-config.env
.env.example
```

- 네 hostname의 same-origin Frontend/BFF/Auth routing을 추가했다.
- `/internal`과 `/internal/**`를 public Caddy route에서 404로 차단했다.
- 운영 Caddy만 80/tcp, 443/tcp, 443/udp를 publish한다.
- 테스트 Caddy는 VM loopback 127.0.0.1에만 publish한다.
- Backend/PostgreSQL은 Host port를 publish하지 않는다.
- 테스트는 Caddy internal CA, 운영은 Automatic HTTPS/ACME 구조다.
- Caddy 고정 IP는 production `172.30.10.254`, test `172.30.80.254`이며 Backend는
  해당 `/32`만 trusted proxy로 설정한다.

### Forwarded Header / Client IP

변경한 파일:

```text
backend/auth-server/src/main/resources/application.yml
backend/admin-server/src/main/resources/application.yml
backend/hr-server/src/main/resources/application.yml
backend/approval-server/src/main/resources/application.yml
backend/auth-server/src/test/java/com/ssolab/auth/security/ratelimit/SecurityRequestIdentifiersTest.java
```

Tomcat native forwarding을 사용하고 Caddy `/32`만 신뢰한다. Caddy는 외부에서 받은
`Forwarded`, `X-Real-IP`을 제거하고 자체 산출한 `X-Forwarded-*`만 전달한다.
Rate Limit은 Header를 직접 읽지 않고 container가 확정한 remote address를 사용한다.

### Production Secret / 공통 Secret 파일 로더

추가/변경한 파일:

```text
backend/shared-infrastructure/build.gradle.kts
backend/shared-infrastructure/src/main/java/com/ssolab/shared/secret/SecretFileValue.java
backend/admin-server/src/main/java/com/ssolab/admin/config/OidcBffProperties.java
backend/admin-server/src/main/java/com/ssolab/admin/internal/InternalAdminProperties.java
backend/hr-server/src/main/java/com/ssolab/hr/config/OidcBffProperties.java
backend/approval-server/src/main/java/com/ssolab/approval/config/OidcBffProperties.java
backend/*-server/build.gradle.kts
settings.gradle.kts
build.gradle.kts
```

Auth Server는 기존 Docker `SecretProvider`를 사용한다. BFF는 mount된 client/internal
Secret 파일을 공통 로더로 읽고 inline/file 동시 설정 또는 빈 파일을 거부한다.
Production override는 crypto/JWT/client/internal/Turnstile/Gmail Secret을 file-backed
Docker Secret으로 mount하고 오래된 inline 환경 값은 빈 값으로 덮어쓴다.

### Gmail SMTP adapter

```text
backend/auth-server/src/main/java/com/ssolab/auth/passwordless/mail/GmailSmtpProperties.java
backend/auth-server/src/main/java/com/ssolab/auth/passwordless/mail/GmailMailTransport.java
backend/auth-server/src/main/java/com/ssolab/auth/passwordless/mail/JavaMailGmailTransport.java
backend/auth-server/src/main/java/com/ssolab/auth/passwordless/mail/GmailSmtpVerificationMailSender.java
backend/auth-server/src/main/java/com/ssolab/auth/passwordless/mail/RuntimeMailConfiguration.java
backend/auth-server/src/test/java/com/ssolab/auth/passwordless/mail/GmailSmtpVerificationMailSenderTest.java
```

Production에서만 Gmail SMTP adapter를 선택하고 STARTTLS required와 timeout을 적용한다.
Local/Test에서는 실제 Gmail을 호출하지 않는다. 실제 계정/app password는 생성하거나
사용하지 않았다.

### CI/CD

```text
.github/workflows/ci.yml
.github/workflows/release-deploy.yml
```

- PR/main CI: Java 21 Gradle test, Testcontainers, Node 24 네 Frontend lint/build,
  Compose validate, Docker image build
- 수동 release: commit SHA immutable tag로 8개 image를 GHCR에 publish
- Production deploy: GitHub protected environment 승인, pinned SSH host key,
  hostname/path 검증, `pull`과 `up -d --wait --no-build`

### 문서 / 검증

```text
docs/PHASE8_INFRA.md
docs/DEPLOYMENT.md
docs/DOMAIN_CHANGE_GUIDE.md
docs/SECRET_MANAGEMENT.md
docs/GMAIL_SMTP_SETUP.md
scripts/test/verify-phase8-caddy.sh
md/AI_AGENT_START_HERE.md
.gitignore
```

START_HERE의 Phase 8 상태는 운영 적용 전이므로 완료가 아니라
`구현/테스트 완료, 운영 적용 승인 대기`로 기록했다. Phase 9는 시작하지 않았다.

## 3. 로컬 검증 결과

- Java 21 toolchain + Gradle 8.14.3: `clean build` 성공
- 로컬 Docker 부재로 PostgreSQL Testcontainers 17건: skip
- Auth/Admin/HR/Approval Unit test: 성공
- 네 Frontend Node 24 + TypeScript lint + Vite 8 production build: 성공
- generated Gradle cache `.gradle-codex`는 검증 후 삭제하고 ignore 규칙을 추가했다.

## 4. Ubuntu VM 변경 내용

변경한 범위:

- `/opt/sso-lab-test`에 제외 규칙을 적용해 로컬 최신 source 동기화
- 테스트 Compose container/image 재빌드 및 재생성
- 테스트 전용 Caddy container와 named volume 생성
- Testcontainers용 Java 21/Gradle cache Docker volume 생성
- 검증 도구 image `curlimages/curl:8.16.0`, `rhysd/actionlint:1.7.7` pull

동기화 제외/보존 대상:

```text
.git
.env 및 .env.*
secrets/
node_modules/
build/
dist/
logs/
.gradle/
```

테스트 종료 시 container는 10개 모두 healthy 상태로 유지했다. Caddy 80/443은
VM의 `127.0.0.1`에만 bind되어 외부에 공개되지 않았다.

## 5. Ubuntu 검증 결과

- Java 21 container 전체 Gradle Build/Test: 성공
- PostgreSQL Testcontainers/Flyway/Phase 1~7 통합 회귀: 성공
- 기존 Phase 7 Compose regression: 성공
- 8개 application Docker image build: 성공
- production Compose model render: 성공
- GitHub Actions actionlint: 성공
- Caddy validate/reload/health: 성공
- 4 Frontend 및 4 Backend Caddy routing: 성공
- HTTP → HTTPS: 성공
- test internal CA TLS: 성공
- HTTPS OIDC discovery/JWKS 및 issuer: 성공
- HR/Approval/Admin exact HTTPS redirect/post logout URI, wildcard 0건: 성공
- `/internal/**` public 차단: 성공
- spoofed Forwarded/XFF 무시: 성공
- 실제 client별 Rate Limit bucket 분리: 성공
- Secure/HttpOnly/SameSite cookie와 Security Header: 성공
- Backend/PostgreSQL Host port 비공개: 성공
- Caddy/DB Docker network 경계: 성공
- 민감정보 Compose log scan: 노출 없음
- Identity DB direct dependency/browser token storage regression: 이상 없음

## 6. 발견한 문제와 해결

1. Caddy 고정 IP를 subnet의 `.2`로 사용하자 먼저 시작한 application container의
   자동 할당과 충돌했다. 자동 할당 영역과 떨어진 `.254`로 이동하고 trusted proxy
   `/32`를 함께 변경했다.
2. Caddy directive 자동 정렬로 `/internal/**` 응답보다 Frontend fallback이 먼저
   처리됐다. 각 site를 명시적 `route`로 묶어 internal 404를 최우선 처리했다.
3. 기존 Phase 7 회귀 스크립트는 Local/Test Turnstile disabled를 전제로 했지만
   기존 test env 파일에는 flag가 없었다. 운영 파일을 바꾸지 않고 테스트 기동 명령과
   `infra/test/phase8.env`에서만 `false`를 명시했다.
4. Ubuntu Host에 Java가 없었다. Host 패키지를 설치하지 않고 Java 21 build container와
   Docker socket으로 전체 Testcontainers 검증을 수행했다.

## 7. 변경하지 않은 운영 항목

```text
/opt/sso-lab
운영 .env
운영 secrets/
UFW
DDNS
SSH 설정
공유기/VM network 설정
운영 Docker Compose/container/volume
```

## 8. 남은 승인 및 미검증

- 실제 네 서비스 hostname 결정/등록
- DNS가 현재 VM 공인 주소를 가리키는지 확인
- 공유기와 VM의 public 80/443 경계 적용
- `/opt/sso-lab` Phase 8 config/Secret 배치
- public CA/Let's Encrypt certificate 발급과 갱신
- 실제 HTTPS issuer/callback/SSO/logout Browser smoke test
- 실제 Gmail SMTP, 실제 Turnstile production key 검증
- GitHub production environment/registry/SSH Secret 설정 및 실제 deploy/rollback

위 항목은 운영 변경 승인을 받은 뒤에만 수행한다.

## 9. 2026-08-26 운영 사전검증 보완

### Turnstile Frontend 런타임 설정

운영 image를 `--no-build`로 배포하면서도 공개 Site Key를 환경별로 정확히 적용할 수
있도록 auth-web을 runtime configuration 방식으로 변경했다.

```text
frontend/auth-web/src/App.tsx
frontend/auth-web/index.html
frontend/auth-web/public/runtime-config.js
frontend/auth-web/docker-entrypoint.d/30-sso-lab-runtime-config.sh
infra/docker/auth-web.Dockerfile
infra/docker/frontend.Dockerfile
docker-compose.yml
infra/test/phase8-prod-config.env
scripts/test/verify-phase8-turnstile-runtime.sh
```

- container 시작 시 `TURNSTILE_ENABLED`와 공개 `TURNSTILE_SITE_KEY`만
  `/usr/share/nginx/html/runtime-config.js`에 기록한다.
- 정적 asset 전체가 아니라 runtime config 파일 하나만 nginx 비특권 사용자에게 쓰기
  권한을 부여한다.
- `turnstile-secret`은 auth-server Docker Secret으로만 유지하며 Frontend build arg,
  image metadata, container environment 및 JavaScript bundle에 포함하지 않는다.
- Local/Vite 개발은 기존 `VITE_TURNSTILE_*` fallback을 유지한다.

첫 실행에서 비특권 nginx 사용자가 root 소유 runtime config 파일을 갱신하지 못하는
문제를 발견했다. 해당 파일만 UID 101 소유로 복사하도록 Dockerfile을 보완한 후 활성,
비활성, Site Key 비고정 및 Secret 경계 검사가 모두 통과했다.

### Release workflow

```text
.github/workflows/ci.yml
.github/workflows/release-deploy.yml
```

- 수동 release 입력으로 `vMAJOR.MINOR.PATCH` 또는 기본 `sha-<commit>` tag를 사용한다.
- publish 전에 동일 tag 존재 여부를 검사하여 기존 release image 덮어쓰기를 거부한다.
- auth-web은 전용 Dockerfile을 사용하며 Turnstile 값은 CI build argument로 전달하지
  않는다.
- `v1.0.0` 기준 8개 GHCR image 경로의 Compose rendering과 actionlint가 통과했다.

### 검증 스크립트 보강

```text
scripts/test/verify-phase4-compose.sh
scripts/test/verify-phase6-compose.sh
scripts/test/verify-phase8-production-model.sh
scripts/test/audit-phase8-production-readonly.sh
```

- Phase 4/6 회귀 검사는 Caddy 추가 후에도 기존 9개 application/DB container의
  비공개 port 계약만 검사하도록 범위를 명시했다. Caddy publish 검사는 Phase 8
  전용 스크립트가 담당한다.
- 안전한 예시 값으로 Production Compose image, HTTPS exact URI, port, network,
  PostgreSQL volume 계약과 Turnstile Secret 경계를 검증한다.
- 운영 `.env`는 값을 실행하지 않고 key별 존재/형식/기대값 일치만 판정하며 Secret은
  내용 없이 존재 여부, mode와 owner만 점검한다.

### 재검증 결과

- Backend 전체 Gradle build/test 및 PostgreSQL Testcontainers: 성공
- Auth Server 강제 재실행 test: 38건 성공, 실패/오류/skip 0건
- PostgreSQL 통합 test: 17건 성공
- 네 Frontend production build: 성공
- 8개 application Docker image build: 성공
- auth-web production runtime Turnstile 활성/비활성 및 경계: 성공
- Production Compose/Caddy 정적 모델 rendering: 성공
- Phase 4/6/7 Compose 회귀 및 Phase 8 Caddy 통합 검증: 성공
- GitHub Actions actionlint: 성공

Ubuntu test host의 Docker build cache가 root filesystem을 가득 채워 image metadata
기록이 실패했다. 실행 중 container/image/volume은 유지하고 미사용 BuildKit cache만
`docker builder prune --force`로 정리해 3.756 GB를 회수한 뒤 전체 image build를
성공시켰다. 최종 점검 시 root filesystem 여유 공간은 약 2.4 GB로 운영 적용 전
추가 용량 확보 또는 보존 정책 결정이 필요하다.

### 운영 경로 읽기 전용 점검 결과

`/opt/sso-lab`은 수정하지 않았다. 운영 `.env`의 DB/image/public URL/Turnstile/Gmail
항목과 12개 Production Secret의 존재·권한은 확인했으나, ACME/hostname/exact
redirect/post-logout 및 Bootstrap 설정은 아직 누락되어 있다. 또한 현재 운영 경로의
`docker-compose.yml`은 0 byte이고 production override와 Caddy 설정은 아직 배포되지
않았으며 `sso-lab_postgres-data` 운영 volume도 생성되지 않았다. 이는 운영 승인 후
source/config 배포와 최초 Compose 적용 단계에서 처리할 항목이다.

Phase 8 상태는 계속 `구현/테스트 완료, 운영 적용 승인 대기`이며 Phase 9는 시작하지
않았다.
