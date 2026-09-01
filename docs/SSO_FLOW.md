# SSO and Logout Flows

## HR 최초 로그인: Email OTP

```mermaid
sequenceDiagram
  participant U as Browser
  participant H as HR BFF
  participant A as Auth Server
  participant M as Mail Adapter
  U->>H: HR 접근
  H->>A: Authorization Code + PKCE S256
  A->>U: Passwordless UI
  U->>A: userId, Email OTP 선택
  A->>M: OTP 발송 (값은 log 금지)
  U->>A: OTP 검증
  A->>A: Auth SSO Session 생성
  A-->>H: exact redirect URI의 code
  H->>A: code + verifier 교환
  H-->>U: HttpOnly BFF Session cookie
```

Email OTP 응답은 계정 존재 여부를 노출하지 않는 공통 형태이며 만료, 재전송 간격, 시도 횟수, 재사용 방지를 적용합니다.

## TOTP 로그인

```mermaid
sequenceDiagram
  participant U as Browser/Authenticator
  participant A as Auth Server
  U->>A: userId, TOTP 선택
  U->>A: 현재 TOTP code
  A->>A: 암호화된 secret 복호화 후 time-window 검증
  A->>A: replay/attempt/rate-limit 검사
  A-->>U: Auth Session
```

TOTP가 등록된 사용자만 선택할 수 있습니다. Recovery Code는 일반 로그인에 사용하지 않고 TOTP 복구 흐름에서 한 번만 소비합니다.

## HR에서 Approval로 SSO

```mermaid
sequenceDiagram
  participant U as Browser
  participant H as HR BFF
  participant P as Approval BFF
  participant A as Auth Server
  U->>H: 기존 HR Session 사용
  U->>P: Approval 접근
  P->>A: Authorization request + PKCE
  A->>A: 기존 Auth SSO Session 확인
  A-->>P: 추가 OTP 없이 authorization code
  P->>A: token exchange
  P-->>U: Approval BFF Session
```

새 ID Token의 `sub`, `preferred_username`, `roles`, `groups`, `amr`, `acr`은 Auth가 발급 시점에 계산합니다.

## RP-Initiated Logout

```mermaid
sequenceDiagram
  participant U as Browser
  participant B as Client BFF
  participant A as Auth Server
  U->>B: CSRF 보호 logout
  B->>A: end_session + id_token_hint + exact post_logout_redirect_uri
  A->>A: Auth/OAuth 폐기
  A-->>B: 등록 URI로 redirect
  B->>B: local BFF Session 폐기
```

## Back-Channel Logout

```mermaid
sequenceDiagram
  participant A as Auth Server
  participant H as HR BFF
  participant P as Approval BFF
  participant D as Admin BFF
  A->>H: signed logout_token (sid/sub/events)
  A->>P: signed logout_token
  A->>D: signed logout_token
  H->>H: signature/iss/aud/exp/events 검증 후 Session 폐기
  P->>P: 검증 후 Session 폐기
  D->>D: 검증 후 Session 폐기
```

Browser cookie 삭제에 의존하지 않습니다. 잘못된 token은 Session을 종료하지 않습니다. 전송은 bounded best-effort이며 durable outbox/retry는 v1 범위가 아닙니다.

## Global Logout

```mermaid
flowchart LR
  Request[Authenticated CSRF request] --> AuthSession[현재/대상 Auth Sessions 폐기]
  Request --> OAuth[authorization, access/refresh token 폐기]
  Request --> BackChannel[모든 BFF에 logout token]
  BackChannel --> BFF[HR/Approval/Admin Sessions 폐기]
```

Global Logout 후 기존 Refresh Token으로 Session을 되살릴 수 없어야 합니다.

## Admin Login과 Email Re-auth

```mermaid
sequenceDiagram
  participant U as Admin Browser
  participant D as Admin BFF
  participant A as Auth Server
  U->>D: OIDC Login
  D->>A: Code + PKCE
  A-->>D: ADMIN role claim
  D->>D: 서버가 ADMIN authority 검증
  U->>D: 민감 작업/Email reveal
  D->>A: Re-auth Email OTP 시작
  U->>D: OTP proof
  D->>A: proof 검증
  A-->>D: 5분 fresh re-auth 상태
  D->>A: 허용된 민감 작업
```

React가 전달한 role이나 UI 확인 문구는 authorization 근거가 아닙니다.
