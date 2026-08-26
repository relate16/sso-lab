# Bootstrap Admin 운영 가이드

이 문서는 SSO Lab의 최초 `ADMIN` 한 명을 안전하게 생성하는 Phase 5 절차다. Bootstrap은 일반 관리자 추가 기능이 아니며 한 번만 claim할 수 있다.

## 원칙

- 소스 코드와 Git에 관리자 이메일, OTP, Client Secret을 넣지 않는다.
- `BOOTSTRAP_ADMIN_EMAIL`은 Email Lookup HMAC으로만 DB에 보관된다. 평문 이메일은 bootstrap 상태 테이블에 저장하지 않는다.
- 지정 이메일 소유자가 정상 Signup Email OTP 검증을 완료했을 때만 `ADMIN` 역할을 받는다.
- DB의 bootstrap 상태가 claim된 뒤에는 재실행해도 다른 사용자를 추가 관리자로 만들지 않는다.
- 기존 bootstrap 상태의 이메일을 다른 값으로 바꾸어 재초기화하는 동작은 실패한다.

## 테스트 환경 준비

테스트 전용 `.env.test` 또는 로컬의 Git 비추적 환경 설정에 다음 값을 넣는다.

```dotenv
BOOTSTRAP_ADMIN_ENABLED=true
BOOTSTRAP_ADMIN_EMAIL=bootstrap-owner@example.com
```

위 주소는 예시다. 테스트 환경에서 실제로 OTP를 확인할 수 있는 주소를 사용한다. 운영 `.env`를 테스트 목적으로 변경하지 않는다.

## 최초 claim

1. `auth-server`와 PostgreSQL을 기동한다.
2. 지정한 이메일로 일반 회원가입을 시작한다.
3. 이메일 OTP를 정상 검증해 회원가입을 완료한다.
4. Admin OIDC Client로 로그인한다.
5. Admin Web 접근과 `ADMIN` 역할을 확인한다.
6. `BOOTSTRAP_ADMIN_CLAIMED` Audit 이벤트가 한 번 기록되었는지 확인한다.

Bootstrap은 Passwordless Signup 검증을 우회하지 않는다. 하드코딩 계정, 고정 OTP 또는 개발용 인증 bypass는 제공하지 않는다.

## claim 후 정리

claim 성공을 확인한 뒤 다음 배포부터는 반드시 비활성화한다.

```dotenv
BOOTSTRAP_ADMIN_ENABLED=false
BOOTSTRAP_ADMIN_EMAIL=
```

서비스 재기동 후 기존 관리자는 유지되며 새 bootstrap claim은 발생하지 않아야 한다. 운영 환경에서 이메일 평문 값을 장기간 환경 변수로 남겨두지 않는다.

## 추가 관리자

두 번째 이후 관리자는 기존 `ADMIN`이 Admin Web에서 검증된 사용자에게 `ADMIN` 역할을 부여한다. 최소 한 명의 ACTIVE ADMIN은 항상 남아야 하므로 마지막 ACTIVE ADMIN의 suspend 또는 역할 제거는 거부된다.

## 문제 확인

- `bootstrap admin state is not initialized`: Flyway V5 적용 여부와 `auth-server` 시작 완료 여부를 확인한다.
- `bootstrap admin configuration cannot replace existing bootstrap state`: 이미 생성된 상태를 다른 이메일로 덮어쓰려 한 것이다. DB를 수동 수정하지 말고 기존 관리자 정책을 사용한다.
- 지정 이메일 사용자가 `USER`만 가진 경우: 정규화된 이메일이 설정과 정확히 일치했는지, OTP 검증 전에 bootstrap이 활성화되었는지, 상태가 이미 claim되지 않았는지 확인한다.

DB의 `bootstrap_admin_state`, 역할, Audit 데이터를 수동으로 삭제해 Bootstrap을 다시 여는 작업은 표준 복구 절차가 아니다. 운영 복구가 필요하면 별도의 승인과 감사 가능한 변경 절차를 사용한다.
