# Architecture

## 서비스와 신뢰 경계

```mermaid
flowchart TB
  Browser -->|HTTPS| Caddy
  Caddy --> Web[React Web containers]
  Caddy --> Auth[auth-server]
  Caddy --> AdminBFF[admin-server]
  Caddy --> HrBFF[hr-server]
  Caddy --> ApprovalBFF[approval-server]
  AdminBFF -->|OIDC, back-channel, internal API| Auth
  HrBFF -->|OIDC, back-channel| Auth
  ApprovalBFF -->|OIDC, back-channel| Auth
  subgraph PostgreSQL[one PostgreSQL database]
    Identity[(auth schema / Identity)]
    HrSchema[(hr schema / future)]
    AdminSchema[(admin schema / future)]
    ApprovalSchema[(approval schema / future)]
  end
  Auth --> Identity
  HrBFF -. own domain persistence when introduced .-> HrSchema
  AdminBFF -. own domain persistence when introduced .-> AdminSchema
  ApprovalBFF -. own domain persistence when introduced .-> ApprovalSchema
```

`auth-server`는 User, Role, Group, credential, Spring Session, OAuth authorization와 Audit의 단일 owner입니다. 현재 물리 이름인 PostgreSQL `auth` schema가 Identity-owned schema이며, 문서의 논리적 `identity` 명칭도 같은 소유 경계를 뜻합니다. `admin-server`, `hr-server`, `approval-server`에는 현재 domain persistence가 없지만, 필요해지면 동일 PostgreSQL database 안의 `admin`, `hr`, `approval` 자체 schema와 전용 DB role을 사용할 수 있습니다. 이번 구조 정의로 아직 존재하지 않는 schema나 table을 미리 만들지는 않습니다.

자체 persistence 기술(JPA/JDBC/PostgreSQL/Flyway)은 금지 대상이 아닙니다. 금지 대상은 auth-server 구현 package/module 직접 의존, Auth 전용 datasource 설정 재사용, 다른 서비스 소유 schema/table 직접 접근입니다. Admin의 Identity 작업도 `Admin Web → Admin Server → Auth Internal API → Identity Service/Repository` 경계를 지킵니다. Caddy는 `/internal/**`를 외부에서 거부합니다.

## OIDC와 BFF

세 client는 confidential client로 Authorization Code + PKCE S256를 사용합니다. Redirect URI와 post-logout redirect URI는 exact allow-list입니다. Auth는 ID/Access/Refresh Token을 발급하고 BFF는 token을 서버 측 Session에만 저장합니다. Browser에는 HttpOnly/Secure/SameSite cookie만 전달되며 `localStorage`, `sessionStorage`, IndexedDB에 token을 저장하지 않습니다.

BFF를 선택한 이유는 client secret과 refresh token을 브라우저에서 분리하고 CSRF/cookie/session 정책을 서버에서 통제하기 위해서입니다. 대가로 BFF Session 저장소, back-channel logout, CSRF token endpoint와 서비스별 운영이 필요합니다.

## SSO와 identity claim

Auth Passwordless Session을 Spring Authorization Server 인증으로 연결합니다. `sub`는 불변 user UUID이고 `preferred_username`, masked/unmasked email scope, role, group claim은 발급 시점의 Identity를 사용합니다. username/email 변경 뒤 기존 JWT를 수정하지 않으며 새 OIDC 발급부터 새 claim을 반영합니다. Email 변경은 현재 Auth Session만 유지하고 다른 Auth/BFF Session과 모든 Refresh Token을 폐기합니다.

## Logout

- RP-Initiated Logout: client가 Auth end-session endpoint로 이동하고 exact post-logout URI만 허용합니다.
- Back-Channel Logout: Auth가 서명된 logout token을 각 BFF 내부 endpoint로 보내 해당 `sid` Session을 제거합니다.
- Global Logout: Auth Session, OAuth authorization/token과 모든 BFF Session을 함께 폐기합니다.

## 서비스 분리 결정

User Service나 OTP Service를 별도 배포 단위로 쪼개지 않았습니다. 현재 규모에서는 하나의 Identity owner 안의 모듈 경계가 transaction, 암호화, Audit, 삭제 무결성을 더 단순하고 안전하게 유지합니다. Admin Server는 별도 공격 표면과 ADMIN authorization/BFF 책임 때문에 분리하며 현재 별도 persistence는 없지만, 미래의 Admin domain data는 `admin` schema로만 소유할 수 있습니다.

React는 네 사용자 경험을 독립적으로 보여주고 SPA 상태/UI 테스트가 쉽다는 장점이 있습니다. 반면 네 bundle과 공통 UI 중복, BFF/CSRF 연동 비용이 있습니다. 인증 정책은 React가 아니라 Backend가 최종 판단합니다.

## 데이터와 인프라

현재는 PostgreSQL 하나의 `auth` schema만 사용하고 직접 접근자는 Auth뿐입니다. Flyway V1-V8이 이 schema를 소유하며 Hibernate는 `validate`만 사용합니다. 향후 domain persistence가 필요하면 database를 서비스마다 분리하는 대신 같은 PostgreSQL database에 `hr`, `admin`, `approval` schema를 필요 시점에 추가하고, 각 서비스의 독립된 migration과 전용 DB role로 소유합니다.

Schema ownership의 최종 강제 수단은 정규식이 아니라 PostgreSQL privilege입니다. 목표 권한은 Auth DB role은 `auth`(Identity)만, HR role은 `hr`만, Admin role은 `admin`만, Approval role은 `approval`만 접근할 수 있게 하고 다른 schema의 `USAGE` 및 table 권한을 부여하지 않는 것입니다. 실제 role/schema/migration 추가는 해당 domain persistence 도입 작업에서 별도 검토하며 이번 변경에는 포함하지 않습니다.

`db-network`와 `internal-network`는 Docker internal network이고 Host에는 Caddy 80/443만 publish합니다.

Redis는 v1에서 사용하지 않습니다. 단일 Auth 인스턴스 전제에서 Spring Session JDBC와 DB/in-memory rate limit을 사용하며, 다중 인스턴스·분산 제한은 Future Work입니다. AWS SSM/KMS와 RDS도 같은 코드/이미지를 유지하는 향후 인프라 확장입니다.

## 서비스 데이터와 transaction

서비스는 다른 서비스의 schema/table을 직접 조회하거나 수정하지 않고 소유 서비스의 API 또는 명시적인 integration contract를 사용합니다. 같은 PostgreSQL database를 사용하더라도 서로 다른 Java process의 작업은 하나의 Spring `@Transactional` 경계로 묶이지 않습니다.

서비스 간 원자성이나 최종 일관성이 필요하면 요구사항과 실패 모델에 따라 Saga, Transactional Outbox, idempotent retry 및 compensation을 검토합니다. 단일 transaction의 편의를 위해 다른 서비스 schema를 직접 수정하는 구조는 사용하지 않습니다.
