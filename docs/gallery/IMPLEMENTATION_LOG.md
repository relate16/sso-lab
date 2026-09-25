# Quiet Winter Gallery implementation log

This log records the staged implementation required by the Gallery specification.

## Phase 1 — Foundation and backend skeleton

- Added the `gallery-server` Spring Boot module and `gallery-web` React/Vite package.
- Added Gallery-owned Flyway schema objects for artworks, multiple artwork images, and inquiries.
- Separated internal UUIDs from public artwork UUIDs and established sale/frame/status enums.
- Added loopback development ports, Vite proxy configuration, Compose services, persistent storage,
  and the Caddy virtual host without weakening the existing `/internal/**` boundary.
- Kept Studio APIs fail-closed until OIDC and server-side `ROLE_ADMIN` checks arrive in Phase 2.

Validation:

- `:backend:gallery-server:test` — passed
- `:backend:gallery-server:bootJar` — passed
- Gallery frontend test, TypeScript lint, and Vite build — passed
- `git diff --check` — passed
- Docker Compose runtime validation deferred to Phase 5 because Docker CLI is unavailable in the
  current Windows execution environment; CI retains the authoritative Compose build.

Remaining after Phase 1: OIDC Studio security and management behavior, complete public experience,
Scene Viewer, security polish, full CI/CD and documentation validation.

## Phase 2 — Studio and OIDC authentication

- Registered a fourth confidential `gallery-client` in Auth with Authorization Code + PKCE,
  exact redirects, independent secret material, and back-channel logout.
- Added Gallery BFF sessions, OIDC Claim-to-authority mapping, and server-side `ROLE_ADMIN`
  enforcement for every Studio API.
- Implemented artwork CRUD, publish validation, bulk reorder with optimistic UI rollback, image
  upload/delete/primary selection, and inquiry list/status management.
- Added local persistent storage behind an interface: originals remain private while re-encoded web
  and thumbnail JPEG variants are served through authorization-aware controllers.
- Uploads enforce declared type, decode validity, byte/pixel limits, UUID keys and traversal-safe
  paths; SVG and undecodable payloads are rejected.

Validation:

- Gallery backend compile and tests — passed
- Auth backend compile and tests — passed (database-dependent tests skipped without Docker)
- Gallery frontend TypeScript lint and production build — passed

Remaining after Phase 2: complete public Home/Works/About experience, exhibition Scenes and public
inquiry UI, then accessibility/security/CI documentation polish.
