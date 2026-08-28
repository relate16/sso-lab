# PC VM Deployment

이 가이드는 SSO Lab의 Primary 배포 환경인 Linux PC VM용이다. 실제 운영 적용은
변경 승인 후에만 수행한다. 현재 테스트 경로는 `/opt/sso-lab-test`, 운영 경로는
`/opt/sso-lab`이며 서로의 `.env`, `secrets`, volume을 공유하지 않는다.

## 사전 조건

- Linux VM에서 Docker Engine과 `docker compose`가 정상 동작해야 한다.
- VM에 최소 4 GB RAM과 애플리케이션 image/volume을 위한 여유 disk를 권장한다.
- VM의 내부 주소는 공유기 DHCP reservation으로 고정하는 것을 권장한다.
- 공인 IPv4가 변경될 수 있으므로 DNS와 SSH는 hostname을 사용한다.
- 네 개의 서비스 hostname, 외부 TCP 80/443 도달성, 운영 Secret 및 immutable
  image tag가 준비되어야 한다.
- CGNAT 환경은 일반 port forwarding으로 inbound 80/443을 제공할 수 없다.
  이 경우 Cloudflare Tunnel 같은 outbound tunnel을 별도 설계할 수 있지만,
  현재 Primary Caddy architecture와 trusted-proxy 정책을 그대로 재사용하면 안 된다.

## Release와 deploy workflow

- `.github/workflows/release-images.yml`은 새 immutable image tag를 만드는 publish
  전용 workflow다. 동일 tag가 GHCR에 이미 있으면 실패하며 deploy는 수행하지 않는다.
- `.github/workflows/deploy-existing-release.yml`은 이미 존재하는 8개 image manifest를
  먼저 확인한 뒤 protected `production` environment 승인 후에만 배포한다. image를
  build, push 또는 overwrite하는 단계는 없다.
- deploy 입력은 immutable `image_tag`와 그 image를 만든 40자리
  `image_source_commit`이다. `latest`는 허용하지 않는다. 배포할 Compose/Caddy source는
  workflow를 실행한 `main`의 `GITHUB_SHA`로 별도 고정하며 운영 Compose는 입력 tag를
  강제로 사용한다.
- 운영 `.env`와 `secrets/`는 서버에 계속 남으며 GitHub runner로 복사하거나 출력하지
  않는다.
- deploy workflow는 운영 Caddy를 중지하기 전에 12개 Secret의 runtime UID/GID
  접근성과 암호화 키 형식을 검사한다. `oidc-private-key`는 Base64 PKCS#8 DER,
  `oidc-public-key`는 일치하는 Base64 X.509 DER이어야 한다.

GitHub Environment에는 다음 값을 설정한다.

| 구분 | 이름 | 값/용도 |
|---|---|---|
| Environment | `production` | Required reviewers와 main branch 배포 정책 적용 |
| Variable | `DEPLOY_HOST` | `today-sso.duckdns.org` |
| Variable | `DEPLOY_USER` | `today` |
| Variable | `DEPLOY_PATH` | `/opt/sso-lab` |
| Variable | `DEPLOY_STATE_PATH` | `/home/today/.local/state/sso-lab` |
| Variable | `TEST_CADDY_CONTAINER` | `sso-lab-test-caddy-1` |
| Secret | `DEPLOY_SSH_PRIVATE_KEY` | 전용 SSH private key 원문 |
| Secret | `DEPLOY_KNOWN_HOSTS` | 사전에 검증한 ED25519 host key entry |

`GITHUB_TOKEN`의 `packages: read` 권한으로 manifest를 확인하므로 별도 GHCR PAT를
workflow secret으로 두지 않는다.

## 승인 후 최초 배치 순서

1. 승인된 release의 Git commit에 포함된 Compose/Caddy 파일을 `/opt/sso-lab`에
   checkout한다.
2. `.env.example`을 참고해 Git-ignored `/opt/sso-lab/.env`를 작성한다.
3. `/opt/sso-lab/secrets`를 `0700`, 각 Secret 파일을 `0600`으로 생성한다.
   file-backed Compose Secret은 Host numeric ownership을 유지하므로 `.env`의
   `BACKEND_RUNTIME_UID`/`BACKEND_RUNTIME_GID`를 Secret 파일의 `%u:%g`와 일치시킨다.
4. DNS 네 hostname이 VM 공인 주소를 가리키는지 확인한다.
5. 공유기 port forwarding과 VM firewall에서 외부 80/443만 Caddy로 허용한다.
6. Compose model을 render하고 Secret/URL/port 경계를 검토한다.
7. deploy-only workflow가 기존 image를 pull하고 `up -d --wait --no-build`로
   기동한다.
8. Flyway, health, TLS, OIDC discovery/JWKS, SSO/logout smoke test를 수행한다.

실행 명령은 `docs/PHASE8_INFRA.md`의 Compose 실행 모델을 따른다. `down -v`는
운영 절차에 사용하지 않는다.

## 정상 확인

```sh
docker compose --env-file .env -f docker-compose.yml -f docker-compose.prod.yml ps
curl --fail https://${AUTH_HOSTNAME}/actuator/health
curl --fail https://${AUTH_HOSTNAME}/.well-known/openid-configuration
curl --fail https://${AUTH_HOSTNAME}/oauth2/jwks
docker compose --env-file .env -f docker-compose.yml -f docker-compose.prod.yml \
  exec -T postgres pg_isready -U "${POSTGRES_USER}" -d "${POSTGRES_DB}"
```

`docker inspect`로 PostgreSQL과 Backend에 Host port binding이 없고 Caddy만
80/443을 publish하는지 확인한다. 로그를 수집할 때 Secret, email, OTP, token을
출력하거나 검색 결과에 포함하지 않는다.

## Update

1. 현재 source commit, image/container, volume/network 상태와 DB backup 상태를
   기록한다.
2. CI에서 test/build/image publish가 완료된 immutable tag를 선택한다.
3. deploy-only workflow에 image tag와 image source commit을 입력하고 production
   environment approval을 거쳐 `pull` 후 `up -d --wait --no-build`한다.
4. health/Flyway/OIDC/SSO/logout smoke test를 반복한다.

publish workflow의 `image_tag`를 비우면 `sha-<commit>`을 사용한다. Semantic
release는 `vMAJOR.MINOR.PATCH` 형식으로 입력하며, workflow는 GHCR에 같은 tag가 이미
있으면 덮어쓰지 않고 실패한다. deploy-only workflow는 입력 tag를 Compose process
environment로 강제하므로 서버의 Secret이나 `.env` 값을 GitHub에 전달하지 않는다.

## Backup

최소 PostgreSQL logical backup과 Caddy data volume의 복구 가능성을 검증한다.
backup 파일은 repository 밖의 접근 제한 저장소에 두고 암호화한다. Secret
backup과 DB backup은 별도 접근 정책으로 관리한다.

## Rollback

deploy workflow는 실행별 state 디렉터리에 변경 전 source commit, container,
volume/network 목록과 실패 로그를 권한 제한 상태로 보존한다. 최초 운영 기동 중
실패하고 테스트 Caddy가 실행 중이었던 경우 Production stack을 volume 삭제 없이
내리고 테스트 Caddy를 다시 시작하며 source checkout도 변경 전 commit으로 되돌린다.

애플리케이션 rollback은 직전 immutable image tag와 image source commit을 deploy-only
workflow에 다시 입력한다. Flyway migration이 이전 image와 비호환이면 image만
되돌리지 말고 검증된 DB restore/runbook을 따른다. volume 삭제는 rollback이 아니다.
구체 명령은 `docs/PHASE8_INFRA.md`를 참조한다.

## 재부팅 및 장애 점검

Compose 서비스는 `restart: unless-stopped`로 재기동된다. 재부팅 후 Docker,
container health, Caddy certificate, DNS, VM 내부 주소, 공유기 port forwarding,
disk/memory, PostgreSQL과 Flyway 순서로 확인한다.
