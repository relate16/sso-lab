# Production Status — v1.1.0

기록일: 2026-09-01

이 문서는 현재 SSO Lab Production 배포의 최종 운영 기준을 기록한다. 실제 Secret,
개인정보, token 및 credential 원문은 포함하지 않는다. 배포 절차와 일반 rollback
runbook은 [Deployment](DEPLOYMENT.md)를 함께 따른다.

## 1. 현재 Production 기준

| 항목 | 현재 값 |
|---|---|
| Application release | `v1.1.0` — immutable GHCR image 8개 |
| Source commit | `32604d9bfb8d56b9dad6ef4af02741af7c188900` |
| Deploy workflow | `deploy-existing-release.yml` run `33461384791` — success |
| Database | PostgreSQL 17, Flyway V1~V7 성공, failed migration 0 |
| Runtime | PostgreSQL/Caddy/Backend/Frontend 10개 container 모두 healthy |
| Public edge | Production Caddy의 80/tcp, 443/tcp, 443/udp만 공개 |
| Test edge | `sso-lab-test-caddy-1` 종료 상태 |

`/opt/sso-lab`의 source와 Production Compose는 위 commit을 사용한다. Auth, Admin,
HR, Approval의 Backend/Frontend image 8개는 모두 `v1.1.0`이며 기존 `v1.0.0`
image도 rollback 증거로 보존되어 있다.

## 2. Database와 pre-v1.1.0 backup

V7은 다음 additive 변경을 포함한다.

- `auth.pending_email_changes`와 active lookup index
- `auth.email_otp_challenges.pending_email_change_id` 및 FK
- Email 변경 context constraint
- `USERNAME_CHANGED`, `EMAIL_CHANGED`, `ACCOUNT_DELETED` Audit event 허용

배포 전 V1~V6 Production DB의 logical backup을 생성하고 Production과 분리된 임시
PostgreSQL 17에서 full restore를 검증했다.

```text
backup ID:   20260901T014319Z-pre-v1.1.0
backup path: /home/today/.local/state/sso-lab/backups/postgres/20260901T014319Z-pre-v1.1.0/
dump:        today_sso-v1-v6.dump
format:      PostgreSQL custom format
```

Backup checksum, Flyway V1~V6 이력, 주요 table row count와 restore 결과가 함께
보존되어 있다. 격리 restore에서 V1~V6 일치, V7 미존재 및 주요 row count 일치를
확인했으므로 이 backup은 실제 V1~V6 복원 기준으로 사용할 수 있다.

## 3. Rollback 주의사항

V7 적용 뒤 `v1.0.0` image만 다시 기동하는 application-only rollback을 안전한
rollback으로 간주하지 않는다.

- `v1.0.0`은 pending Email 변경 schema와 lifecycle을 알지 못한다.
- `v1.1.0`에서 생성될 수 있는 `USERNAME_CHANGED`, `EMAIL_CHANGED`,
  `ACCOUNT_DELETED` Audit event 및 관련 상태를 구버전 코드가 동일하게 처리한다고
  보장할 수 없다.
- Email 변경·Hard Delete 뒤의 Session/OAuth/credential 폐기 의미를 구버전이
  재현하지 못하므로 schema가 additive라는 이유만으로 호환성을 가정하면 안 된다.
- 현재 사용자가 없더라도 운영 트래픽이 시작된 뒤에는 V7 data가 생길 수 있으므로
  현재의 빈 데이터 상태를 향후 rollback 근거로 사용하지 않는다.

따라서 DB 호환성이 입증되지 않은 장애에서는 먼저 외부 Caddy와 application writer를
정지하고 상태를 보존한다. 복구는 검증된 pre-v1.1.0 backup restore + `v1.0.0`
복귀 또는 V7을 이해하는 forward-fix image 중 승인된 방법을 사용한다. 실제 restore,
volume 변경 및 release 전환은 상태 보고와 별도 승인 후 수행한다. `down -v`는 rollback이
아니다.

## 4. Phase 9 E2E와 Production smoke의 차이

| 범위 | Phase 9 격리 E2E | v1.1.0 Production smoke |
|---|---|---|
| 환경 | `/opt/sso-lab-test`, 별도 DB/network/volume | `/opt/sso-lab`, 실제 Caddy/TLS/DB |
| Email | Test Mail Sink | 실제 Gmail 발송 미수행 |
| Turnstile | test flag/test double | Frontend/Backend 활성화와 Secret 경계만 확인, 실제 challenge 미수행 |
| TOTP clock | controllable test clock | 실제 clock, invalid-code 음성 경로만 확인 |
| 사용자 lifecycle | 테스트 사용자 생성, Profile/username/Email 변경/Hard Delete 수행 | 실제 사용자 생성·변경·삭제 미수행 |
| SSO/Logout | HR→Approval browser SSO와 중앙 Logout 전체 흐름 | exact redirect/PKCE, client 인증, endpoint 및 logout 경계의 비파괴 smoke |

격리 E2E는 기능 전체 흐름을 검증하고 종료 후 test resource를 제거했다. Production
smoke는 실제 운영 데이터 오염과 Gmail/Turnstile 호출을 피하면서 10개 health,
HTTPS/TLS, OIDC discovery/JWKS, client token 인증, authorization login 연결,
invalid bearer 거부, exact redirect/post-logout/back-channel 설정, 외부
`/internal/**` 차단, Profile lifecycle endpoint 보호를 확인했다.

## 5. Client token smoke 진단 기록

최초 smoke는 `client_secret_basic` credential을 단순히
`Base64(client_id + ":" + client_secret)`로 만들었다. RFC 6749 §2.3.1은
`client_id`와 `client_secret`을 각각
`application/x-www-form-urlencoded` 규칙으로 인코딩한 뒤 `:`로 연결하고 Base64
처리하도록 요구한다. Reserved character가 있는 Secret에서 이 단계가 빠져 HR/Admin이
401을 반환했고 Approval은 400을 반환했다.

진단 중 Caddy와 application writer를 정지하고 다음을 확인했다.

1. Production Secret 파일과 DB의 bcrypt client-secret hash가 세 client 모두 일치
2. Docker Secret source/target와 runtime readability 정상
3. RFC 6749 방식으로 수정한 Docker private-path 요청에서 HR/Approval/Admin 모두
   `400 invalid_grant`
4. 유효 authorization code를 사용하지 않아 access/refresh token 발급 없음
5. Secret 및 생성된 Basic credential을 출력하거나 로그에 기록하지 않음

따라서 401은 Production client credential 불일치가 아니라 최초 진단 요청의 인코딩
오류였다. 수정된 smoke가 성공한 뒤 9개 application을 먼저 healthy로 확인하고 마지막에
Caddy를 재기동했다.

## 6. 배포 후 데이터와 보안 상태

최종 검증 시 실제 사용자 데이터는 아직 없다.

- `auth.users`, 사용자 Role/Group, credential, OTP/TOTP/Recovery, OAuth
  authorization/consent, OIDC client session, pending Email 변경 및 Audit row 0
- 인증 사용자와 연결되지 않은 CSRF/authorization smoke용 임시 Spring Session만 존재하며
  사용자 session metadata는 0
- `.env`와 Production Secret checksum은 배포 전후 불변
- 10개 container 로그의 Secret/PII/token/OTP 원문 및 ERROR/FATAL/예상하지 못한
  Exception 탐지 0
- 기존 `v1.0.0` image와 위 V1~V6 backup 보존 확인

실제 사용자가 생긴 이후의 배포·rollback 판단은 이 문서의 빈 사용자 상태가 아니라
새로운 DB backup과 당시의 Flyway/data 상태를 기준으로 다시 수행한다.
