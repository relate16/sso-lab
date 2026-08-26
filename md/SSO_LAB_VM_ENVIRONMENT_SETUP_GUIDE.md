# SSO Lab — PC VM 환경 구성 가이드

> 대상: Linux/VM/Docker에 익숙하지 않은 초보 개발자
> 기준 프로젝트: **SSO Lab — OIDC SSO + Passwordless Multi-Method Authentication**
> 배포 기준: **Host PC → Linux VM → Docker Compose → Caddy → 각 서비스 → PostgreSQL**
>
> 이 문서는 **아직 프로젝트 소스가 없는 상태에서 Git Repository를 만드는 단계부터**,
> 프로젝트 구현 후 **도메인 연결, TLS 인증서 발급, Secret 설정, 최초 배포 및 점검**까지 순서대로 설명한다.

---

# 0. 이 문서를 어떻게 사용하면 되나요?

전체 환경 구성은 크게 두 번에 나누어 진행하면 된다.

```text
[1차: 프로젝트 개발 전]

GitHub Repository 생성
        ↓
Linux VM 생성
        ↓
VM Network 설정
        ↓
SSH 접속 확인
        ↓
Docker / Docker Compose 설치
        ↓
Git Clone 가능한 상태까지 준비


[2차: 프로젝트 구현 후]

Repository Clone / Pull
        ↓
.env 작성
        ↓
Secret / Key 생성
        ↓
도메인 및 DNS 연결
        ↓
공유기 Port Forwarding
        ↓
Firewall 설정
        ↓
Caddy 설정
        ↓
Docker Compose 실행
        ↓
Let's Encrypt 인증서 자동 발급
        ↓
Flyway / Health Check
        ↓
회원가입 / Admin / SSO / TOTP / Logout 확인
```

처음 환경을 구성한다면 **1장부터 순서대로 따라가면 된다.**

---

# 1. 최종적으로 만들 환경

목표 구조는 다음과 같다.

```text
                         Internet
                            │
                       HTTPS :443
                            │
                       Public IP
                            │
                         Router
                     80 / 443 Forward
                            │
                            ▼
                    ┌────────────────┐
                    │    Linux VM    │
                    │                │
                    │     Caddy      │
                    └───────┬────────┘
                            │
              Docker Internal Network
                            │
       ┌────────────────────┼─────────────────────┐
       │                    │                     │
       ▼                    ▼                     ▼
   Auth Web              HR Web             Approval Web
       │                    │                     │
       ▼                    ▼                     ▼
 Auth Server            HR Server           Approval Server
       ▲
       │
 Admin Server
       ▲
       │
   Admin Web

       │
       ▼
   PostgreSQL
```

Master Specification의 핵심 인프라 구성은 다음과 같다.

- Docker
- Docker Compose
- Caddy
- Let's Encrypt
- PostgreSQL 1 Instance
- PC VM 공개 배포
- GitHub Actions
- Monorepo
- 서비스별 독립 Docker Image

---

# 2. 이 가이드에서 사용하는 기준 환경

초보자가 따라 하기 쉽도록 이 문서에서는 아래 구성을 예시로 사용한다.

## Host PC

```text
Windows 10 / Windows 11
```

## VM

```text
Ubuntu Server 24.04 LTS 64-bit
```

Ubuntu 22.04 LTS를 사용해도 큰 흐름은 동일하다.

## VM 프로그램

다음 중 하나를 사용할 수 있다.

```text
VMware Workstation
VirtualBox
Hyper-V
Proxmox
기타 일반 Linux VM
```

처음이라면 VMware Workstation 또는 VirtualBox가 비교적 이해하기 쉽다.

---

# 3. 권장 VM 사양

SSO Lab은 하나의 VM에서 여러 Backend/Frontend와 PostgreSQL, Caddy를 동시에 실행한다.

최소 권장값:

```text
CPU    : 4 vCPU
RAM    : 8 GB
Disk   : 40 GB
Network: 1 NIC
```

여유가 있다면:

```text
CPU    : 6~8 vCPU
RAM    : 12~16 GB
Disk   : 60~100 GB
```

개발용 PC의 메모리가 16 GB뿐이라면 VM에 8 GB 이상을 무리하게 할당하지 않는다.

예:

```text
Host RAM 16 GB
VM RAM    6~8 GB
```

Host와 VM 모두 메모리가 부족하면 Docker Build 중 속도가 매우 느려질 수 있다.

---

# 4. 먼저 준비할 계정

환경 구성을 시작하기 전에 다음 계정이 필요하다.

```text
[필수]
GitHub Account

[Production 공개 테스트 시]
도메인 또는 무료 Subdomain 제공 서비스 계정

[Email OTP 구현 후]
Google Account

[Turnstile 구현 후]
Cloudflare Account
```

Gmail SMTP와 Cloudflare Turnstile은 애플리케이션 기능이 구현된 뒤 설정해도 된다.

---

# 5. GitHub Repository 생성

프로젝트는 **Monorepo 하나**로 관리한다.

권장 Repository 이름:

```text
sso-lab
```

GitHub에서 다음 순서로 생성한다.

```text
GitHub 로그인
→ New repository
→ Repository name: sso-lab
→ Public 또는 Private 선택
→ Create repository
```

포트폴리오 공개 목적이라면 최종적으로 Public Repository를 사용할 수 있다.

다만 개발 초기에 Secret 관리가 익숙하지 않다면 Private으로 시작하고,
Secret 노출 여부를 확인한 뒤 Public으로 전환하는 것도 방법이다.

---

# 6. Repository를 처음 Clone하기

개발 PC에서 Git이 설치되어 있는지 확인한다.

```bash
git --version
```

정상 예:

```text
git version 2.x.x
```

Repository Clone:

```bash
git clone https://github.com/<GITHUB_ID>/sso-lab.git
cd sso-lab
```

예:

```bash
git clone https://github.com/jb-dev/sso-lab.git
cd sso-lab
```

---

# 7. 최초 Monorepo 폴더 만들기

아직 프로젝트 구현 전이라면 아래 구조까지만 먼저 만들어도 된다.

```text
sso-lab/
├── backend/
│   ├── auth-server/
│   ├── admin-server/
│   ├── hr-server/
│   └── approval-server/
│
├── frontend/
│   ├── auth-web/
│   ├── admin-web/
│   ├── hr-web/
│   └── approval-web/
│
├── infra/
│   ├── caddy/
│   ├── docker/
│   └── scripts/
│
├── docs/
│
├── .github/
│   └── workflows/
│
├── docker-compose.yml
├── .env.example
├── .gitignore
├── DEVELOPER_SETUP.txt
└── README.md
```

Linux/Git Bash:

```bash
mkdir -p backend/{auth-server,admin-server,hr-server,approval-server}
mkdir -p frontend/{auth-web,admin-web,hr-web,approval-web}
mkdir -p infra/{caddy,docker,scripts}
mkdir -p docs
mkdir -p .github/workflows

touch docker-compose.yml
touch .env.example
touch .gitignore
touch DEVELOPER_SETUP.txt
touch README.md
```

첫 Commit:

```bash
git add .
git commit -m "chore: initialize monorepo structure"
git push origin main
```

---

# 8. 반드시 먼저 작성할 .gitignore

이 프로젝트에서는 Secret이 매우 많다.

`.gitignore`에 최소 다음 내용을 넣는다.

```gitignore
# Environment
.env
.env.*
!.env.example

# Secret files
secrets/
*.pem
*.key
*.p12
*.jks

# Backend
build/
.gradle/

# Frontend
node_modules/
dist/

# IDE
.idea/
.vscode/

# OS
.DS_Store
Thumbs.db

# Logs
*.log
logs/

# Temporary
tmp/
temp/
```

특히 아래 파일은 **절대로 Git에 Commit하지 않는다.**

```text
.env
SMTP Password
Gmail App Password
JWT Private Key
AES Encryption Key
HMAC Key
TOTP Secret
Turnstile Secret
실제 OAuth Client Secret
Production DB Password
```

---

# 9. .env.example과 .env의 차이

두 파일의 역할을 반드시 이해해야 한다.

## `.env.example`

Git에 올려도 되는 **설정 항목의 예시**이다.

예:

```dotenv
POSTGRES_DB=jb_sso
POSTGRES_USER=jb_sso
POSTGRES_PASSWORD=CHANGE_ME

AUTH_PUBLIC_URL=https://auth.example.com
ADMIN_PUBLIC_URL=https://admin.example.com
HR_PUBLIC_URL=https://hr.example.com
APPROVAL_PUBLIC_URL=https://approval.example.com

TURNSTILE_ENABLED=true

BOOTSTRAP_ADMIN_ENABLED=true
BOOTSTRAP_ADMIN_EMAIL=your-email@example.com
```

실제 Secret 값은 넣지 않는다.

## `.env`

실제 서버에서 사용하는 값이다.

```text
Git Commit 금지
```

`.env.example`을 복사해서 만든다.

```bash
cp .env.example .env
```

---

# 10. Linux VM 생성

VM 프로그램에서 새 VM을 만든다.

권장 설정:

```text
OS       Ubuntu Server 24.04 LTS
CPU      4 Core
RAM      8 GB
Disk     40 GB 이상
Network  Bridged 권장
```

Ubuntu 설치 과정에서 다음 옵션은 가능하면 선택한다.

```text
Install OpenSSH server
```

그래야 Host PC에서 SSH로 VM에 접속할 수 있다.

---

# 11. VM Network Mode 이해하기

VM에서 가장 많이 헷갈리는 부분이다.

## 방법 A. Bridged

권장 방식이다.

```text
공유기
 │
 ├── Host PC  192.168.0.10
 │
 └── VM       192.168.0.20
```

VM이 공유기에 직접 연결된 하나의 별도 PC처럼 동작한다.

장점:

```text
공유기 Port Forwarding 설정이 단순함
VM으로 직접 SSH 가능
Caddy/TLS 구성이 이해하기 쉬움
```

이 가이드에서는 **Bridged Mode를 기본 기준**으로 사용한다.

---

## 방법 B. NAT

구조:

```text
공유기
  │
Host PC
  │
VM NAT
```

이 경우 외부에서 VM으로 직접 접근하려면:

```text
공유기 Port Forwarding
+
VM 프로그램 Port Forwarding
```

두 단계가 필요할 수 있다.

처음 구축한다면 Bridged가 훨씬 쉽다.

---

# 12. VM IP 확인

VM에 로그인한 뒤:

```bash
ip addr
```

또는:

```bash
hostname -I
```

예:

```text
192.168.0.20
```

이 IP를 메모해 둔다.

이후 공유기 Port Forwarding 대상 IP가 된다.

---

# 13. VM IP가 바뀌지 않도록 설정

서버 IP가 바뀌면 Port Forwarding이 깨진다.

가장 쉬운 방법은 공유기에서:

```text
DHCP Reservation
```

을 설정하는 것이다.

예:

```text
VM MAC Address
        ↓
항상 192.168.0.20 할당
```

공유기 관리자 페이지에서 보통 다음 이름으로 제공된다.

```text
DHCP 고정 할당
IP 예약
Address Reservation
Static DHCP
```

Linux 내부에서 수동 Static IP를 설정하는 방법도 있지만,
초보자에게는 공유기의 DHCP Reservation을 권장한다.

---

# 14. Host PC에서 VM SSH 접속 확인

Windows PowerShell:

```powershell
ssh <VM_USER>@192.168.0.20
```

예:

```powershell
ssh jb@192.168.0.20
```

처음 접속하면 다음과 비슷한 질문이 나온다.

```text
Are you sure you want to continue connecting?
```

입력:

```text
yes
```

접속되면 준비 완료다.

---

# 15. Ubuntu 기본 업데이트

VM에서:

```bash
sudo apt update
sudo apt upgrade -y
```

필수 도구:

```bash
sudo apt install -y \
  ca-certificates \
  curl \
  git \
  openssl \
  jq \
  unzip
```

확인:

```bash
git --version
curl --version
openssl version
```

---

# 16. 서버 시간 확인

OIDC, TOTP, TLS는 시간이 틀리면 문제가 발생할 수 있다.

현재 시간 확인:

```bash
timedatectl
```

예상:

```text
System clock synchronized: yes
NTP service: active
```

Time Zone을 Seoul로 사용할 경우:

```bash
sudo timedatectl set-timezone Asia/Seoul
```

다시 확인:

```bash
timedatectl
```

---

# 17. Docker 설치

Docker는 Ubuntu 기본 저장소의 임의 패키지보다 **Docker 공식 Repository 방식** 사용을 권장한다.

먼저 충돌 가능한 기존 패키지를 제거한다.

```bash
sudo apt remove -y \
  docker.io \
  docker-compose \
  docker-compose-v2 \
  docker-doc \
  podman-docker \
  containerd \
  runc 2>/dev/null || true
```

Docker 공식 GPG Key 준비:

```bash
sudo apt update
sudo apt install -y ca-certificates curl

sudo install -m 0755 -d /etc/apt/keyrings

sudo curl -fsSL \
  https://download.docker.com/linux/ubuntu/gpg \
  -o /etc/apt/keyrings/docker.asc

sudo chmod a+r /etc/apt/keyrings/docker.asc
```

Docker Repository 등록:

```bash
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "${UBUNTU_CODENAME:-$VERSION_CODENAME}") stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
```

설치:

```bash
sudo apt update

sudo apt install -y \
  docker-ce \
  docker-ce-cli \
  containerd.io \
  docker-buildx-plugin \
  docker-compose-plugin
```

---

# 18. Docker 정상 확인

```bash
sudo docker run --rm hello-world
```

정상이라면:

```text
Hello from Docker!
```

와 비슷한 메시지가 나온다.

Docker Version:

```bash
docker --version
```

Docker Compose:

```bash
docker compose version
```

중요:

```text
docker-compose
```

보다 현재 표준 명령인:

```text
docker compose
```

를 사용한다.

---

# 19. sudo 없이 Docker 사용하기

현재 사용자를 docker group에 추가한다.

```bash
sudo usermod -aG docker $USER
```

그다음 SSH 연결을 종료한다.

```bash
exit
```

다시 접속:

```bash
ssh <VM_USER>@192.168.0.20
```

확인:

```bash
docker ps
```

`permission denied`가 나오지 않으면 정상이다.

> 주의
> docker group 사용자는 사실상 높은 시스템 권한을 가진다.
> 신뢰할 수 있는 서버 운영 사용자만 docker group에 추가한다.

---

# 20. Docker 자동 시작 확인

```bash
sudo systemctl enable docker
sudo systemctl status docker
```

정상 예:

```text
Active: active (running)
```

VM 재부팅 뒤에도 Docker가 자동 시작되어야 한다.

---

# 21. VM에 Repository Clone

운영용 디렉터리 예:

```text
/opt/sso-lab
```

생성:

```bash
sudo mkdir -p /opt/sso-lab
sudo chown -R $USER:$USER /opt/sso-lab
```

Clone:

```bash
git clone https://github.com/<GITHUB_ID>/sso-lab.git /opt/sso-lab
```

이동:

```bash
cd /opt/sso-lab
```

확인:

```bash
git status
```

---

# 22. Private Repository라면

Private Repository는 인증 없이 Clone할 수 없다.

방법은 여러 가지지만 VM 배포에서는 일반적으로:

```text
SSH Deploy Key
```

또는 제한된 권한의 GitHub 인증을 사용한다.

운영 VM 전체 권한을 가진 개인 Access Token을 평문 파일로 저장하지 않는 것을 권장한다.

초기 학습 단계에서 Public Repository라면 HTTPS Clone이 가장 간단하다.

---

# 23. 프로젝트 구현 전 여기까지 하면 1차 환경 준비 완료

아직 Java/Spring/React 소스가 없다면 여기서 멈춰도 된다.

현재 확인할 것:

```bash
git --version
docker --version
docker compose version
hostname -I
```

모두 정상이라면 개발 전 VM 준비는 끝났다.

---

# 24. 프로젝트 구현 후 필요한 Container

최종 Docker Compose에는 최소 다음 서비스가 존재해야 한다.

```text
postgres

auth-server
admin-server
hr-server
approval-server

auth-web
admin-web
hr-web
approval-web

caddy
```

각 Backend/Frontend는 별도의 Docker Image로 만든다.

---

# 25. 권장 Docker Network

외부에 모든 Container를 노출하면 안 된다.

논리적으로 다음처럼 나눈다.

```text
public-network
internal-network
db-network
```

예:

```text
Internet
   │
 Caddy
   │
public-network
   │
Frontend / Backend
   │
internal-network
   │
Auth Server
   │
db-network
   │
PostgreSQL
```

PostgreSQL의 `5432`를 인터넷에 공개하지 않는다.

즉 이런 설정은 Production에서 피한다.

```yaml
ports:
  - "5432:5432"
```

외부에서 DB에 접속할 필요가 없다면 `ports` 자체를 두지 않는다.

---

# 26. .env 만들기

Repository Root:

```bash
cd /opt/sso-lab
cp .env.example .env
```

권한을 제한한다.

```bash
chmod 600 .env
```

확인:

```bash
ls -l .env
```

권장:

```text
-rw------- ...
```

---

# 27. Production URL 정하기

서비스별 URL이 필요하다.

예:

```text
AUTH_PUBLIC_URL=https://auth.example.com
ADMIN_PUBLIC_URL=https://admin.example.com
HR_PUBLIC_URL=https://hr.example.com
APPROVAL_PUBLIC_URL=https://approval.example.com
```

무료 Subdomain을 사용한다면 예를 들어:

```text
https://jb-auth.example-free-domain
https://jb-admin.example-free-domain
https://jb-hr.example-free-domain
https://jb-approval.example-free-domain
```

중요한 것은 Source Code에 URL을 하드코딩하지 않는 것이다.

`.env` 또는 Production Config로 관리한다.

---

# 28. PostgreSQL 계정 준비

임의의 강한 Password 생성:

```bash
openssl rand -base64 32
```

예시는 절대로 그대로 사용하지 않는다.

`.env`:

```dotenv
POSTGRES_DB=jb_sso
POSTGRES_USER=jb_sso
POSTGRES_PASSWORD=<생성한 강한 비밀번호>
```

`.env`는 Git에 올리지 않는다.

---

# 29. Secret 저장 디렉터리 만들기

Master Specification에서는 Production Secret을 Source Code 또는 Git에 저장하지 않는다.

VM에서:

```bash
sudo mkdir -p /opt/sso-lab/secrets
sudo chown $USER:$USER /opt/sso-lab/secrets
chmod 700 /opt/sso-lab/secrets
```

확인:

```bash
ls -ld /opt/sso-lab/secrets
```

---

# 30. 필요한 주요 Key

논리적으로 최소 다음 Key를 분리한다.

```text
EMAIL_ENCRYPTION_KEY
TOTP_ENCRYPTION_KEY
EMAIL_LOOKUP_HMAC_KEY
JWT_SIGNING_KEY
```

한 Key를 여러 목적으로 재사용하지 않는다.

특히:

```text
JWT Private Key
```

와:

```text
AES Encryption Key
```

는 완전히 별개의 Key다.

---

# 31. Email AES-256 Key 생성

AES-256은 32 byte Key가 필요하다.

생성:

```bash
openssl rand -base64 32 > secrets/email-encryption-key
```

권한:

```bash
chmod 600 secrets/email-encryption-key
```

---

# 32. TOTP AES-256 Key 생성

Email Key와 **다른 Key**를 만든다.

```bash
openssl rand -base64 32 > secrets/totp-encryption-key
chmod 600 secrets/totp-encryption-key
```

---

# 33. Email Lookup HMAC Key 생성

```bash
openssl rand -base64 32 > secrets/email-lookup-hmac-key
chmod 600 secrets/email-lookup-hmac-key
```

---

# 34. JWT RSA Signing Key 생성

예시로 RSA 3072 bit Private Key를 생성한다.

```bash
openssl genpkey \
  -algorithm RSA \
  -pkeyopt rsa_keygen_bits:3072 \
  -out secrets/jwt-private-key.pem
```

Public Key:

```bash
openssl pkey \
  -in secrets/jwt-private-key.pem \
  -pubout \
  -out secrets/jwt-public-key.pem
```

권한:

```bash
chmod 600 secrets/jwt-private-key.pem
chmod 644 secrets/jwt-public-key.pem
```

Private Key 확인:

```bash
openssl pkey \
  -in secrets/jwt-private-key.pem \
  -check \
  -noout
```

정상 예:

```text
Key is valid
```

---

# 35. Secret 파일을 화면에 출력하지 마세요

운영 서버에서 다음 명령을 습관적으로 사용하지 않는 것이 좋다.

```bash
cat secrets/jwt-private-key.pem
cat secrets/email-encryption-key
```

Terminal History, 화면 공유, 로그 등에 노출될 수 있다.

Secret 존재 여부만 확인:

```bash
ls -l secrets/
```

---

# 36. Docker Compose Secret 예시

Compose가 Secret 파일을 Container에 전달하도록 구성할 수 있다.

예:

```yaml
services:
  auth-server:
    secrets:
      - email_encryption_key
      - totp_encryption_key
      - email_lookup_hmac_key
      - jwt_private_key

secrets:
  email_encryption_key:
    file: ./secrets/email-encryption-key

  totp_encryption_key:
    file: ./secrets/totp-encryption-key

  email_lookup_hmac_key:
    file: ./secrets/email-lookup-hmac-key

  jwt_private_key:
    file: ./secrets/jwt-private-key.pem
```

애플리케이션은 `SecretProvider` abstraction을 통해 해당 값을 읽도록 구현한다.

---

# 37. Gmail SMTP 준비

Email OTP 기능이 구현된 후 설정한다.

개념적으로 필요한 것은:

```text
SMTP Username
SMTP App Password
```

Google Account의 일반 Login Password를 Source Code에 넣지 않는다.

App Password 또는 프로젝트가 채택한 안전한 SMTP 인증 방법을 사용한다.

예:

```text
SMTP_USERNAME
SMTP_PASSWORD
```

`SMTP_PASSWORD`는:

```text
.env
Docker Secret
Environment Secret
```

등을 통해 전달한다.

Git Commit 금지.

실제 상세 절차는 프로젝트의:

```text
docs/GMAIL_SMTP_SETUP.md
```

에 별도 작성하는 것을 권장한다.

---

# 38. Cloudflare Turnstile 준비

Production 인터넷 공개 환경에서는 로그인/회원가입 시작점에 Turnstile을 적용한다.

설정 예:

```dotenv
TURNSTILE_ENABLED=true
TURNSTILE_SITE_KEY=<public site key>
```

Secret Key는 Git에 저장하지 않는다.

예:

```text
TURNSTILE_SECRET
```

Local Profile:

```dotenv
TURNSTILE_ENABLED=false
```

Production에서는 기본적으로 활성화하는 구성을 권장한다.

---

# 39. Bootstrap Admin 설정

최초 ADMIN을 만들기 위한 설정이다.

예:

```dotenv
BOOTSTRAP_ADMIN_ENABLED=true
BOOTSTRAP_ADMIN_EMAIL=developer@example.com
```

흐름:

```text
해당 Email로 회원가입
       ↓
Email 인증 완료
       ↓
Bootstrap Email 일치
       ↓
USER + ADMIN Role 부여
```

최초 Admin 생성이 완료되면:

```dotenv
BOOTSTRAP_ADMIN_ENABLED=false
```

로 변경하는 것을 권장한다.

---

# 40. Public IPv4 확인

TLS 발급 전에 반드시 확인해야 한다.

VM에서:

```bash
curl -4 ifconfig.me
```

출력 예:

```text
203.0.113.10
```

이 값은 **공유기의 외부 Public IPv4**여야 한다.

공유기 관리자 화면의 WAN IP도 확인한다.

두 값이 같거나 정상적인 Public IP 구조라면 일반적인 Port Forwarding 방식이 가능하다.

---

# 41. CGNAT인지 확인하는 방법

아래 상황이면 CGNAT일 가능성이 있다.

```text
curl로 확인한 외부 IP
≠
공유기 WAN IP
```

또는 공유기 WAN IP가 사설/CGNAT 대역이라면 직접 inbound가 안 될 수 있다.

이 경우:

```text
Internet
→ 내 공유기
→ VM
```

로 80/443을 직접 열기 어려울 수 있다.

대안:

```text
Cloudflare Tunnel
기타 Outbound Tunnel
Public VM/VPS
```

이 프로젝트의 Primary Architecture는 Caddy + Public IP + Port Forwarding이지만,
CGNAT 환경에서는 Tunnel을 대체 방법으로 사용할 수 있다.

---

# 42. 도메인 또는 무료 Subdomain 준비

Caddy가 Public TLS 인증서를 자동 발급하려면
도메인이 실제 Public IP를 가리켜야 한다.

예:

```text
auth.example.com      → 203.0.113.10
admin.example.com     → 203.0.113.10
hr.example.com        → 203.0.113.10
approval.example.com  → 203.0.113.10
```

모두 같은 Public IP를 사용해도 된다.

Caddy가 Hostname에 따라 요청을 각 서비스로 분리한다.

---

# 43. DNS A Record 설정

DNS Provider에서 다음과 같이 설정한다.

```text
Type    Name       Value
A       auth       <PUBLIC_IP>
A       admin      <PUBLIC_IP>
A       hr         <PUBLIC_IP>
A       approval   <PUBLIC_IP>
```

예:

```text
auth.example.com → 203.0.113.10
```

DNS 전파 후 VM 또는 PC에서 확인:

```bash
nslookup auth.example.com
```

또는:

```bash
dig +short auth.example.com
```

결과가 Public IP와 같아야 한다.

---

# 44. 공유기 Port Forwarding

공유기 관리자 페이지에서:

```text
External 80  → VM_IP:80
External 443 → VM_IP:443
```

예:

```text
TCP 80  → 192.168.0.20:80
TCP 443 → 192.168.0.20:443
```

Caddy HTTP/3까지 사용하려면 443/UDP도 고려할 수 있지만,
처음 구성에서는 TCP 80/443 정상 동작부터 확인한다.

가장 중요한 것은:

```text
Internet의 80/443 요청이 최종적으로 Caddy까지 도착해야 한다.
```

---

# 45. Ubuntu Firewall 설정

UFW 상태 확인:

```bash
sudo ufw status
```

SSH를 먼저 허용한다.

```bash
sudo ufw allow OpenSSH
```

HTTP/HTTPS 허용:

```bash
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
```

필요하면 HTTP/3:

```bash
sudo ufw allow 443/udp
```

Firewall 활성화:

```bash
sudo ufw enable
```

확인:

```bash
sudo ufw status
```

예:

```text
22/tcp   ALLOW
80/tcp   ALLOW
443/tcp  ALLOW
```

주의:

SSH 허용 전에 UFW를 활성화하면 원격 접속이 끊길 수 있다.

---

# 46. 외부에 열어야 하는 Port

기본:

```text
22   SSH
80   HTTP / ACME
443  HTTPS
```

22번은 가능하면 전체 인터넷에 무제한 공개하지 않는 것이 좋다.

예:

```text
관리자 IP만 허용
VPN 사용
SSH Key 인증
```

Portfolio Demo에서는 최소한 Password Login보다 SSH Key 인증을 권장한다.

---

# 47. 외부에 열면 안 되는 대표 Port

다음 Container Port는 일반적으로 외부 인터넷에 직접 노출하지 않는다.

```text
5432 PostgreSQL

8080 / 8081 / 8082 ...
Spring Backend 내부 Port

3000 / 5173 ...
Frontend 개발용 Port
```

Production 외부 진입점은 기본적으로 Caddy의:

```text
80
443
```

만 사용한다.

---

# 48. Caddy의 역할

Caddy는 다음 역할을 맡는다.

```text
1. Internet 요청 수신
2. HTTP → HTTPS Redirect
3. Let's Encrypt 인증서 자동 발급
4. 인증서 자동 갱신
5. Hostname별 Reverse Proxy
```

따라서 직접 인증서 파일을 매번 발급해서 복사하는 방식보다 단순하다.

---

# 49. Caddy는 Docker Container로 실행

이 프로젝트에서는 Caddy도 Compose에 포함하는 방식을 권장한다.

예:

```yaml
caddy:
  image: caddy:2
  restart: unless-stopped

  ports:
    - "80:80"
    - "443:443"
    - "443:443/udp"

  volumes:
    - ./infra/caddy/Caddyfile:/etc/caddy/Caddyfile:ro
    - caddy_data:/data
    - caddy_config:/config
```

중요한 Volume:

```text
/data
/config
```

TLS 관련 데이터가 유지되어야 하므로 무작정 삭제하지 않는다.

---

# 50. 기본 Caddyfile 예제

`infra/caddy/Caddyfile`

```caddy
auth.example.com {
    reverse_proxy auth-web:80
}

admin.example.com {
    reverse_proxy admin-web:80
}

hr.example.com {
    reverse_proxy hr-web:80
}

approval.example.com {
    reverse_proxy approval-web:80
}
```

Frontend와 Backend가 같은 Origin을 사용한다면 예:

```caddy
hr.example.com {
    handle /api/* {
        reverse_proxy hr-server:8080
    }

    handle {
        reverse_proxy hr-web:80
    }
}
```

Auth도 같은 방식으로 구성할 수 있다.

```caddy
auth.example.com {
    handle /api/* {
        reverse_proxy auth-server:8080
    }

    handle /.well-known/* {
        reverse_proxy auth-server:8080
    }

    handle /oauth2/* {
        reverse_proxy auth-server:8080
    }

    handle {
        reverse_proxy auth-web:80
    }
}
```

실제 OIDC Endpoint Path는 프로젝트 구현과 Spring Authorization Server 설정에 맞춰 조정한다.

---

# 51. /internal/** 절대 외부 공개 금지

Master Specification의 중요한 보안 규칙이다.

다음 API:

```text
/internal/admin/v1/**
```

는 Caddy Public Route를 만들면 안 된다.

즉 외부 Browser:

```text
Internet
→ Caddy
→ /internal/**
```

가 가능하면 안 된다.

Admin Server가 Docker Internal Network를 통해 Auth Server에 직접 호출하도록 한다.

```text
Admin Server
      │
internal-network
      │
Auth Server
```

---

# 52. Caddyfile 검사

Caddy Container가 이미 준비된 경우:

```bash
docker compose run --rm caddy \
  caddy validate \
  --config /etc/caddy/Caddyfile
```

정상이라면 설정 오류가 없어야 한다.

---

# 53. TLS 인증서 발급 전 반드시 확인할 4가지

Let's Encrypt 오류의 대부분은 아래 4가지다.

```text
1. DNS가 현재 Public IP를 가리키는가?
2. 공유기 80/443 Port Forwarding이 되어 있는가?
3. Ubuntu Firewall에서 80/443을 허용했는가?
4. Caddy가 80/443을 실제 Listen 중인가?
```

이 네 가지가 먼저다.

---

# 54. Docker Compose 실행

Repository Root:

```bash
cd /opt/sso-lab
```

Image Build가 필요한 경우:

```bash
docker compose build
```

기동:

```bash
docker compose up -d
```

확인:

```bash
docker compose ps
```

정상 예:

```text
NAME                 STATUS
jb-postgres          Up
jb-auth-server       Up
jb-auth-web          Up
jb-admin-server      Up
...
jb-caddy             Up
```

---

# 55. Container가 바로 종료된다면

전체 상태:

```bash
docker compose ps -a
```

특정 서비스 Log:

```bash
docker compose logs auth-server
```

마지막 200줄:

```bash
docker compose logs --tail=200 auth-server
```

실시간:

```bash
docker compose logs -f auth-server
```

전체:

```bash
docker compose logs --tail=200
```

---

# 56. Caddy TLS 발급 Log 확인

```bash
docker compose logs -f caddy
```

정상이라면 Caddy가 Domain에 대해 인증서를 관리하는 Log가 보인다.

외부 PC에서:

```bash
curl -I https://auth.example.com
```

또는 Browser:

```text
https://auth.example.com
```

로 접속한다.

브라우저 주소창에 정상적인 HTTPS 잠금 표시가 나타나는지 확인한다.

---

# 57. 인증서 직접 확인

OpenSSL:

```bash
openssl s_client \
  -connect auth.example.com:443 \
  -servername auth.example.com \
  </dev/null 2>/dev/null \
  | openssl x509 -noout -subject -issuer -dates
```

확인할 값:

```text
subject
issuer
notBefore
notAfter
```

Caddy가 관리하는 인증서는 자동 갱신되므로,
cron으로 `certbot renew`를 별도로 구성하지 않는다.

---

# 58. 인증서가 발급되지 않을 때

순서대로 확인한다.

## 1.

```bash
nslookup auth.example.com
```

Public IP가 맞는가?

## 2.

```bash
sudo ss -lntp | grep -E ':80|:443'
```

80/443 Listen 중인가?

## 3.

```bash
sudo ufw status
```

80/443 허용인가?

## 4.

공유기:

```text
80 → VM:80
443 → VM:443
```

인가?

## 5.

```bash
docker compose logs --tail=200 caddy
```

ACME Error를 확인한다.

---

# 59. Flyway Migration 확인

auth-server 시작 Log:

```bash
docker compose logs auth-server | grep -i flyway
```

Database에 직접 접속해서 확인해야 하는 경우:

```bash
docker compose exec postgres \
  psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"
```

접속 후:

```sql
SELECT * FROM flyway_schema_history ORDER BY installed_rank;
```

Production에서는 Hibernate:

```text
ddl-auto=create
ddl-auto=update
```

를 사용하지 않는다.

권장:

```text
ddl-auto=validate
```

DB Schema 변경은 Flyway로 관리한다.

---

# 60. Health Check

각 Backend는 최소:

```text
/actuator/health
```

또는 이에 준하는 Health Endpoint를 제공한다.

외부에서 Auth:

```bash
curl https://auth.example.com/actuator/health
```

예:

```json
{"status":"UP"}
```

단, Production에서 다음 Actuator Endpoint는 공개하지 않는다.

```text
/env
/beans
/heapdump
/configprops
```

---

# 61. OIDC Discovery 확인

Auth Server가 기동된 후 OIDC Discovery Endpoint를 확인한다.

일반적으로:

```text
https://auth.example.com/.well-known/openid-configuration
```

확인:

```bash
curl -s \
  https://auth.example.com/.well-known/openid-configuration \
  | jq
```

확인할 항목:

```text
issuer
authorization_endpoint
token_endpoint
jwks_uri
```

`issuer`가 실제 Production URL과 일치해야 한다.

예:

```text
https://auth.example.com
```

---

# 62. 최초 회원가입

브라우저에서:

```text
https://auth.example.com
```

접속.

다음 순서로 확인한다.

```text
회원가입
  ↓
userId 입력
username 입력
email 입력
  ↓
Email OTP
  ↓
OTP 검증
  ↓
가입 완료
```

비밀번호 입력란은 없어야 한다.

---

# 63. Bootstrap Admin 확인

`.env`에 설정한:

```text
BOOTSTRAP_ADMIN_EMAIL
```

과 동일한 Email로 최초 회원가입한다.

가입 후 Admin Service:

```text
https://admin.example.com
```

접속.

Role 확인:

```text
USER
ADMIN
```

Admin 생성 확인 후 Bootstrap 기능을 끈다.

```dotenv
BOOTSTRAP_ADMIN_ENABLED=false
```

재배포:

```bash
docker compose up -d
```

---

# 64. Email OTP 로그인 Smoke Test

확인 순서:

```text
1. HR 접속
2. Auth Server Redirect
3. userId 입력
4. Email OTP 선택
5. Email OTP 입력
6. HR 로그인 완료
```

HR 화면에서 확인:

```text
amr = ["email_otp"]
acr = "urn:jb:loa:1"
```

---

# 65. SSO Smoke Test

Email OTP로 HR 로그인 상태에서:

```text
https://approval.example.com
```

접속한다.

정상 Flow:

```text
Approval
   ↓
Auth Server
   ↓
기존 SSO Session 발견
   ↓
추가 OTP 입력 없음
   ↓
Approval 로그인 완료
```

이 동작이 이 프로젝트의 핵심 Demo 중 하나다.

---

# 66. TOTP 등록 Smoke Test

Auth Web의 내 정보:

```text
TOTP 등록
```

Flow:

```text
재인증
  ↓
TOTP Secret 생성
  ↓
QR 표시
  ↓
Authenticator Scan
  ↓
6자리 TOTP 입력
  ↓
Server 검증
  ↓
등록 완료
  ↓
Recovery Code 표시
```

QR만 Scan하고 끝나면 안 된다.

최소 한 번 실제 TOTP Code 검증에 성공한 뒤 활성화되어야 한다.

---

# 67. TOTP 로그인 Smoke Test

Logout 후 다시 Login.

TOTP 등록 사용자는:

```text
Email OTP
TOTP
```

중 선택 가능해야 한다.

TOTP Login 후 Claim:

```text
amr = ["totp"]
acr = "urn:jb:loa:1"
```

인지 확인한다.

---

# 68. Back-Channel Logout 확인

예:

```text
1. HR 로그인
2. Approval SSO 로그인
3. HR에서 현재 SSO Session 로그아웃
4. Auth Server Session 삭제
5. Auth Server → Approval Back-Channel Logout
6. Approval Backend Session 삭제
7. Approval 새로고침
8. 다시 인증 필요
```

정상이라면 중앙 로그아웃이 동작한 것이다.

---

# 69. 중요한 Log 확인 명령 모음

모든 Container:

```bash
docker compose ps
```

모든 Log:

```bash
docker compose logs
```

마지막 100줄:

```bash
docker compose logs --tail=100
```

Auth:

```bash
docker compose logs -f auth-server
```

Caddy:

```bash
docker compose logs -f caddy
```

PostgreSQL:

```bash
docker compose logs -f postgres
```

특정 문자열 검색:

```bash
docker compose logs auth-server | grep -i error
```

---

# 70. Log에 절대 출력하면 안 되는 값

다음은 Production Log에 남기면 안 된다.

```text
OTP 원문
TOTP Secret
Recovery Code 원문
AES Key
JWT Private Key
SMTP Password
Turnstile Secret
Access Token
Refresh Token
전체 Email 원문
```

개발 편의를 위해:

```java
logger.info("otp={}", otp);
```

같은 코드를 만드는 것도 피한다.

---

# 71. Container 재시작

전체:

```bash
docker compose restart
```

특정 서비스:

```bash
docker compose restart auth-server
```

정지:

```bash
docker compose stop
```

다시 시작:

```bash
docker compose start
```

---

# 72. Compose 재배포

설정 또는 Image가 변경된 경우:

```bash
docker compose up -d
```

Source 기반 Build:

```bash
docker compose up -d --build
```

Registry Image를 Pull하는 구조:

```bash
docker compose pull
docker compose up -d
```

---

# 73. 서버 재부팅 테스트

VM:

```bash
sudo reboot
```

잠시 후 SSH 재접속.

```bash
ssh <VM_USER>@<VM_IP>
```

확인:

```bash
docker compose -f /opt/sso-lab/docker-compose.yml ps
```

각 Container에:

```yaml
restart: unless-stopped
```

등 적절한 Restart Policy가 설정되어 있다면 자동 시작되어야 한다.

Caddy도 자동 시작되어야 HTTPS가 복구된다.

---

# 74. GitHub Actions 기본 전략

권장 Pipeline:

```text
Pull Request
   ↓
Backend Unit Test
   ↓
Frontend Test / Lint
   ↓
Testcontainers Integration Test
   ↓
Build
   ↓
Docker Image Build
```

Main:

```text
test
 ↓
build
 ↓
Docker Image
 ↓
Container Registry Push
 ↓
PC VM Deploy
```

---

# 75. 초보자에게 권장하는 배포 방식

처음에는 다음 2단계로 나누는 것이 이해하기 쉽다.

## 단계 1 — 수동 배포

VM:

```bash
cd /opt/sso-lab
git pull
docker compose up -d --build
```

먼저 수동 배포가 완전히 성공하는지 확인한다.

## 단계 2 — GitHub Actions 자동화

수동 절차가 검증된 뒤:

```text
GitHub Actions
   ↓
SSH
   ↓
VM
   ↓
docker compose pull
docker compose up -d
```

로 자동화한다.

처음부터 CI/CD와 Application 문제를 동시에 디버깅하지 않는 것이 좋다.

---

# 76. GitHub Actions 배포용 별도 사용자 권장

운영 VM에서 별도 사용자 예:

```text
deploy
```

생성:

```bash
sudo adduser deploy
sudo usermod -aG docker deploy
```

Repository 권한:

```bash
sudo chown -R deploy:deploy /opt/sso-lab
```

배포 자동화에는 필요한 권한만 준다.

---

# 77. SSH Key 생성

개발 PC 또는 배포용 환경에서:

```bash
ssh-keygen -t ed25519 -C "sso-lab-deploy"
```

Public Key:

```text
id_ed25519.pub
```

를 VM의:

```text
~/.ssh/authorized_keys
```

에 등록한다.

Private Key는 절대 Repository에 Commit하지 않는다.

GitHub Actions에서 사용할 경우 GitHub Repository Secret 등 안전한 Secret 저장소를 사용한다.

---

# 78. GitHub Secret 예

자동 배포에 필요한 값 예:

```text
VM_HOST
VM_USER
VM_SSH_PRIVATE_KEY
```

애플리케이션 Secret까지 모두 GitHub Actions에 복사해야 하는 것은 아니다.

가능하면 운영 Secret은 VM에 유지하고,
GitHub Actions는 **새 Image 배포만 수행**하는 구조가 단순하다.

---

# 79. GitHub Actions 배포 개념 예제

아래는 개념 예시이다.

```yaml
name: Deploy

on:
  push:
    branches:
      - main

jobs:
  deploy:
    runs-on: ubuntu-latest

    steps:
      - name: Deploy to VM
        run: |
          echo "SSH를 통해 VM에서 배포 Script 실행"
```

실제 SSH Action 선택, Registry 인증, Secret 전달 방법은
프로젝트의 CI/CD 정책에 맞춰 별도 구현한다.

중요한 원칙:

```text
Production Secret을 Workflow Log에 echo 하지 않는다.
```

---

# 80. Self-hosted Runner를 사용할 수도 있나요?

가능하다.

구조:

```text
GitHub
  ↓
Self-hosted Runner on VM
  ↓
docker compose pull
docker compose up -d
```

장점:

```text
SSH 배포 구조가 단순해질 수 있음
```

단점:

```text
GitHub Workflow Code가 운영 VM에서 직접 실행됨
Runner 권한 관리가 매우 중요
Public Repository에서 특히 신중해야 함
```

초보자는 처음에는:

```text
GitHub-hosted runner → SSH → VM
```

구조를 이해한 뒤 선택하는 것을 권장한다.

---

# 81. Update 절차

새 Version 배포 전:

```bash
cd /opt/sso-lab
git status
```

운영 서버에 수동 변경이 없어야 한다.

변경 확인:

```bash
git fetch origin
git log --oneline HEAD..origin/main
```

배포:

```bash
git pull --ff-only
docker compose up -d --build
```

또는 Registry 기반:

```bash
docker compose pull
docker compose up -d
```

확인:

```bash
docker compose ps
```

Health:

```bash
curl https://auth.example.com/actuator/health
```

---

# 82. Rollback 준비

배포 전 현재 Commit 기록:

```bash
git rev-parse HEAD
```

예:

```text
abc1234...
```

메모 또는 배포 Log에 저장한다.

문제가 발생하면 이전 Commit으로 돌아간다.

```bash
git checkout <PREVIOUS_COMMIT>
docker compose up -d --build
```

Registry Image Tag를 사용한다면:

```text
latest
```

만 사용하는 것보다:

```text
1.0.0
1.0.1
git-sha
```

와 같이 되돌릴 수 있는 Tag 전략이 좋다.

---

# 83. DB Migration과 Rollback 주의

Application Image만 이전 Version으로 되돌려도
DB Schema가 이미 변경되었다면 동작하지 않을 수 있다.

따라서 배포 전에 확인해야 한다.

```text
이번 배포에 Flyway Migration이 포함되는가?
Backward Compatible한가?
Rollback 시 이전 App이 새 DB Schema에서 동작하는가?
```

Production에서는 Migration을 신중하게 설계한다.

---

# 84. PostgreSQL Backup

가장 단순한 논리 Backup:

```bash
mkdir -p /opt/sso-lab/backups
```

Backup:

```bash
docker compose exec -T postgres \
  pg_dump \
  -U "$POSTGRES_USER" \
  "$POSTGRES_DB" \
  > backups/jb_sso_$(date +%Y%m%d_%H%M%S).sql
```

확인:

```bash
ls -lh backups/
```

Backup 파일에는 개인정보 또는 민감 데이터가 포함될 수 있다.

따라서:

```text
Git Commit 금지
외부 공개 금지
접근 권한 제한
```

---

# 85. Caddy Volume Backup

Caddy 인증서 및 상태는 Docker Volume에 저장될 수 있다.

Volume 확인:

```bash
docker volume ls
```

`docker compose down -v`는 Volume까지 삭제하므로
운영 서버에서는 의미를 이해하지 못한 상태에서 사용하지 않는다.

일반 정지:

```bash
docker compose down
```

과:

```bash
docker compose down -v
```

는 다르다.

`-v`는 데이터 Volume 삭제가 포함될 수 있다.

---

# 86. PostgreSQL Volume 절대 주의

운영 DB가 Docker Volume에 있다면:

```bash
docker volume ls
```

로 확인한다.

다음 명령은 초보자가 Production에서 함부로 실행하면 안 된다.

```bash
docker compose down -v
```

PostgreSQL Volume까지 삭제될 수 있기 때문이다.

운영에서는 Backup 후 작업한다.

---

# 87. 장애 발생 시 가장 먼저 볼 것

순서대로 확인한다.

## 1. VM 살아 있나?

```bash
ping <VM_IP>
ssh <VM_USER>@<VM_IP>
```

## 2. Docker 살아 있나?

```bash
systemctl status docker
```

## 3. Container 상태?

```bash
docker compose ps -a
```

## 4. Caddy?

```bash
docker compose logs --tail=100 caddy
```

## 5. Auth Server?

```bash
docker compose logs --tail=100 auth-server
```

## 6. PostgreSQL?

```bash
docker compose logs --tail=100 postgres
```

## 7. DNS?

```bash
nslookup auth.example.com
```

## 8. HTTPS?

```bash
curl -Iv https://auth.example.com
```

---

# 88. 502 Bad Gateway가 나오는 경우

Caddy 화면은 뜨는데:

```text
502 Bad Gateway
```

이면 Caddy까지 요청은 들어왔지만
뒤쪽 Container에 연결하지 못한 경우가 많다.

확인:

```bash
docker compose ps
```

예를 들어 Caddyfile:

```caddy
reverse_proxy auth-web:80
```

이면:

```text
auth-web
```

이라는 Compose Service Name이 실제 존재하는지 확인한다.

Caddy와 대상 Container가 같은 Docker Network에 있는지도 확인한다.

---

# 89. Connection Refused

예:

```text
connect: connection refused
```

확인할 것:

```text
대상 Process가 실행 중인가?
올바른 Port인가?
Container 내부 Port와 Host Port를 혼동하지 않았는가?
Docker Network가 같은가?
```

예:

```yaml
auth-server:
  expose:
    - "8080"
```

Caddy에서는 같은 Docker Network라면:

```text
auth-server:8080
```

으로 접근한다.

---

# 90. DNS는 맞는데 HTTPS가 안 되는 경우

```bash
nslookup auth.example.com
```

은 맞는데 HTTPS 실패.

다음 확인:

```bash
sudo ss -lntp | grep ':443'
sudo ufw status
docker compose ps
docker compose logs caddy
```

그리고 공유기 443 Forwarding을 다시 확인한다.

---

# 91. 내부에서는 되는데 외부에서 안 되는 경우

VM에서:

```bash
curl http://localhost
```

는 되는데 외부에서 안 된다면 Application 문제보다 Network 문제일 가능성이 크다.

확인:

```text
Router Port Forwarding
Host Firewall
VM Firewall
ISP Port 차단
CGNAT
DNS
```

순으로 본다.

---

# 92. Production Security 기본 Checklist

배포 전 반드시 확인한다.

- [ ] `.env`가 Git에 없다.
- [ ] `secrets/`가 Git에 없다.
- [ ] JWT Private Key가 Git에 없다.
- [ ] PostgreSQL 5432가 인터넷에 열려 있지 않다.
- [ ] `/internal/**`가 외부에서 접근되지 않는다.
- [ ] HTTPS가 적용되어 있다.
- [ ] Production Cookie가 Secure이다.
- [ ] HttpOnly Cookie를 사용한다.
- [ ] Browser localStorage에 Access Token이 없다.
- [ ] Browser sessionStorage에 Refresh Token이 없다.
- [ ] OTP 원문을 Log에 남기지 않는다.
- [ ] TOTP Secret을 Log에 남기지 않는다.
- [ ] Actuator의 민감 Endpoint가 공개되지 않는다.
- [ ] Bootstrap Admin 생성 후 Bootstrap 기능을 끈다.
- [ ] Production에서 Turnstile이 활성화되어 있다.
- [ ] DB Backup 절차가 확인되어 있다.
- [ ] Rollback 방법을 알고 있다.

---

# 93. Git Secret 노출 여부 확인

Commit 전에:

```bash
git status
```

```bash
git diff --cached
```

Secret 파일이 Stage 되어 있는지 확인한다.

추가로:

```bash
git ls-files | grep -E '\.env$|secrets/|\.pem$|\.key$'
```

실제 Secret 파일이 출력되면 Commit하기 전에 확인한다.

`.env.example`이나 Dummy Public Key 등 의도한 파일인지 구분해야 한다.

---

# 94. 실수로 Secret을 Commit했다면

단순히 다음 Commit에서 파일을 삭제하는 것만으로는 충분하지 않을 수 있다.

이미 Git History에 Secret이 남았기 때문이다.

즉시 해야 할 일:

```text
1. 해당 Secret 폐기
2. 새로운 Secret 생성
3. Git History 노출 범위 확인
4. 필요하면 History 정리
5. GitHub Secret Scanning 경고 확인
```

가장 중요한 것은 **노출된 Secret을 계속 사용하지 않는 것**이다.

---

# 95. Domain 변경 시 바꿔야 할 곳

도메인은 Source Code에 하드코딩하지 않는다.

대표 변경 대상:

```text
AUTH_PUBLIC_URL
ADMIN_PUBLIC_URL
HR_PUBLIC_URL
APPROVAL_PUBLIC_URL

Caddy
OIDC issuer
redirect_uri
post_logout_redirect_uri
CORS
Cookie 설정
Frontend API Base URL
OIDC Client Bootstrap
DNS
```

도메인을 변경한 뒤 반드시 SSO를 다시 테스트한다.

---

# 96. Local 개발과 Production 차이

Local 예:

```text
auth.localhost
admin.localhost
hr.localhost
approval.localhost
```

Local에서는 TLS를 생략할 수 있지만
Production은 HTTPS Only를 기준으로 한다.

Local에서만 예외가 허용되는 설정과
Production 설정을 혼동하지 않는다.

예:

```text
Local:
TURNSTILE_ENABLED=false

Production:
TURNSTILE_ENABLED=true
```

---

# 97. Java / Node를 VM에 꼭 설치해야 하나요?

최종 Production이 **Docker Image Build + Docker 실행** 구조라면
운영 VM에 Java와 Node를 직접 설치하지 않아도 된다.

예:

```text
Backend Dockerfile
→ JDK/Gradle Build
→ JRE Runtime Image

Frontend Dockerfile
→ Node Build
→ Static Web Server Image
```

운영 VM은 기본적으로:

```text
Git
Docker
Docker Compose
OpenSSL
curl
```

정도만 있어도 배포 가능하도록 만드는 것이 관리하기 쉽다.

개발 PC에서는 Java 21과 Node.js LTS가 필요하다.

---

# 98. 환경별 설정 파일 권장

예:

```text
application-local.yml
application-prod.yml
```

또는 동등한 Spring Profile을 사용한다.

환경 차이는 다음으로 해결한다.

```text
Spring Profile
Environment Variable
SecretProvider
system_config
Caddy Configuration
Docker Compose Override
```

VM용 별도 Source Branch를 만들지 않는다.

---

# 99. Docker Compose Override를 쓰고 싶다면

예:

```text
docker-compose.yml
docker-compose.vm.yml
```

실행:

```bash
docker compose \
  -f docker-compose.yml \
  -f docker-compose.vm.yml \
  up -d
```

기본 설정은 `docker-compose.yml`,
VM 차이만 `docker-compose.vm.yml`에 둔다.

중복 설정을 지나치게 늘리지 않는다.

---

# 100. 최소 docker-compose.yml 구조 예시

실제 프로젝트 구현에 맞춰 수정해야 하는 **개념 예시**다.

```yaml
services:

  postgres:
    image: postgres:17
    restart: unless-stopped
    environment:
      POSTGRES_DB: ${POSTGRES_DB}
      POSTGRES_USER: ${POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    volumes:
      - postgres_data:/var/lib/postgresql/data
    networks:
      - db-network

  auth-server:
    build:
      context: ./backend/auth-server
    restart: unless-stopped
    env_file:
      - .env
    depends_on:
      - postgres
    networks:
      - internal-network
      - db-network

  admin-server:
    build:
      context: ./backend/admin-server
    restart: unless-stopped
    env_file:
      - .env
    networks:
      - internal-network

  hr-server:
    build:
      context: ./backend/hr-server
    restart: unless-stopped
    env_file:
      - .env
    networks:
      - internal-network

  approval-server:
    build:
      context: ./backend/approval-server
    restart: unless-stopped
    env_file:
      - .env
    networks:
      - internal-network

  auth-web:
    build:
      context: ./frontend/auth-web
    restart: unless-stopped
    networks:
      - public-network

  admin-web:
    build:
      context: ./frontend/admin-web
    restart: unless-stopped
    networks:
      - public-network

  hr-web:
    build:
      context: ./frontend/hr-web
    restart: unless-stopped
    networks:
      - public-network

  approval-web:
    build:
      context: ./frontend/approval-web
    restart: unless-stopped
    networks:
      - public-network

  caddy:
    image: caddy:2
    restart: unless-stopped
    ports:
      - "80:80"
      - "443:443"
      - "443:443/udp"
    volumes:
      - ./infra/caddy/Caddyfile:/etc/caddy/Caddyfile:ro
      - caddy_data:/data
      - caddy_config:/config
    networks:
      - public-network
      - internal-network

networks:
  public-network:
  internal-network:
  db-network:

volumes:
  postgres_data:
  caddy_data:
  caddy_config:
```

> 위 Compose는 구조 이해를 위한 예시다.
> 실제 Port, Health Check, Secret, Dependency, Image 이름은 구현 시 확정한다.

---

# 101. 처음부터 끝까지 실행 순서 요약

초보 개발자는 아래 순서만 기억하면 된다.

```text
01. GitHub Repository 생성
02. Monorepo 기본 폴더 생성
03. .gitignore / .env.example 작성
04. Ubuntu VM 생성
05. Bridged Network 설정
06. VM IP 확인
07. DHCP Reservation
08. SSH 접속 확인
09. Ubuntu Update
10. Git / curl / openssl 설치
11. Docker 설치
12. Docker Compose 확인
13. Repository VM에 Clone
14. 프로젝트 구현
15. .env 생성
16. PostgreSQL Password 생성
17. AES / HMAC Key 생성
18. JWT Signing Key 생성
19. Gmail SMTP Secret 설정
20. Turnstile 설정
21. Bootstrap Admin 설정
22. Public IPv4 확인
23. Domain / Subdomain 준비
24. DNS A Record 설정
25. 공유기 80/443 Forwarding
26. Ubuntu Firewall 80/443 허용
27. Caddyfile 작성
28. docker compose up -d
29. Caddy Log 확인
30. HTTPS 인증서 확인
31. Flyway Migration 확인
32. Health Check
33. OIDC Discovery 확인
34. 최초 회원가입
35. Bootstrap Admin 확인
36. Email OTP Login
37. HR → Approval SSO
38. TOTP 등록/Login
39. Back-Channel Logout
40. Backup
41. Update/Rollback 절차 검증
42. GitHub Actions 배포 자동화
```

---

# 102. 개발 시작 전에 오늘 당장 해야 하는 것

아직 소스가 하나도 없는 현재 시점이라면 아래까지만 먼저 해도 충분하다.

## Step 1

GitHub에:

```text
sso-lab
```

Repository 생성.

## Step 2

Monorepo 기본 Directory 생성.

## Step 3

`.gitignore`와 `.env.example` 작성.

## Step 4

Ubuntu Server VM 생성.

권장:

```text
4 vCPU
8 GB RAM
40 GB Disk
Bridged Network
```

## Step 5

VM에 SSH 접속.

## Step 6

Docker / Docker Compose 설치.

## Step 7

VM에 Repository Clone.

여기까지 완료하면:

```text
개발 PC
      ↓
GitHub
      ↓
Linux VM
      ↓
Docker
```

라는 프로젝트 개발/배포의 기본 뼈대가 완성된다.

도메인, 인증서, Gmail SMTP, Turnstile, Bootstrap Admin은
Application이 실제로 동작하기 시작한 뒤 연결해도 된다.

---

# 103. 전체 구축 완료 판단 기준

아래가 모두 체크되면 VM 환경 구성은 완료된 것이다.

## Infrastructure

- [ ] Linux VM이 정상 실행된다.
- [ ] VM IP가 고정되어 있다.
- [ ] SSH 접속이 된다.
- [ ] Docker가 정상 실행된다.
- [ ] Docker Compose가 정상 실행된다.
- [ ] VM 재부팅 후 Docker가 자동 시작된다.

## Repository

- [ ] GitHub Monorepo가 있다.
- [ ] VM에서 Clone/Pull이 된다.
- [ ] `.gitignore`가 적용되어 있다.
- [ ] 실제 `.env`가 Commit되어 있지 않다.
- [ ] 실제 Secret File이 Commit되어 있지 않다.

## Network

- [ ] Public IPv4 여부를 확인했다.
- [ ] DNS가 Public IP를 가리킨다.
- [ ] Router 80/443 Forwarding이 설정되었다.
- [ ] VM Firewall 80/443이 허용되었다.
- [ ] PostgreSQL은 외부 공개되지 않는다.

## TLS

- [ ] Caddy가 실행된다.
- [ ] HTTP가 HTTPS로 연결된다.
- [ ] Let's Encrypt 인증서가 정상 발급된다.
- [ ] Browser에서 인증서 오류가 없다.

## Application

- [ ] PostgreSQL이 정상이다.
- [ ] Flyway Migration이 성공한다.
- [ ] Auth Server Health가 UP이다.
- [ ] OIDC Discovery가 정상이다.
- [ ] 최초 회원가입이 된다.
- [ ] Bootstrap Admin이 생성된다.
- [ ] Email OTP Login이 된다.
- [ ] TOTP 등록 및 Login이 된다.
- [ ] HR → Approval SSO가 된다.
- [ ] Back-Channel Logout이 된다.

## Operation

- [ ] Container Log를 확인할 수 있다.
- [ ] 서버 재부팅 후 자동 복구된다.
- [ ] PostgreSQL Backup 방법을 검증했다.
- [ ] Update 절차가 있다.
- [ ] Rollback 절차가 있다.
- [ ] Production Secret이 Log에 출력되지 않는다.

---

# 104. 자주 사용하는 명령어 한 장 요약

## VM

```bash
hostname -I
timedatectl
df -h
free -h
```

## Git

```bash
git status
git pull --ff-only
git log --oneline -10
```

## Docker

```bash
docker ps
docker images
docker volume ls
docker network ls
```

## Compose

```bash
docker compose ps
docker compose ps -a
docker compose up -d
docker compose up -d --build
docker compose pull
docker compose restart
docker compose logs --tail=100
docker compose logs -f auth-server
docker compose logs -f caddy
```

## Network

```bash
ip addr
sudo ss -lntp
sudo ufw status
nslookup auth.example.com
curl -Iv https://auth.example.com
```

## Certificate

```bash
openssl s_client \
  -connect auth.example.com:443 \
  -servername auth.example.com \
  </dev/null
```

## Health

```bash
curl https://auth.example.com/actuator/health
```

## OIDC

```bash
curl -s \
  https://auth.example.com/.well-known/openid-configuration \
  | jq
```

---

# 105. 마지막으로 기억할 것

이 프로젝트의 VM 환경 구성에서 가장 중요한 원칙은 다음 다섯 가지다.

```text
1. Secret은 Git에 올리지 않는다.

2. 외부 진입점은 기본적으로 Caddy 80/443만 사용한다.

3. PostgreSQL과 /internal/** API를 인터넷에 직접 공개하지 않는다.

4. Production 인증은 HTTPS를 전제로 한다.

5. 한 번에 CI/CD까지 만들지 말고,
   수동 배포 → 정상 확인 → 자동 배포 순서로 진행한다.
```

이 순서를 따르면 문제가 발생했을 때:

```text
Application 문제인지
Docker 문제인지
VM 문제인지
Network 문제인지
DNS 문제인지
TLS 문제인지
CI/CD 문제인지
```

범위를 하나씩 좁혀가며 확인할 수 있다.

---

# Appendix A. 권장 Repository 최종 구조

```text
sso-lab/
├── backend/
│   ├── auth-server/
│   ├── admin-server/
│   ├── hr-server/
│   └── approval-server/
│
├── frontend/
│   ├── auth-web/
│   ├── admin-web/
│   ├── hr-web/
│   └── approval-web/
│
├── infra/
│   ├── caddy/
│   │   └── Caddyfile
│   ├── docker/
│   └── scripts/
│
├── docs/
│   ├── ARCHITECTURE.md
│   ├── LOCAL_SETUP.md
│   ├── DEPLOYMENT.md
│   ├── SECURITY.md
│   ├── SSO_FLOW.md
│   ├── TEST_GUIDE.md
│   ├── DOMAIN_CHANGE_GUIDE.md
│   ├── GMAIL_SMTP_SETUP.md
│   ├── TURNSTILE_SETUP.md
│   ├── BOOTSTRAP_ADMIN_GUIDE.md
│   ├── SECRET_MANAGEMENT.md
│   ├── VM_SETUP_GUIDE.md
│   └── AWS_SETUP_GUIDE.md
│
├── .github/
│   └── workflows/
│
├── secrets/               # Git 제외
├── backups/               # Git 제외 권장
├── docker-compose.yml
├── docker-compose.vm.yml  # 필요 시
├── .env                   # Git 제외
├── .env.example
├── .gitignore
├── DEVELOPER_SETUP.txt
└── README.md
```

---

# Appendix B. 공식 문서 참고

환경 도구 설치 방법은 버전에 따라 변경될 수 있으므로 실제 구축 시 공식 문서를 우선한다.

- Docker Engine Ubuntu 설치
  https://docs.docker.com/engine/install/ubuntu/

- Docker Compose Plugin 설치
  https://docs.docker.com/compose/install/linux/

- Caddy Automatic HTTPS
  https://caddyserver.com/docs/automatic-https

- Caddy HTTPS Quick Start
  https://caddyserver.com/docs/quick-starts/https

- Caddy Docker 실행 참고
  https://caddyserver.com/docs/running

- GitHub Actions 보안 가이드
  https://docs.github.com/en/actions/security-for-github-actions/security-guides/security-hardening-for-github-actions

- GitHub Self-hosted Runner
  https://docs.github.com/en/actions/hosting-your-own-runners/managing-self-hosted-runners/about-self-hosted-runners

---

# Appendix C. 이 문서의 Master Specification 반영 범위

이 가이드는 SSO Lab Master Specification에서 요구한 VM 환경 가이드의 다음 항목을 반영한다.

```text
권장 Linux 배포판 / VM 사양
VM Network Mode
고정 내부 IP / DHCP Reservation
Docker / Docker Compose
Repository Clone
.env
Docker Secret
PostgreSQL Credential
JWT Signing Key
Email/TOTP/HMAC Key
Gmail SMTP
Cloudflare Turnstile
Bootstrap Admin
도메인 / DNS
80/443 Port Forwarding
Firewall
Caddy
Let's Encrypt
Docker Compose 기동
Flyway
Health Check
최초 회원가입
Bootstrap Admin
SSO
TOTP
Back-Channel Logout
Log
재기동
서버 재부팅
Backup
Update / Deploy
Rollback
장애 대응
CGNAT 대안
GitHub Actions
```
