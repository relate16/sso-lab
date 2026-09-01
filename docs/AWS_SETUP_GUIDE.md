# AWS Setup Guide

AWS는 v1의 Primary 배포 대상이 아니라 향후 이전 가이드입니다. 이 문서는 유료 리소스를 생성하지 않으며, 실제 생성 전 사용자가 비용과 보안 변경을 승인해야 합니다.

> AWS Free Tier 및 Credit 정책은 변경될 수 있으므로 실제 배포 시 AWS Console의 현재 과금 정책을 확인할 것.

## 현재 지원 경계

같은 source와 같은 immutable Docker image를 사용합니다. v1 코드에 구현된 `SecretProvider`는 `ENVIRONMENT`와 `DOCKER_SECRET`입니다. `AwsSsmSecretProvider`는 아직 구현되지 않은 Future Work이므로 현재 값을 `AWS_SSM`으로 설정해 활성화할 수 없습니다. AWS 배포 전에는 다음 중 하나를 명시적으로 승인해야 합니다.

1. 최소권한 bootstrap 과정이 SSM `SecureString`을 `/run/secrets` 대응 file로 materialize하고 기존 `DOCKER_SECRET` provider를 사용하거나,
2. AWS SDK, IAM 인증, timeout/cache/redaction test가 포함된 `AwsSsmSecretProvider` adapter를 별도 구현합니다.

Secret 값을 user-data, AMI, repository, Compose output 또는 GitHub log에 넣으면 안 됩니다. Domain/business service에는 AWS SDK를 섞지 않습니다.

## 1. Account, Region과 비용

AWS account에 MFA와 billing alarm/budget을 먼저 설정합니다. 사용자에 가까우며 필요한 서비스가 있는 Region을 선택하고 EC2, EBS, public IPv4/Elastic IP, data transfer, Route 53, CloudWatch, SSM advanced parameter, RDS 비용을 현재 pricing에서 확인합니다.

소규모 기준은 Ubuntu LTS EC2, 최소 2 vCPU/4 GiB RAM, gp3 40 GiB(권장 4 vCPU/8 GiB/80 GiB)입니다. 종료 시 EBS/EIP/snapshot/RDS가 남아 과금될 수 있습니다.

## 2. EC2, storage와 접근

1. 최신 Ubuntu LTS x86_64 AMI로 EC2를 생성합니다.
2. encrypted gp3 EBS와 삭제/backup 정책을 결정합니다.
3. DHCP public IPv4는 재시작 때 바뀔 수 있습니다. Elastic IP는 DNS 안정성이 있지만 미사용 과금 정책을 확인합니다.
4. Security Group inbound는 관리자 source IP의 TCP 22 또는 Session Manager만, public TCP 80/443과 필요 시 UDP 443만 허용합니다. 5432/8080/2019는 허용하지 않습니다.
5. SSH private key는 GitHub/repository에 넣지 않습니다. 가능하면 EC2 Instance Connect나 SSM Session Manager와 IAM audit을 사용합니다.
6. OS firewall을 사용할 때 Security Group과 이중 정책을 문서화합니다.

Session Manager에는 `AmazonSSMManagedInstanceCore` 수준을 검토하되 광범위 managed policy를 그대로 쓰기보다 실제 필요 action/resource로 축소합니다.

## 3. Docker와 source

Docker 공식 Ubuntu 절차로 Engine/Compose plugin을 설치하고 `docker info`, `docker compose version`을 확인합니다. 승인된 repository commit을 `/opt/sso-lab`에 clone하고 `.env.example`에서 Git-ignored `.env`를 작성합니다.

```text
SPRING_PROFILES_ACTIVE=prod
SESSION_COOKIE_SECURE=true
AUTH_PUBLIC_URL=https://<auth-hostname>
IMAGE_TAG=<immutable-tag-not-latest>
```

네 hostname, exact redirect/post-logout URI, trusted Caddy subnet은 VM 가이드와 동일합니다. 환경 차이는 profile/env/SecretProvider/system_config/Caddy/Compose override에만 둡니다.

## 4. SSM SecureString과 IAM

공식 참고:

- <https://docs.aws.amazon.com/systems-manager/latest/userguide/what-is-a-parameter.html>
- <https://docs.aws.amazon.com/systems-manager/latest/userguide/parameter-store-setting-up.html>
- <https://docs.aws.amazon.com/systems-manager/latest/userguide/setup-instance-permissions.html>

Parameter hierarchy 예시는 이름만 사용합니다.

```text
/sso-lab/prod/email-encryption-key
/sso-lab/prod/oidc-private-key
/sso-lab/prod/hr-client-secret
```

값을 CLI argument로 전달하면 shell history/process list에 남을 수 있으므로 AWS Console의 보호 입력이나 표준입력/승인된 secret bootstrap 도구를 사용합니다. 각 parameter는 `SecureString`과 고객 관리 KMS key 사용 여부를 결정합니다.

EC2 instance role에는 선택한 prefix의 `ssm:GetParameter`/`ssm:GetParameters`와 해당 KMS key의 `kms:Decrypt`만 허용하고 `Resource`를 account/region/path ARN으로 제한합니다. `ssm:GetParametersByPath`가 불필요하면 허용하지 않습니다. write/delete와 다른 environment path는 금지합니다.

최소 정책 골격은 실제 account/region/KMS key로 제한해 작성합니다. 아래에는 Secret 값이
없습니다.

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": ["ssm:GetParameter", "ssm:GetParameters"],
      "Resource": "arn:aws:ssm:<region>:<account>:parameter/sso-lab/prod/*"
    },
    {
      "Effect": "Allow",
      "Action": "kms:Decrypt",
      "Resource": "arn:aws:kms:<region>:<account>:key/<key-id>"
    }
  ]
}
```

상위 path의 `GetParametersByPath`는 하위 parameter 접근 범위를 넓힐 수 있으므로 현재
목록 조회가 필요하지 않은 배포에는 부여하지 않습니다. Parameter 이름/metadata는
암호화 대상이 아니므로 email 등 개인정보를 이름이나 tag에 넣지 않습니다.

현재 v1에서는 `AwsSsmSecretProvider` activation 값이 없습니다. adapter 구현 시에만 enum/config에 `AWS_SSM`, region, parameter reference를 추가하고 EC2 instance profile credential chain을 사용합니다. static AWS access key를 `.env`에 두지 않습니다. timeout/fail-closed/cache/rotation/log-redaction과 Local unit test를 먼저 갖춘 뒤 문서를 갱신해야 합니다.

현재 배포 가능한 대안은 승인된 bootstrap이 12개 SSM 값을 0700 directory의 0600 file로 원문 출력 없이 쓰고 owner UID/GID를 Backend와 맞춘 뒤 기존 Docker Secret reference를 사용하는 것입니다. instance 종료/재생성 때 재실행되며 temp file과 user-data log에 값을 남기지 않아야 합니다.

## 5. Application Secret과 외부 서비스

- OIDC private: Base64 PKCS#8 RSA DER, public: matching Base64 X.509 DER
- Email/TOTP AES key, email/OTP HMAC key는 목적별 독립 32-byte material
- HR/Approval/Admin client secret과 Admin Internal secret은 각각 독립
- SMTP App Password와 Turnstile Secret은 Auth에만 공급
- Turnstile Site Key와 Gmail host/username/from은 non-secret env

AWS KMS direct crypto는 v1에 구현되지 않았습니다. JWT/AES key 처리 형식을 바꾸지 않습니다.

## 6. DNS, Caddy, Compose

Route 53 또는 기존 DNS에서 네 hostname을 Elastic/Public IP로 연결합니다. Caddy 80/443만 public이고 TLS는 public CA를 사용합니다. Security Group, Caddy hostname, issuer, exact redirect/logout URI가 같은 값을 사용해야 합니다.

Compose render 후 GHCR immutable image digest와 port/network를 확인하고 `up -d --wait --no-build`합니다. PostgreSQL은 같은 EC2의 encrypted EBS-backed Docker volume을 기본 예제로 사용하며 Flyway V1-V7을 적용합니다. RDS로 옮길 때는 private subnet/Security Group, TLS JDBC, credential provider, backup/maintenance/latency와 비용을 별도 설계하고 Auth만 DB에 연결합니다.

Health는 네 Backend, PostgreSQL, Caddy, OIDC discovery/JWKS를 확인합니다. Bootstrap Admin, HR→Approval SSO, TOTP, back-channel/global logout smoke를 수행합니다.

## 7. CI/CD 선택

- GitHub-hosted runner SSH: 설정이 단순하지만 inbound SSH와 deploy key/known_hosts 관리가 필요합니다.
- Self-hosted runner: private 접근이 쉽지만 runner 격리, patching, credential persistence와 repository trust 책임이 큽니다.
- SSM Run Command/CodeDeploy: SSH inbound를 줄일 수 있지만 IAM과 artifact verification을 추가해야 합니다.

어느 방식이든 protected `production` environment approval, concurrency, immutable tag/digest 검증, `.env`/Secret 비전송, `down -v` 금지, rollback state 보존을 유지합니다.

## 8. Backup, reboot, update, rollback

PostgreSQL logical backup과 EBS snapshot의 일관성/restore를 검증하고 Caddy data, source commit, image digest, volume metadata를 기록합니다. Secret backup과 DB backup의 접근 권한을 분리합니다. 재부팅 뒤 Docker enabled, `restart: unless-stopped`, disk mount, health와 DNS를 확인합니다.

Update는 CI/test → immutable publish → approval → deploy → Flyway/health/SSO smoke입니다. Rollback은 이전 image tag와 호환 DB restore plan을 사용하며 volume 삭제는 하지 않습니다. EC2 재생성은 DNS/EIP, encrypted EBS attach, instance role, SSM bootstrap, Docker network와 Caddy 인증서 상태를 다시 확인합니다.

로그는 CloudWatch Agent 또는 제한된 Docker log 수집을 사용할 수 있으나 email/OTP/token/Secret redaction을 보존합니다. 리소스 정리 시 EC2뿐 아니라 unattached EBS, snapshot, Elastic IP, load balancer, NAT Gateway, RDS, Route 53, CloudWatch retention과 SSM advanced parameter 과금을 확인합니다.

## 환경 차이 요약

| 항목 | Local/PC VM | AWS |
|---|---|---|
| Secret | Docker Secret / Env | SSM SecureString 권장, v1은 승인된 file materialization 필요 |
| Provider | Docker/Environment | 향후 AwsSsmSecretProvider adapter |
| Public IP | 가정용 회선/DHCP | EC2 Public/Elastic IP |
| Firewall | Router + VM firewall | Security Group + OS firewall |
| TLS | Caddy + Let's Encrypt | Caddy + Let's Encrypt |
| DB | Docker PostgreSQL | Docker PostgreSQL 기본, RDS 확장 |
| 배포 | GitHub Actions → VM | SSH/SSM/self-hosted runner 중 승인된 방식 |
| 주요 이슈 | CGNAT | VPC/IAM/비용 |

AWS 실환경 검증은 계정과 유료 리소스 승인이 없어 수행하지 않았습니다. 실행 전 현재 AWS 공식 문서, 가격, AMI와 Docker 설치 절차를 다시 검증해야 합니다.
