# PC / Linux VM Setup Guide

이 문서는 처음 repository를 받은 개발자가 같은 source와 immutable image로 Linux VM에 배포하는 실행 순서입니다. 운영 변경은 반드시 별도 승인과 backup 후 수행합니다.

## 1. VM과 network

- Ubuntu 24.04 LTS x86_64를 권장합니다.
- 최소 2 vCPU, RAM 4 GiB, disk 40 GiB; 권장 4 vCPU, RAM 8 GiB, disk 80 GiB입니다.
- Bridged mode는 VM이 LAN 주소를 직접 받아 공유기 forwarding 대상이 단순합니다.
- NAT mode는 Host→VM과 Router→Host의 이중 forwarding이 필요할 수 있습니다.
- VM 내부 주소는 DHCP reservation 또는 고정 주소로 유지합니다. 공인 IPv4는 소스에 기록하지 않고 DNS hostname을 사용합니다.

공유기에서 외부 TCP 80/443을 VM으로 전달합니다. HTTP/3가 필요하면 UDP 443도 전달합니다. SSH 22는 source IP 제한/VPN을 권장합니다. VM firewall은 22(관리망), 80, 443만 필요 범위에 허용하고 PostgreSQL 5432/Backend 8080/Caddy Admin 2019는 열지 않습니다.

CGNAT이면 직접 inbound 80/443과 public ACME가 불가능할 수 있습니다. ISP의 public IPv4, Cloudflare Tunnel 또는 다른 outbound tunnel을 검토하되 primary `Internet → Caddy → Compose` trust model을 임의로 바꾸지 말고 forwarded-header/rate-limit 경계를 다시 설계합니다.

## 2. Docker와 repository

Docker Engine은 Docker 공식 Ubuntu repository 절차로 설치하고 현재 공식 문서를 확인합니다. 완료 확인:

공식 절차: <https://docs.docker.com/engine/install/ubuntu/>

Ubuntu 24.04의 최소 설치 예시는 다음과 같습니다. 배포 시 공식 페이지와 package
version을 다시 확인합니다.

```sh
sudo apt update
sudo apt install -y ca-certificates curl git
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
  -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
sudo tee /etc/apt/sources.list.d/docker.sources >/dev/null <<EOF
Types: deb
URIs: https://download.docker.com/linux/ubuntu
Suites: $(. /etc/os-release && echo "${UBUNTU_CODENAME:-$VERSION_CODENAME}")
Components: stable
Architectures: $(dpkg --print-architecture)
Signed-By: /etc/apt/keyrings/docker.asc
EOF
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo systemctl enable --now docker
```

기존 Docker/container가 있는 host에서는 충돌 package 제거 또는 재설치를 위 명령으로
자동 수행하지 말고 현재 data/daemon 설정을 먼저 backup합니다.

완료 확인:

```sh
docker --version
docker compose version
docker info
```

배포 계정을 `docker` group에 추가하는 것은 root-equivalent 권한임을 인지합니다.
필요하면 `sudo usermod -aG docker <deploy-user>` 후 새 login session에서 권한을
확인합니다. Repository는 다음 형태로 준비합니다.

```sh
sudo install -d -o <deploy-user> -g <deploy-user> -m 0750 /opt/sso-lab
git clone <repository-url> /opt/sso-lab
cd /opt/sso-lab
git checkout --detach <approved-source-commit>
```

운영 `.env`와 `secrets/`는 Git 밖의 파일로 보존합니다.

## 3. `.env`와 네 domain

`.env.example`을 `/opt/sso-lab/.env`로 복사해 non-secret만 확정합니다.

- 네 `*_HOSTNAME`과 네 `*_PUBLIC_URL=https://<hostname>`
- Auth issuer인 `AUTH_PUBLIC_URL`
- HR/Approval/Admin exact `/login/oauth2/code/<client>` redirect URI
- 각 exact post-logout root URI
- PostgreSQL DB/user/JDBC URL의 database/user 일치
- `SPRING_PROFILES_ACTIVE=prod`, `SESSION_COOKIE_SECURE=true`
- Caddy subnet/address와 동일한 `TRUSTED_PROXY_CIDR`
- immutable `IMAGE_REGISTRY`, `IMAGE_NAMESPACE`, `IMAGE_TAG`; `latest` 금지
- Gmail의 non-secret host/port/username/from, Turnstile 공개 Site Key

DNS provider에서 네 hostname을 VM의 현재 public address로 연결합니다. public IP가 바뀌면 DNS만 갱신합니다. [Domain Change Guide](DOMAIN_CHANGE_GUIDE.md)의 issuer/exact URI 체크리스트를 지킵니다.

## 4. Secret 준비

`/opt/sso-lab/secrets`를 0700, 아래 파일을 0600으로 만들고 owner numeric UID/GID를 Backend runtime UID/GID와 일치시킵니다.

```text
email-encryption-key          Base64 32-byte AES key
email-lookup-hmac-key         Base64 independent 32-byte HMAC key
otp-hmac-key                  Base64 independent 32-byte HMAC key
totp-encryption-key           Base64 independent 32-byte AES key
oidc-private-key              Base64 PKCS#8 RSA DER (minimum 2048-bit)
oidc-public-key               matching Base64 X.509 RSA DER
hr-client-secret
approval-client-secret
admin-client-secret
admin-internal-api-secret
turnstile-secret
gmail-app-password
```

PostgreSQL password는 현재 Compose 구조상 Git-ignored `.env`에서 공급합니다. 값을 stdout, command argument, shell history 또는 CI log에 출력하지 않습니다. JWT signing key와 네 symmetric key는 절대 재사용하지 않습니다. 자세한 형식/preflight는 [Secret Management](SECRET_MANAGEMENT.md)와 `scripts/test/validate-phase8-production-secrets.py`를 사용합니다.

Gmail App Password와 Turnstile key는 각각 [Gmail](GMAIL_SMTP_SETUP.md), [Turnstile](TURNSTILE_SETUP.md)을 따릅니다. 최초 Admin은 [Bootstrap Guide](BOOTSTRAP_ADMIN_GUIDE.md)대로 설정합니다.

## 5. Caddy, Compose와 TLS

공개 흐름은 `Internet → Caddy 80/443 → Web/BFF/Auth`입니다. Compose render와 port/network를 먼저 검사합니다.

```sh
cd /opt/sso-lab
docker compose --env-file .env -f docker-compose.yml -f docker-compose.prod.yml config --quiet
docker compose --env-file .env -f docker-compose.yml -f docker-compose.prod.yml config --images
```

Compose가 Caddy 외 host port를 publish하지 않고 `/internal/**`가 Caddy에서 차단되는지 확인합니다. 기존 80/443 listener와 volume/container 상태를 기록한 뒤 승인된 deploy-only workflow 또는 아래와 동등한 절차로 immutable image를 사용합니다.

```sh
docker compose --env-file .env -f docker-compose.yml -f docker-compose.prod.yml pull
docker compose --env-file .env -f docker-compose.yml -f docker-compose.prod.yml up -d --wait --no-build
```

`down -v`는 금지합니다. Caddy log에서 네 Let's Encrypt public CA 인증서 발급을
확인하되 private key를 출력하지 않습니다. HTTP는 HTTPS로 redirect되어야 합니다.

## 6. Migration, health와 최초 사용

Auth 시작 시 Flyway V1-V7이 실행됩니다. 신규 DB는 V1부터 순서대로 적용되고 기존 DB에는 V7만 additive 적용됩니다.

```sh
docker compose --env-file .env -f docker-compose.yml -f docker-compose.prod.yml ps
curl --fail "https://${AUTH_HOSTNAME}/actuator/health"
curl --fail "https://${AUTH_HOSTNAME}/.well-known/openid-configuration"
curl --fail "https://${AUTH_HOSTNAME}/oauth2/jwks"
```

PostgreSQL은 container 내부 `pg_isready`로 확인합니다. 최초 signup과 Bootstrap Admin claim 후 flag를 끄고, HR→Approval SSO, TOTP enrollment/login, Admin 접근, RP/global/back-channel logout을 확인합니다. email/token/session/OTP는 log나 test report에 기록하지 않습니다.

## 7. 운영, backup, update와 rollback

- `restart: unless-stopped`로 재부팅 후 자동 기동되며 Docker enabled와 health를 확인합니다.
- log는 `docker compose logs --since ... <service>`로 제한해 확인하고 redaction을 유지합니다.
- PostgreSQL logical backup, volume 목록, 현재 source commit/image digest, `.env`/Secret metadata checksum을 배포 전에 기록합니다.
- backup은 repository 밖의 접근 제한·암호화 저장소에 두고 restore rehearsal을 합니다.
- Update는 CI 성공 → immutable image publish → protected deploy-only approval → health/SSO/logout smoke 순서입니다.
- Rollback은 직전 immutable tag와 source commit을 재배포합니다. Flyway가 비호환이면 image rollback만 하지 않고 검증된 DB restore runbook을 사용합니다.
- container 재기동은 `docker compose ... restart <service>`로 하고 원인/영향을 먼저 확인합니다.

장애 시 DNS authoritative 결과, VM/LAN 주소, router forwarding, firewall, host 80/443 listener, disk/memory, Docker daemon, container health, Caddy ACME, network, Secret UID/GID/format, PostgreSQL/Flyway 순서로 확인합니다. 상세 deploy state/rollback은 [Deployment](DEPLOYMENT.md)와 [Phase 8 Infra](PHASE8_INFRA.md)를 따릅니다.
