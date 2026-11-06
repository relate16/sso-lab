# Quiet Winter Gallery architecture

## 경계와 데이터 흐름

Gallery는 하나의 `gallery-web`과 `gallery-server`로 구성된다. 공개 Home, Works, About,
Scene Viewer와 문의 작성은 로그인 없이 사용할 수 있지만, `/studio/**`와 Studio API는 기존
Auth의 Gallery 전용 confidential OIDC client를 통해 로그인한 `ROLE_ADMIN`만 사용할 수
있다. Gallery는 Auth의 schema나 repository를 직접 읽지 않고 검증된 OIDC claim만 사용한다.

```mermaid
flowchart LR
  Browser -->|HTTPS| Caddy
  Caddy --> GalleryWeb[gallery-web]
  Caddy --> GalleryServer[gallery-server]
  GalleryWeb -->|same-origin API + CSRF| GalleryServer
  GalleryServer -->|Authorization Code + PKCE| Auth[auth-server]
  Auth -. signed back-channel logout .-> GalleryServer
  GalleryServer --> GallerySchema[(gallery schema)]
  GalleryServer --> ImageVolume[(private original / public web / thumbnail)]
```

## Routes

Public routes:

- `/` — 최근 공개 작품 최대 4점의 Home 전시장
- `/works` — 검색, 필터, 정렬, 페이지 탐색
- `/about` — 전시 소개
- `/artworks/{publicId}?scene=1~3` — URL 기반 Scene Viewer

Studio routes:

- `/studio/artworks` — 작품 목록과 drag-and-drop 순서 변경
- `/studio/artworks/new` — 작품 생성
- `/studio/artworks/{id}` — 편집, 공개, 이미지와 대표 이미지 관리
- `/studio/inquiries` — 문의 목록과 상태 관리

## API

- `GET /api/v1/gallery/home`
- `GET /api/v1/gallery/artworks`
- `GET /api/v1/gallery/artworks/{publicId}`
- `POST /api/v1/gallery/inquiries`
- `GET|POST /api/v1/gallery/studio/artworks`
- `GET|PATCH|DELETE /api/v1/gallery/studio/artworks/{id}`
- `PATCH /api/v1/gallery/studio/artworks/order`
- `POST /api/v1/gallery/studio/artworks/{id}/images`
- `PATCH /api/v1/gallery/studio/artworks/{id}/images/primary`
- `DELETE /api/v1/gallery/studio/artworks/{id}/images/{imageId}`
- `GET|PATCH /api/v1/gallery/studio/inquiries[/{id}]`
- `GET /api/v1/gallery/studio/analytics` — 조회수·순 방문자 집계
- `GET /api/v1/gallery/studio/analytics/views?page=0&size=20` — 관리자 전용 최근 조회 기록

공개 목록과 상세는 `published=true`만 조회한다. Studio는 내부 UUID를 사용하고 공개 route는
별도의 public UUID를 사용한다.

## Scene Viewer

Scene은 작품에 고정되지 않는다. URL의 `scene`이 source of truth이며 작품을 선택할 때마다
`1 → 2 → 3 → 1`로 이동한다. 같은 작품을 다시 선택해도 다음 Scene으로 이동하고 browser
history에 새 URL을 남긴다. Scene 정의는 `sceneDefinitions.ts`에 분리되어 있어 새 공간을
추가할 때 component의 조건문을 늘리지 않는다.

- Scene 1: 정면 벽, 대칭 조명과 중앙 작품
- Scene 2: 두 벽이 만나는 코너와 비대칭 원근
- Scene 3: 긴 측면 벽과 복도형 소실점

모두 semantic HTML/CSS 2D geometry로 구현하며 Three.js, WebGL, 자유 카메라는 사용하지
않는다. 작품의 실제 가로·세로 cm를 상대 크기에 반영하고 네 가지 frame type을 별도로
표현한다. 긴 설명은 desktop drawer, mobile bottom sheet로 제공한다.

## 이미지 저장

DB는 한 작품에 여러 `ArtworkImage`를 저장하고 대표 이미지를 한 장 선택한다. 원본은 공개
controller로 노출하지 않는다. 서버가 업로드를 decode한 뒤 web/thumbnail JPEG로 다시
인코딩하고 UUID storage key 아래에 저장한다. 공개 Home과 목록은 thumbnail, Scene은 web
variant를 사용한다.

업로드는 허용 MIME, 실제 decode 가능 여부, byte/pixel 한도와 경로 정규화를 검증한다.
SVG는 허용하지 않는다. Production named volume은 one-shot init container가 non-root
Gallery runtime UID/GID에 맞춰 준비하며, DB와 image volume은 함께 백업해야 한다.

## 보안과 개인정보

조회가 인정된 이벤트의 원본 IP는 `gallery.artwork_view_events.visitor_ip` (`INET`, nullable)에
저장한다. 별도의 만료·자동 삭제 정책은 없으며 기존 이벤트의 IP는 소급 수집하지 않는다.
IP가 없거나 유효한 주소가 아니면 `NULL`로 저장하고 조회 집계는 계속한다. IP는 중복 제거나
순 방문자 식별에 사용하지 않는다. 관리자·봇 제외 및 쿠키 기반 30분 중복 제거는 그대로다.

방문자 IP는 `HttpServletRequest.getRemoteAddr()`를 사용한다. 운영 환경에서는 기존
`SERVER_FORWARD_HEADERS_STRATEGY=native`와 Caddy만 신뢰하는 `TRUSTED_PROXY_CIDR` 설정을
유지해야 한다. Caddy는 외부에서 들어온 위조 전달 헤더를 신뢰하지 않고 `X-Forwarded-For`를
구성하며, 애플리케이션에서는 이 헤더를 직접 파싱하지 않는다. 직접 로컬 실행에서는 접속한
상대방 주소(예: `127.0.0.1`)가 기록된다.

Studio 최근 조회 목록은 한국 시간·작품명·조회 화면·IP를 최신순으로 보여주며 IP가 `NULL`이면
‘미수집’으로 표시한다. 기본 20건, 최대 100건의 페이지 조회이며 동일 시각에는 이벤트 UUID로
순서를 고정한다. 조회 시각·UUID 인덱스로 최신 목록을 조회한다. 공개 API에는 IP를 노출하지 않는다.

통계 조회와 집계는 `ArtworkAnalyticsQueryRepository`의 QueryDSL 구현에서 수행한다.
`ArtworkAnalyticsService`는 한국 시간 기준의 날짜 범위를 계산하고 조회 결과를 조합한다.
조회 이벤트는 `@Immutable`인 `ArtworkViewEventEntity`로 읽기 매핑하며, `InetAddress`를 통해
PostgreSQL `INET`을 읽는다. IPv6 응답은 Java의 확장된 주소 문자열로 표현한다. 이벤트 저장과
30분 중복 판정은 `ArtworkViewService`의 기존 원자적 JDBC SQL을 유지한다.

- Studio authorization은 UI가 아니라 Spring Security의 서버 측 `ROLE_ADMIN`으로 강제한다.
- Cookie 기반 API는 기존 CSRF 정책을 유지하고 공개 문의 POST도 CSRF token을 요구한다.
- 작품명과 설명은 raw HTML로 렌더링하지 않고 React text rendering을 사용한다.
- 문의의 이름, email, phone, message는 application log에 기록하지 않는다.
- 문의 UI는 개인정보 처리 동의를 명시적으로 요구하되 법적 준수 완료를 주장하지 않는다.
- Gallery OIDC client secret은 별도 Docker Secret이며 repository나 frontend bundle에 넣지 않는다.

