# Cloudflare Turnstile Setup

Cloudflare 계정과 Turnstile widget은 운영 담당자가 직접 준비한다. 이 프로젝트는 실제
Production Site Key/Secret을 생성하거나 저장하지 않는다.

공식 참고 문서:

- https://developers.cloudflare.com/turnstile/get-started/widget-management/dashboard/
- https://developers.cloudflare.com/turnstile/get-started/server-side-validation/
- https://developers.cloudflare.com/turnstile/troubleshooting/testing/

## Production 설정

1. Cloudflare Dashboard의 Turnstile에서 widget을 생성한다.
2. Auth Web의 정확한 production hostname만 allow-list에 등록한다.
3. 공개 Site Key를 실제 Git-ignored `.env`의 `TURNSTILE_SITE_KEY`에 넣는다.
4. Secret Key는 `TURNSTILE_SECRET_KEY` 환경변수 또는 Docker Secret에 넣는다.
5. SecretProvider reference를 설정하고 Turnstile을 활성화한다.

```text
TURNSTILE_ENABLED=true
TURNSTILE_SITE_KEY=<PUBLIC_SITE_KEY>
TURNSTILE_SECRET_PROVIDER=ENVIRONMENT
TURNSTILE_SECRET_REF=TURNSTILE_SECRET_KEY
TURNSTILE_SECRET_KEY=<PRODUCTION_SECRET_NOT_IN_GIT>
```

Docker Secret을 사용할 때는 provider를 `DOCKER_SECRET`으로 바꾸고 reference를
`/run/secrets` 아래의 파일명으로 설정한다. Auth Web container는 시작할 때
`TURNSTILE_ENABLED`와 공개 `TURNSTILE_SITE_KEY`로 `/runtime-config.js`를 생성한다.
따라서 Site Key를 immutable image build argument로 전달하지 않으며 운영 `.env`와
Backend가 동일한 enable flag를 사용한다. Secret Key는 Auth Web 환경, image,
Browser response, JavaScript bundle, build argument, log 또는 Git에 넣지 않는다.

## Local 개발

Turnstile 없이 기능을 개발할 때:

```text
SPRING_PROFILES_ACTIVE=local
TURNSTILE_ENABLED=false
```

이 예외는 Local/Test 전용이다. `prod` profile은 명시적인 override가 없으면 활성화된다.

## 공식 Dummy Test Key

Cloudflare 공식 문서가 제공하는 공개 테스트 credential이다. 실제 운영에 사용하지 않는다.

항상 성공하는 visible widget 조합:

```text
TURNSTILE_SITE_KEY=1x00000000000000000000AA
TURNSTILE_SECRET_KEY=1x0000000000000000000000000000000AA
```

항상 실패하는 조합:

```text
TURNSTILE_SITE_KEY=2x00000000000000000000AB
TURNSTILE_SECRET_KEY=2x0000000000000000000000000000000AA
```

Dummy Site Key와 Dummy Secret Key는 함께 사용해야 한다. Production Secret은 dummy token을
거부하며, Dummy Secret을 Production에 배치하면 안 된다.

## 정상 확인

1. Auth Web에서 Turnstile widget이 표시되는지 확인한다.
2. 정상 widget 완료 후 Email OTP 발송이 진행되는지 확인한다.
3. 누락/실패 token이 `HUMAN_VERIFICATION_FAILED` 일반 오류로 거부되는지 확인한다.
4. Auth Server log와 Frontend bundle에 Secret Key가 없는지 확인한다.
5. 재사용 token과 Cloudflare 장애가 fail-closed 되는지 확인한다.

자동 Unit Test는 `TurnstileClient` test double을 사용해 enabled/disabled/실패를 검증하며
실제 Cloudflare endpoint 또는 운영 Secret에 연결하지 않는다.
