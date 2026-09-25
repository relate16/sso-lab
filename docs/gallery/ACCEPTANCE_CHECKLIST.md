# Quiet Winter Gallery acceptance checklist

Checked against `quiet-winter-gallery-ai-agent-development-spec.md` after Phase 5.

## Public

- [x] Quiet Winter Gallery branding and anonymous Home
- [x] Latest published Home exhibition, maximum four works, opening in Scene 1
- [x] Three spatially distinct CSS 2D scenes: front wall, corner and long corridor
- [x] One protagonist artwork, physical-size scaling, four frame types and dark wood treatment
- [x] Wall caption, desktop story drawer and mobile bottom sheet
- [x] URL-backed `1 → 2 → 3 → 1` cycling, including same-artwork reselection
- [x] Refresh, deep link, browser back/forward and invalid-scene fallback
- [x] Bottom artwork navigation with search and Works escape route
- [x] Works search, price/size/sale filters, recent/price sort and responsive grid/list discovery
- [x] Unpublished artwork exclusion and all four sale states
- [x] Inquiry drawer and About page

## Studio

- [x] Existing Auth OIDC, Gallery-specific client and server-side `ROLE_ADMIN`
- [x] Artwork create/read/update/delete, publish, featured and display order
- [x] Drag-and-drop reorder with optimistic rollback
- [x] Multiple-image data model, upload, web/thumbnail resize and primary image
- [x] Frame, sale state and price management
- [x] Inquiry list and status management

## Security

- [x] No raw user HTML; React text rendering only
- [x] Upload MIME/decode/byte/pixel/path validation; SVG rejected
- [x] Existing cookie/CSRF policy retained, including anonymous inquiry POST
- [x] No committed production Secret
- [x] Public/Studio endpoints and public/private media separated
- [x] Inquiry personal data excluded from application logs
- [x] No Auth database or implementation coupling

## Quality and delivery

- [x] Responsive and mobile-specific scene/drawer variants
- [x] Keyboard focus, dialog semantics, focus trap and Escape close
- [x] `prefers-reduced-motion` for transitions and programmatic rail scrolling
- [x] Automated scene cycle, unpublished visibility and admin authorization coverage
- [x] README and architecture/run/security/deployment/test documents updated
- [x] Gallery added to CI, Release, Deploy, Compose, Caddy and repository audit contracts
- [ ] Remote GitHub Actions run — requires pushing the Phase 5 commit
- [ ] Docker Compose/Caddy runtime verification — Docker CLI unavailable in the current environment;
      configured Linux CI scripts remain the authoritative check

