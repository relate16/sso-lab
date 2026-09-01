# AI Agent — START HERE

> 목적: AI Agent가 `sso-lab` 프로젝트를 구현할 때 문서 우선순위, 실제 서버 환경, 작업 범위, 보안 경계, 금지사항을 혼동하지 않도록 하기 위한 시작 문서이다.

---

# 1. 반드시 먼저 읽을 문서

AI Agent는 작업 시작 전에 아래 3개 문서를 모두 읽는다.

## 1순위 — 기능 / 아키텍처 / 보안 / 구현 기준

```text
AI_AGENT_MASTER_SPEC_SSO_LAB.md
```

이 문서는 기능 요구사항, 아키텍처, 인증 흐름, 보안 정책, 서비스 경계, 데이터 모델, Phase 범위, 테스트, 문서화 요구사항의 최우선 기준이다.

Master Specification의 보안 원칙과 서비스 경계를 임의로 완화하거나 우회하지 않는다.

이 START HERE 문서는 AI Agent가 프로젝트 작업을 시작할 때 읽는 진입 문서이다. Master Specification을 대체하거나 기능 및 Phase 범위를 재정의하지 않는다.

두 문서 사이에 충돌이 발생하면 `AI_AGENT_MASTER_SPEC_SSO_LAB.md`를 우선한다.

## 2순위 — VM 배포 / 운영 절차

```text
SSO_LAB_VM_ENVIRONMENT_SETUP_GUIDE.md
```

이 문서는 실제 VM 환경에서의 Docker, Docker Compose, `.env`, Secret, PostgreSQL, Caddy, TLS, Flyway, Health Check, 배포, 재기동, Backup, Rollback 절차의 기준이다.

## 3순위 — 현재 실제 네트워크 상태

```text
ubuntu_vm_network_environment.md
```

네트워크 관련 내용은 이 문서에 기록된 현재 실제 환경을 우선한다.

---

# 2. 프로젝트 이름과 실제 경로

현재 실제 프로젝트 / Repository 이름:

```text
sso-lab
```

Linux VM Repository root:

```text
/opt/sso-lab
```

새 코드, Script, README, Docker Compose, 문서에서 `jb-sso-lab` 또는 `/opt/jb-sso-lab`을 새로 사용하지 않는다.

단, 기존 프로토콜 값이나 내부 정책 값에서 `jb`가 의미를 가지는 값은 이름 변경 대상으로 취급하지 않는다.

예:

```text
urn:jb:loa:1
```

위 값은 Master Specification의 의미를 그대로 유지한다.

---

# 3. 현재 실제 서버 환경

```text
Windows Host PC
        ↓
VMware
        ↓
Ubuntu VM
        ↓
Docker / Docker Compose
        ↓
Caddy
        ↓
Auth / Admin / HR / Approval
        ↓
PostgreSQL
```

VM Network Mode:

```text
Bridged
```

Ubuntu Network Interface:

```text
ens33
```

IP 할당 방식:

```text
DHCP
```

서버 접속 기준 hostname:

```text
today-sso.duckdns.org
```

Public IPv4는 DHCP로 변경되므로 이 진입 문서에 고정값으로 기록하지 않는다.

따라서 SSH 명령, 코드, callback URL, redirect URI, 외부 endpoint 등에 당시의
Public IPv4를 영구 하드코딩하지 않는다.

외부 주소는 DuckDNS hostname 사용을 우선한다.

---

# 4. 현재 네트워크 구조 관련 절대 조건

현재 VM은 VMware NAT 구조가 아니다.

잘못된 가정:

```text
Internet
  ↓
Windows Host
  ↓
VMware NAT Port Forwarding
  ↓
Ubuntu VM
```

실제 구조:

```text
Internet
  ↓
Ubuntu VM Public IPv4
```

따라서 VMware NAT Port Forwarding을 전제로 구성하지 않는다.

외부 LTE/5G에서 Ubuntu VM의 TCP 8080 테스트 접속에 성공했으므로 외부 inbound가 가능한 환경임은 확인된 상태이다.

---

# 5. DDNS

DDNS Provider:

```text
DuckDNS
```

Updater:

```text
/home/today/duckdns/duck.sh
```

갱신 주기:

```text
5분 / cron
```

Public IPv4가 변경될 수 있으므로 Public IPv4보다 DuckDNS hostname을 외부 서비스 식별자로 우선한다.

DuckDNS Token은 Secret이다. Git, README, 로그, 테스트 출력, Frontend bundle에 넣지 않는다.

---

# 6. 현재 방화벽 상태

현재 Ubuntu UFW:

```text
inactive
```

AI Agent는 임의로 `sudo ufw enable`을 실행하지 않는다.

방화벽 활성화가 필요한 시점에는 먼저 사용자에게 알리고 SSH 연결이 끊기지 않도록 필요한 포트 허용을 선행한다.

Production 공개 시 기본적으로 고려할 포트:

```text
22/tcp   SSH
80/tcp   HTTP / ACME
443/tcp  HTTPS
```

다음 포트는 외부 Internet에 직접 공개하지 않는다.

```text
5432      PostgreSQL
8080 계열 Spring Backend 내부 Port
개발용 Frontend Port
```

---

# 7. Secret / .env 규칙

실제 서버의 민감정보 위치:

```text
/opt/sso-lab/.env
/opt/sso-lab/secrets/
```

AI Agent는 실제 Secret 값을 다음 위치에 넣지 않는다.

```text
Git
README
로그
테스트 결과
예제 코드
Frontend bundle
```

`.env.example`에는 placeholder만 둔다.

실제 Secret 값은 임의로 덮어쓰거나 재생성하지 않는다.

---

# 8. 반드시 지켜야 하는 아키텍처 경계

## Identity DB

Identity DB는 `auth-server`만 직접 접근한다.

직접 DB 접근 금지:

```text
admin-server
hr-server
approval-server
```

관리 흐름:

```text
Admin Web
   ↓
Admin Server
   ↓
Auth Server Internal Admin API
   ↓
Identity DB
```

## Internal API

다음 경로는 외부 Public Route로 노출하지 않는다.

```text
/internal/**
```

특히:

```text
/internal/admin/v1/**
```

는 Caddy를 통해 인터넷에 공개하지 않는다.

## Browser Token 저장 금지

Browser Storage에 다음을 저장하지 않는다.

```text
Access Token
Refresh Token
ID Token
Client Secret
```

금지 대상:

```text
localStorage
sessionStorage
IndexedDB를 Token 저장소로 사용하는 방식
```

BFF 패턴을 사용하고 Token은 Backend server-side에서 관리한다.

---

# 9. 공식 구현 순서 및 현재 진행 상태

프로젝트의 공식 Phase 체계는 Master Specification의 9개 Phase를 유일한 기준으로 사용한다. 아래 범위는 작업 순서를 빠르게 확인하기 위한 요약이며, 세부 기능 요구사항과 완료 조건은 항상 Master Specification 원문을 따른다.

## Phase 1 — Skeleton

```text
- Monorepo
- Backend service 생성
- Frontend service 생성
- PostgreSQL
- Docker Compose
- Flyway
```

### Phase 1에서 선행 완료된 항목

다음 항목은 Master Specification의 최소 Skeleton 범위를 넘어 Phase 1에서 이미 구현 또는 검증되었다. 이후 Phase의 공식 범위를 변경하는 의미가 아니며, 삭제하거나 되돌리지 않는다.

```text
- 서비스별 Dockerfile 및 Docker image build
- Backend Health Check
- PostgreSQL container 기동
- Flyway migration 실제 적용
- PostgreSQL Testcontainers 통합 테스트
- 전체 Docker Compose smoke test
- Backend health 상태 확인
- Docker network 연결 확인
- 외부에 불필요한 Port가 publish되지 않았는지 확인
```

## Phase 2 — Identity

```text
- User
- Role
- Group
- Email Encryption
- SecretProvider
- System Config
```

## Phase 3 — Passwordless

```text
- Signup OTP
- Login Email OTP
- TOTP
- Recovery Code
- Session
```

## Phase 4 — OIDC

```text
- Authorization Server
- Registered Clients
- HR Client
- Approval Client
- Admin Client
- Claims
- SSO
```

## Phase 5 — Admin

```text
- admin-server
- Internal API
- User 관리
- Group 관리
- Suspend
- Email Re-auth
- Audit
```

## Phase 6 — Logout

```text
- RP-Initiated Logout
- Back-Channel Logout
- Global Logout
- Session UI
```

## Phase 7 — Security

```text
- Rate Limit
- Turnstile
- CSRF
- Security headers
- Secret audit
- Log masking
```

Phase 7은 보안 요구사항을 처음 적용하는 시점을 뜻하지 않는다. Master Specification의 필수 보안 정책과 서비스 경계는 모든 Phase에서 준수하고, Phase 7에서는 위 보안 기능과 hardening 범위를 집중적으로 완성한다.

## Phase 8 — Infra

```text
- Caddy
- Domain
- TLS
- CI/CD
- PC VM deploy
```

현재 Phase 8의 코드, 테스트 전용 Caddy/Compose, CI/CD 및 운영 절차 문서는
구현되었고 `/opt/sso-lab-test` 검증을 완료했다. 이후 승인된 deploy-only workflow로
`/opt/sso-lab` Production 배포를 완료했으며 네 DuckDNS hostname, Caddy 공개
80/443, Let's Encrypt TLS, OIDC HTTPS endpoint, Docker network/volume 및 전체
container health를 실제 환경에서 검증했다. Phase 8은 완료 상태이다.

Production Docker Compose의 file-backed Secret은 Host 파일의 numeric UID/GID를
유지하므로 네 Backend는 Host Secret 소유자와 일치하는 non-root `1000:1000`으로
실행한다. OIDC private key는 Base64 PKCS#8 RSA DER이어야 하며 PKCS#1 DER은 배포 전
preflight에서 거부한다. 이 보완은 Compose/deploy source 변경이므로 기존
`v1.0.0` application image 8개를 재생성하지 않고 그대로 재사용했다.

## Phase 9 — Test / Docs

```text
- Unit
- Integration
- E2E
- Docs
- README
- Developer Setup
```

Phase 9는 전체 시스템 수준의 최종 검증과 문서 완성을 위한 Phase이다. 각 기능 Phase에서도 해당 범위의 Build/Test를 수행하고, 테스트하지 못한 항목은 미검증 상태로 보고한다.

## 현재 진행 상태

| Phase | 상태 |
|---|---|
| Phase 1 — Skeleton | 완료 |
| Phase 2 — Identity | 완료 |
| Phase 3 — Passwordless | 완료 |
| Phase 4 — OIDC | 완료 |
| Phase 5 — Admin | 완료 |
| Phase 6 — Logout | 완료 |
| Phase 7 — Security | 완료 |
| Phase 8 — Infra | 완료 |
| Phase 9 — Test / Docs | 완료 — Local/Ubuntu 격리 회귀, OpenAPI, 필수 문서, 최종 보안 감사 완료 |

---

# 10. 작업 시작 전 절차

코드 수정 전에:

```text
1. 세 문서를 모두 읽는다.
2. Repository tree를 확인한다.
3. 기존 구현을 먼저 분석한다.
4. 기존 코드가 있으면 이어서 구현한다.
5. Master Specification과 현재 코드의 차이를 확인한다.
6. Phase 단위 구현 계획을 작성한다.
```

명백한 충돌이 없으면 불필요한 질문을 반복하지 말고 구현을 진행한다.

다음 상황에서는 작업 전에 사용자에게 보고한다.

```text
- 문서 간 직접 충돌
- 실제 Secret 값이 있어야만 진행 가능
- 사용자 데이터 삭제 필요
- destructive DB migration 필요
- Firewall / SSH 연결 단절 가능성
- Master Specification의 보안 원칙 변경 필요
```

---

# 11. 구현 중 금지사항

```text
- 편의를 위해 서비스 경계를 합치기
- auth-server 이외 서비스가 Identity DB 직접 접근
- Browser localStorage 등에 Token 저장
- /internal/** 외부 공개
- Production Secret Git Commit
- Public IPv4 하드코딩
- 실제 Secret 로그 출력
- PostgreSQL 5432 Public 공개
- 실패한 테스트를 성공으로 보고
- 구현하지 않은 기능을 완료로 표시
```

---

# 12. 테스트 원칙

기능 구현 후 가능한 테스트를 실제 실행한다.

```text
Backend
- compile
- unit test
- integration test

Frontend
- npm ci / npm install
- build
- lint/test (설정된 경우)

Infrastructure
- docker compose config
- Docker build
```

테스트하지 못한 항목은 미검증 상태라고 명시한다.

---

# 13. 완료 보고 형식

각 Phase 또는 전체 작업 완료 후:

```text
1. 구현 완료 항목
2. 생성/수정한 주요 파일
3. 실행한 테스트
4. 테스트 결과
5. 남은 TODO
6. 사용자가 직접 준비해야 하는 외부 설정
7. 배포 시 실행할 명령
8. 현재 위험요소 / 미검증 항목
```

---

# 14. 개발과 실제 서버 배포를 분리한다

권장 흐름:

```text
개발 PC / AI Agent
      ↓
코드 구현 및 테스트
      ↓
Git
      ↓
GitHub
      ↓
Ubuntu VM /opt/sso-lab
      ↓
git pull
      ↓
docker compose up -d --build
```

개발 중 실제 운영 VM의 `.env`와 `secrets/`를 불필요하게 변경하지 않는다.

---

# 15. 최종 목표

```text
Internet
   ↓
DuckDNS hostname
   ↓
Caddy :443
   ↓
Auth / Admin / HR / Approval
   ↓
PostgreSQL
```

핵심 시연:

```text
1. Passwordless 회원가입
2. Email OTP Login
3. TOTP 등록 및 Login
4. HR Login
5. 동일 Browser에서 Approval 접속
6. 추가 인증 없이 SSO
7. Admin 관리
8. 중앙 Logout / Back-Channel Logout
```

---

# 16. 문서 우선순위 한 줄 요약

> 구현 기준은 Master Specification, 실제 배포 절차는 VM Environment Guide, 현재 네트워크 사실은 Ubuntu Network Environment 문서를 따른다.
