# Gmail SMTP Setup

Phase 3의 mail abstraction에 Phase 8 운영 adapter를 연결한다. 소규모
포트폴리오 배포에서 별도 mail server를 운영하지 않고 Gmail의 인증된 SMTP와
STARTTLS를 사용할 수 있기 때문에 선택했다. Gmail 정책과 계정 요건은 변경될
수 있으므로 운영 적용 시 공식 Google 계정 안내를 다시 확인한다.

## 준비

1. 운영 전용 Gmail/Google Workspace 계정과 발신 주소를 결정한다.
2. 계정에 필요한 보안 설정을 완료하고 SMTP용 app password를 발급한다.
3. app password는 `/opt/sso-lab/secrets/gmail-app-password`에만 저장한다.
4. `.env`에는 비밀이 아닌 설정만 둔다.

```text
GMAIL_SMTP_ENABLED=true
GMAIL_SMTP_HOST=smtp.gmail.com
GMAIL_SMTP_PORT=587
GMAIL_SMTP_USERNAME=<account>
GMAIL_SMTP_FROM=<verified-from-address>
GMAIL_PASSWORD_PROVIDER=DOCKER_SECRET
GMAIL_PASSWORD_REF=gmail-app-password
```

`GMAIL_APP_PASSWORD` inline 환경 변수는 운영에서 비워 둔다. app password를
소스, `.env.example`, CI output 또는 문서에 넣지 않는다.

## 전송 보안과 오류

- adapter는 SMTP AUTH와 STARTTLS required를 사용한다.
- connection/read/write timeout을 적용한다.
- OTP와 email 원문, credential을 로그에 남기지 않는다.
- Local/Test profile은 실제 Gmail 전송을 사용하지 않는다.
- 인증 실패 시 username/from 일치 여부, app password 상태, 계정 정책을
  Secret 값을 출력하지 않고 점검한다.
- timeout이면 VM DNS/outbound TCP 587, provider 장애 및 계정 제한을 확인한다.

운영 smoke test는 승인된 테스트 수신자에게 단일 OTP 요청을 보내 도착과 만료를
확인한다. OTP 값 자체는 test report나 log에 기록하지 않는다.
