# Phase 8 — Infra 로컬 및 서버 변경 이력

작성일: 2026-08-24

로컬 저장소: `C:\dev\sso-lab`

Ubuntu VM 테스트 경로: `/opt/sso-lab-test`

## 2026-08-27 최초 Production 배포 실패 후속 조치

- Compose의 Secret source/target 이름은 정확했으나 file-backed Secret bind mount가
  Host의 `today:today` (`1000:1000`) 소유권을 유지하고, 기존 Backend image는 Alpine
  `ssolab` (`100:101`)로 실행되어 mode `600` 파일을 읽을 수 없었다. Production
  Compose가 `BACKEND_RUNTIME_UID:GID`의 non-root 사용자로 Backend를 실행하도록
  보강했다.
- 운영 OIDC private key는 값을 노출하지 않고 확인한 결과 Base64 PKCS#1 DER이었다.
  Java loader가 요구하는 형식은 Base64 PKCS#8 DER이다. 테스트 Caddy 중지 전에 이
  불일치를 차단하는 배포 preflight를 추가했다.
- 이 수정 과정에서 운영 `.env`, Secret, 컨테이너 및 volume은 변경하지 않았다.

이 문서는 Phase 8 구현 중 로컬 저장소와 테스트 전용 Ubuntu VM에서 변경하거나
검증한 내용과, 이후 별도 승인으로 수행한 Production 배포 결과를 추적한다. 초기
구현/테스트 단계에서는 `/opt/sso-lab`을 변경하지 않았고, 운영 적용은 승인된
deploy-only workflow로만 수행했다. 운영 `.env`와 Secret 값은 문서에 기록하지 않는다.

## 1. 현재 상태

- Phase 1~7: 완료
- Phase 8: Production 배포 및 검증 완료
- Phase 9: 미착수 — 계획 검토 및 승인 대기

실제 서비스별 hostname, public DNS, 외부 80/443, Let's Encrypt certificate,
운영 Compose 및 전체 health를 Production에서 검증했다.

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

당시 START_HERE의 Phase 8 상태는 운영 적용 전이므로
`구현/테스트 완료, 운영 적용 승인 대기`로 기록했다. 이후 승인된 Production 배포와
검증이 완료되어 현재 상태는 `완료`로 갱신했다. Phase 9는 시작하지 않았다.

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

## 7. 구현/테스트 단계에서 변경하지 않았던 운영 항목

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

## 8. 운영 적용 전 당시 남아 있던 승인 및 미검증

- 실제 네 서비스 hostname 결정/등록
- DNS가 현재 VM 공인 주소를 가리키는지 확인
- 공유기와 VM의 public 80/443 경계 적용
- `/opt/sso-lab` Phase 8 config/Secret 배치
- public CA/Let's Encrypt certificate 발급과 갱신
- 실제 HTTPS issuer/callback/SSO/logout Browser smoke test
- 실제 Gmail SMTP, 실제 Turnstile production key 검증
- GitHub production environment/registry/SSH Secret 설정 및 실제 deploy/rollback

위 목록은 운영 적용 전 시점의 기록이다. 이후 hostname/DNS, `/opt/sso-lab` 배포,
public TLS, GitHub production environment 및 deploy/rollback 경계는 승인 후 완료했다.
실제 Gmail 발송과 실제 Turnstile challenge의 최종 Browser E2E는 Phase 9 계획에서
별도 취급한다.

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

이 항목은 2026-08-26 사전검증 당시의 snapshot이다. 이후 디스크 확장, 운영 설정 준비,
release/deploy workflow 적용과 Production 검증을 완료했다. Phase 9는 시작하지 않았다.

## Deploy-only workflow 보완

기존 `.github/workflows/release-deploy.yml`의 publish와 deploy 책임을 다음 두
workflow로 분리했다.

```text
.github/workflows/release-images.yml
.github/workflows/deploy-existing-release.yml
```

- `release-images.yml`: 새 immutable image의 build/publish만 담당하며 기존 tag는
  계속 overwrite하지 않는다.
- `deploy-existing-release.yml`: 기존 tag와 source commit을 입력받아 8개 GHCR
  manifest/digest를 검증한 뒤 protected `production` environment 승인 후에만
  `/opt/sso-lab`을 배포한다. image build/push 단계는 없다.
- 운영 `.env`와 `secrets/`는 서버에 유지하고 GitHub로 전송하거나 출력하지 않는다.
- 배포 전 상태는 repository 밖의 권한 제한 state 경로에 남긴다. 실패 시 source와
  실행 중이던 테스트 Caddy를 복원하며 Production Compose를 내릴 때 volume을
  삭제하지 않는다.

이 보완 작업에서는 workflow와 문서만 로컬에서 수정했다. `/opt/sso-lab`, 테스트
Caddy, Production container/network/volume 및 GitHub Environment는 변경하지 않았고
실제 deploy도 실행하지 않았다.

## 최초 Production deploy Secret mount 실패 보완

최초 deploy에서 BFF 세 서비스가 Secret file unavailable로 종료됐다. Docker inspect와
격리 one-off container로 확인한 결과 source/target 이름은 정확했으나 file-backed
Compose Secret이 Host UID/GID `1000:1000`, mode `0600`을 유지한 반면 Backend image는
UID/GID `100:101`의 `ssolab`으로 실행되어 읽기 권한이 없었다. auth-server의 12개
Secret에도 동일한 잠재 문제가 확인됐다.

Production Compose는 네 Backend를 `BACKEND_RUNTIME_UID:BACKEND_RUNTIME_GID`의
비-root identity로 실행하도록 보완했다. Secret 권한은 `0600`으로 유지한다. 정적
Compose 검증에 runtime identity와 BFF target 검사를 추가했고, 실제 dummy file-backed
Secret을 네 Backend에 mount하여 읽기 가능 여부를 검사하는 CI 테스트를 추가했다.

기존 `v1.0.0` image는 변경하지 않는다. deploy-only workflow는 기존 image를 만든
`image_source_commit`과 Compose/Caddy를 가져오는 workflow `GITHUB_SHA`를 분리하여
Compose-only 수정이 image provenance를 흐리지 않도록 보완했다.

## Production 재배포 및 Phase 8 완료

최초 Production 배포 실패 후 다음 두 원인을 분리해 해결했다.

1. file-backed Compose Secret은 Host UID/GID를 유지하지만 기존 image 내부 기본
   사용자는 `100:101`이어서 mode `0600` Secret을 읽을 수 없었다.
2. 운영 OIDC private key가 Base64 PKCS#1 DER이었으나 Java
   `PKCS8EncodedKeySpec`은 Base64 PKCS#8 DER을 요구했다.

Production Compose는 네 Backend를 non-root `1000:1000`으로 실행한다. 기존 RSA key
pair를 재생성하지 않고 private key 형식만 PKCS#8 DER로 변환했으며, 원본은 권한 제한
경로에 checksum과 함께 백업했다. 배포 전 preflight와 CI에서 runtime owner/mode,
mount readability, PKCS#8/X.509 형식 및 key pair 일치를 검증한다.

Phase 8 보완 commit과 성공 배포 provenance:

```text
deployment source: fad354887baf9a80ec7b7798e3dd1fe9f1835a4c
image source:      f7e6f4a590a5d76b248c7954bb5938cb8d3dfec2
image tag:         v1.0.0
deployment state:  33136044357 (succeeded)
```

수정이 Compose/workflow/preflight 범위였으므로 기존 `v1.0.0` application image 8개는
rebuild/republish/overwrite하지 않고 재사용했다.

Production 배포 후 읽기 전용 검증 결과:

- PostgreSQL, Caddy, Auth/Admin/HR/Approval Backend와 네 Frontend를 포함한 10개
  container가 모두 `running/healthy`
- Production Caddy만 Host 80/tcp, 443/tcp, 443/udp 사용
- Backend 8080, PostgreSQL 5432, Caddy Admin 2019 Host 비공개
- 테스트 Caddy 종료 상태로 Production과 포트 충돌 없음
- 네 DuckDNS hostname HTTPS 200 및 HTTP 308 HTTPS 전환
- Let's Encrypt TLS chain, hostname SAN, 유효기간 검증 성공
- OIDC discovery/JWKS/authorization 및 invalid credential 오류 경로 정상
- Auth Server 12개와 BFF Secret mount 모두 read-only/readable
- Backend 로그의 Secret/PII/credential 원문과 심각 오류 0건
- `/opt/sso-lab` worktree clean, deployment source commit exact match
- `.env` 및 Production Secret checksum 불변, Git ignore 유지
- 최초 실패 배포에서 보존한 PostgreSQL/Caddy volume을 성공 배포가 정상 재사용

Master Specification의 Caddy, Domain, TLS, CI/CD, PC VM deploy 완료 조건을 모두
충족했으므로 Phase 8 — Infra를 완료로 기록한다. Phase 9 — Test / Docs는 계획 승인
전까지 시작하지 않는다.
