# Production Secret Management

운영 Secret은 Git, image, `.env.example`, 문서, 명령 인자, Browser bundle 및
application log에 기록하지 않는다. PC VM에서는 Auth Server `SecretProvider`와
Docker Compose file-backed Secret을 기본으로 사용한다.

## 준비 원칙

- 각 용도마다 독립적인 random 값을 사용한다.
- crypto key rotation 전에는 기존 key version을 제거하지 않는다.
- private key와 public key를 혼동하지 않는다.
- 실제 `/opt/sso-lab/secrets`는 운영 승인 후 운영자가 직접 생성한다.
- Secret 생성 명령의 stdout을 채팅/CI log에 출력하지 않는다.
- 디렉터리 권한 `0700`, 파일 권한 `0600`, owner는 배포 계정으로 제한한다.

필수 파일 이름과 형식은 `docs/PHASE8_INFRA.md`의 Production Secret 공급 표를
따른다. Client BFF에는 해당 client secret만 mount하며 다른 client나 crypto
Secret을 제공하지 않는다. Admin Server에는 admin client secret과 internal API
secret만 제공한다.

## Rotation

1. 영향 서비스와 지원되는 key version 정책을 확인한다.
2. 새 Secret을 repository 밖에서 생성하고 제한된 경로에 배치한다.
3. crypto key는 새 version을 current로 추가한 뒤 기존 ciphertext/token 수명과
   migration을 고려한다.
4. OIDC signing key는 JWKS 전환 기간과 기존 token TTL을 고려한다.
5. client/internal/Turnstile/Gmail Secret은 양쪽 소비자를 원자적으로 전환한다.
6. 재기동 후 health와 기능 테스트를 수행한 뒤에만 이전 Secret을 폐기한다.

## Audit

Compose `config`, container environment, mount 목록, Git tracked file과 application
log를 검사한다. 검사 보고서에는 발견 여부와 key 이름만 기록하고 값은 절대
복사하지 않는다. Git history에서 실제 Secret이 발견되면 단순 파일 삭제로
끝내지 않고 즉시 폐기/재발급한다.
