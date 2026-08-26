# Domain Change Guide

도메인 변경은 OIDC issuer와 모든 Registered Client allow-list를 함께 바꾸는
보안 변경이다. 일부 URL만 먼저 바꾸면 기존 session/token과 callback이 실패할
수 있으므로 maintenance window와 rollback 값을 준비한다.

## 변경 순서

1. 새 `AUTH_HOSTNAME`, `ADMIN_HOSTNAME`, `HR_HOSTNAME`, `APPROVAL_HOSTNAME`의
   소유권과 DNS 등록 상태를 확인한다.
2. 새 hostname이 VM을 가리키되 아직 기존 DNS를 제거하지 않는다.
3. 새 hostname에 대한 외부 80/443 도달성을 확인한다.
4. 운영 `.env`의 hostname 네 개와 `AUTH_PUBLIC_URL`, `ADMIN_PUBLIC_URL`,
   `HR_PUBLIC_URL`, `APPROVAL_PUBLIC_URL`을 함께 변경한다.
5. HR/Approval/Admin의 exact `redirect_uri`와 `post_logout_redirect_uri`를 새
   HTTPS URL로 함께 변경한다. wildcard는 사용하지 않는다.
6. Caddy hostname 값을 변경하고 Compose config를 검증한다.
7. Cookie domain을 별도로 설정하지 않은 현재 host-only cookie 정책이 각 새
   hostname에서 유지되는지 확인한다.
8. Same-origin reverse proxy 구조이므로 Frontend API URL/CORS source 변경이
   없는지 확인한다. 향후 absolute URL을 추가했다면 별도 점검한다.
9. Caddy를 기동해 새 hostname의 public CA 인증서 발급을 확인한다.
10. discovery `issuer`, `authorization_endpoint`, `jwks_uri`, token `iss`, 세
    callback, RP/global logout redirect를 smoke test한다.
11. HR 로그인 후 Approval SSO, Admin role 접근, back-channel/global logout을
    확인한다.
12. 충분한 전환 기간 후에만 기존 DNS를 제거한다.

## 주의사항

- issuer 변경 전 발급된 token은 새 issuer 검증과 호환되지 않으므로 활성
  session/token에 미치는 영향을 공지하고 필요하면 global logout한다.
- Caddy data volume을 삭제해 인증서 갱신 상태를 초기화하지 않는다.
- 현재 공인 IPv4를 Java, React, Compose, Caddy 또는 GitHub workflow에
  하드코딩하지 않는다.
- 무료 hostname 제공자가 동일 parent domain을 제공하지 않아도 네 개의 서로
  다른 exact hostname을 사용할 수 있다.

## Rollback

이전 hostname/URL/redirect 값이 담긴 `.env` 백업으로 Compose를 재기동하고,
이전 DNS가 유지되어 있는지 확인한다. 이전 issuer와 새 issuer 사이에서 발급된
token을 혼용하지 않는다. rollback 후 discovery, callback, SSO, logout 및 TLS를
다시 검증한다.
