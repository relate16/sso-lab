# SSO Lab

Spring Boot 기반 OIDC SSO 및 Passwordless Multi-Method Authentication 플랫폼입니다.

현재 구현 범위는 **Phase 1 — Project Foundation**입니다. 인증/Identity/OIDC 기능은
아직 구현되지 않았으며, 후속 Phase에서 Master Specification 순서대로 추가합니다.

## Phase 1 구조

- Backend: Java 21, Spring Boot 4.1.0, Gradle Kotlin DSL
- Frontend: React, TypeScript, Vite, npm
- Database: PostgreSQL 17, auth-server 전용 직접 접근
- Migration: auth schema 및 Spring Session JDBC 테이블을 관리하는 Flyway
- Packaging: Backend/Frontend 서비스별 독립 Docker image
- Health: 각 Backend의 `/actuator/health`

서비스 경계:

```text
admin-web -> admin-server --internal API (Phase 5)--> auth-server -> PostgreSQL
hr-web    -> hr-server    --OIDC (Phase 4)----------> auth-server
approval-web -> approval-server --OIDC (Phase 4)---> auth-server
```

`admin-server`, `hr-server`, `approval-server`에는 PostgreSQL/JPA/Flyway 의존성을
두지 않습니다. Browser storage에 OAuth/OIDC Token을 저장하는 코드도 두지 않습니다.

## Build

필수 도구는 Java 21과 Node.js 24 LTS입니다. Gradle은 Wrapper를 사용합니다.

```bash
./gradlew clean test build

cd frontend/auth-web && npm ci && npm run build
cd ../admin-web && npm ci && npm run build
cd ../hr-web && npm ci && npm run build
cd ../approval-web && npm ci && npm run build
```

Docker 환경에서는 실제 `.env`를 Git에 추가하지 말고 `.env.example`을 복사한 뒤
placeholder를 교체합니다. PostgreSQL과 Backend 포트는 Host에 publish하지 않습니다.

```bash
docker compose --env-file .env config
docker compose --env-file .env up -d --build
```

Production Caddy/TLS 및 공개 URL routing은 후속 Infrastructure Phase에서 구성합니다.
