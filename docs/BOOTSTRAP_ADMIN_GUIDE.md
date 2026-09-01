# Bootstrap Admin Guide

Bootstrap Admin은 최초 운영 구성에서 ADMIN 사용자가 한 명도 없을 때만 사용하는 one-shot claim입니다. 하드코딩된 관리자 계정이나 password를 만들지 않습니다.

## 설정

Git-ignored 환경 파일에만 다음을 둡니다.

```text
BOOTSTRAP_ADMIN_ENABLED=true
BOOTSTRAP_ADMIN_EMAIL=<OTP를 수신할 정확한 최초 관리자 email>
```

Email은 Auth의 기존 AES-256-GCM/HMAC 정책으로 처리됩니다. 실제 주소를 repository, 문서, log 또는 Audit metadata에 복사하지 않습니다.

## 최초 가입

1. 아직 ACTIVE ADMIN이 없고 bootstrap claim이 소비되지 않았는지 확인합니다.
2. 설정된 email로 정상 Passwordless signup과 Email OTP 검증을 완료합니다.
3. Auth가 해당 User에 ADMIN Role을 부여하고 claim을 consumed 상태로 기록합니다.
4. Admin OIDC 로그인 후 서버가 검증한 ADMIN authority로 Admin API 접근이 가능한지 확인합니다.
5. 즉시 `BOOTSTRAP_ADMIN_ENABLED=false`로 변경하고 승인된 배포 절차로 재기동합니다.

Flag를 다시 true로 하거나 서버를 재시작해도 consumed claim은 새 Admin을 만들지 않습니다. Bootstrap Admin을 Hard Delete해도 같은 계정이 자동 복원되지 않습니다.

## 복구와 보호

- 마지막 ACTIVE ADMIN은 자기 suspend, ADMIN role 제거, Hard Delete가 차단됩니다.
- 관리자를 추가할 때는 기존 ADMIN이 일반 사용자의 Role을 승인된 Admin API로 변경합니다.
- 모든 ADMIN을 잃었다면 DB를 직접 수정하지 말고 백업 복구와 보안 사고 절차를 수행합니다. Master에 별도 emergency-admin bypass는 없습니다.
- Admin email reveal과 민감 작업은 fresh email re-authentication 정책을 계속 적용합니다.

## 확인

Audit에는 bootstrap claim/role 동작의 actor, action, target UUID, timestamp와 결과만 남깁니다. OTP, email 원문, token, Secret은 남기지 않습니다. 자동 PostgreSQL test는 one-shot, 재시작 비복원과 마지막 ACTIVE ADMIN 보호를 검증합니다.
