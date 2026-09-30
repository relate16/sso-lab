import assert from 'node:assert/strict'
import { access, readFile } from 'node:fs/promises'
import test from 'node:test'

const pages = await readFile(new URL('../src/gallery/PublicPages.tsx', import.meta.url), 'utf8')
const inquiry = await readFile(new URL('../src/gallery/InquiryDrawer.tsx', import.meta.url), 'utf8')
const api = await readFile(new URL('../src/api.ts', import.meta.url), 'utf8')
const frame = await readFile(new URL('../src/gallery/ArtworkFrame.tsx', import.meta.url), 'utf8')
const frameRenderer = await readFile(new URL('../src/gallery/FrameRenderer.tsx', import.meta.url), 'utf8')
const scene = await readFile(new URL('../src/gallery/GalleryScene.tsx', import.meta.url), 'utf8')
const homeScene = await readFile(new URL('../src/gallery/HomeExhibition.tsx', import.meta.url), 'utf8')
const homeRail = await readFile(new URL('../src/gallery/HomeArtworkRail.tsx', import.meta.url), 'utf8')
const carouselCrop = await readFile(new URL('../src/gallery/carouselCrop.ts', import.meta.url), 'utf8')
const definitions = await readFile(new URL('../src/gallery/sceneDefinitions.ts', import.meta.url), 'utf8')
const story = await readFile(new URL('../src/gallery/ArtworkStoryDrawer.tsx', import.meta.url), 'utf8')
const styles = await readFile(new URL('../src/styles.css', import.meta.url), 'utf8')
const lightbox = await readFile(new URL('../src/gallery/ArtworkLightbox.tsx', import.meta.url), 'utf8')

test('public gallery exposes URL-backed discovery controls and the four-work exhibition', () => {
  assert.match(pages, /useSearchParams/)
  assert.match(pages, /최근 공개 작품/)
  for (const filter of ['saleStatus', 'size', 'minPrice', 'maxPrice', 'sort']) assert.match(pages, new RegExp(`name="${filter}"`))
})

test('artwork inquiry remains accessible and requires privacy consent', () => {
  assert.match(inquiry, /role="dialog"/)
  assert.match(inquiry, /aria-modal="true"/)
  assert.match(inquiry, /privacyAgreed/)
  assert.match(inquiry, /event\.key === 'Escape'/)
})

test('Works opens a URL-backed, accessible full-screen artwork lightbox', () => {
  assert.match(pages, /params\.get\('artwork'\)/)
  assert.match(pages, /<ArtworkLightbox/)
  assert.match(lightbox, /role="dialog"/)
  assert.match(lightbox, /aria-modal="true"/)
  assert.match(lightbox, /event\.key === 'Escape'/)
  assert.match(lightbox, /event\.key === 'ArrowLeft'/)
  assert.match(lightbox, /event\.key === 'ArrowRight'/)
  assert.match(lightbox, /드래그해 이동/)
  assert.match(lightbox, /전시장에서 보기/)
  assert.match(lightbox, /className="artwork-lightbox-image-fit"/)
  assert.match(styles, /\.artwork-lightbox \{[\s\S]*position: fixed;[\s\S]*inset: 0;/)
  assert.match(styles, /\.artwork-lightbox-image-fit[\s\S]*position: absolute;[\s\S]*inset: clamp\([\s\S]*\.artwork-lightbox-image-fit img[\s\S]*width: 100%;[\s\S]*height: 100%;[\s\S]*object-fit: contain;/)
})

test('public frontend uses the backend Gallery API contract without a synthetic public segment', () => {
  assert.match(api, /\/api\/v1\/gallery\/home/)
  assert.match(api, /\/api\/v1\/gallery\/artworks/)
  assert.match(api, /\/api\/v1\/gallery\/inquiries/)
  assert.doesNotMatch(api, /\/api\/v1\/gallery\/public\//)
})

test('scene polish covers every frame type and reduced-motion users', () => {
  for (const frameType of ['none', 'mat_board', 'acrylic_box', 'floating_frame']) {
    assert.match(styles, new RegExp(`\\.frame-${frameType}`))
  }
  assert.match(frameRenderer, /fetchPriority=\{priority \? 'high' : 'auto'\}/)
  assert.match(frame, /zoomLevel < 4/)
  assert.match(frame, /preload\.src = image\.originalUrl/)
  assert.match(frame, /highResolutionImageId === image\.id \? image\.originalUrl : image\.webUrl/)
  assert.match(scene, /zoomLevel=\{zoomLevel\}/)
  assert.match(scene, /prefers-reduced-motion: reduce/)
  assert.match(styles, /@media \(prefers-reduced-motion: reduce\)/)
})

test('shared frame renderer composes orientation-specific PNG layers in the required order', async () => {
  assert.match(frame, /<FrameRenderer/)
  assert.match(frameRenderer, /width >= height \? 'landscape' : 'portrait'/)
  assert.ok(frameRenderer.indexOf('role="shadow"') < frameRenderer.indexOf('role="back-panel"'))
  assert.ok(frameRenderer.indexOf('role="back-panel"') < frameRenderer.indexOf('{artworkLayer}'))
  assert.ok(frameRenderer.indexOf('{artworkLayer}') < frameRenderer.indexOf('role="front-case"'))
  for (const asset of [
    'acrylic-box/acrylic-back-panel-landscape.png',
    'acrylic-box/acrylic-back-panel-portrait.png',
    'acrylic-box/acrylic-front-case-landscape.png',
    'acrylic-box/acrylic-front-case-portrait.png',
    'acrylic-box/acrylic-shadow-landscape.png',
    'acrylic-box/acrylic-shadow-portrait.png',
    'floating-frame/floating-frame-inner-depth-landscape.png',
    'floating-frame/floating-frame-inner-depth-portrait.png',
    'floating-frame/floating-frame-outer-landscape.png',
    'floating-frame/floating-frame-outer-portrait.png',
    'floating-frame/floating-frame-shadow-landscape.png',
    'floating-frame/floating-frame-shadow-portrait.png',
    'mat-board/mat-board-landscape.png',
    'mat-board/mat-board-portrait.png',
    'mat-board/mat-outer-frame-landscape.png',
    'mat-board/mat-outer-frame-portrait.png',
  ]) await access(new URL(`../src/assets/frames/${asset}`, import.meta.url))
  assert.match(styles, /\.frame-composite__layer[\s\S]*object-fit: fill/)
  assert.match(styles, /\.frame-composite__artwork-image[\s\S]*object-fit: fill/)
  assert.match(styles, /\.frame-composite--acrylic-box \.frame-composite__front-case[\s\S]*transform: none/)
  assert.match(styles, /\.frame-composite--acrylic-box\.frame-composite--landscape \.frame-composite__artwork[\s\S]*inset: 18\.5% 11\.5%/)
  assert.match(styles, /\.frame-composite--acrylic-box\.frame-composite--portrait \.frame-composite__artwork[\s\S]*inset: 11%/)
  assert.match(styles, /\.frame-composite--acrylic-box\.frame-composite--landscape \.frame-composite__artwork[\s\S]*background: transparent/)
  assert.match(styles, /\.frame-composite--acrylic-box\.frame-composite--landscape \.frame-composite__back-panel[\s\S]*left: 1\.69118%[\s\S]*top: -1\.48882%[\s\S]*width: 96\.4801%[\s\S]*height: 103\.53509%/)
  assert.match(styles, /\.frame-composite--acrylic-box\.frame-composite--portrait \.frame-composite__back-panel[\s\S]*left: -1\.02273%[\s\S]*top: -2\.31988%[\s\S]*width: 102\.04545%[\s\S]*height: 104\.84694%/)
  assert.doesNotMatch(frameRenderer, /backing-surface/)
  assert.match(frame, /framedMountDimensions/)
  assert.match(frame, /MAT_BOARD: \{ width: 1052 \/ 1448, height: 529 \/ 1086 \}/)
  assert.match(frame, /MAT_BOARD: \{ width: 684 \/ 1086, height: 964 \/ 1448 \}/)
  assert.match(frame, /FLOATING_FRAME: \{ width: 1218 \/ 1402, height: 843 \/ 1122 \}/)
  assert.match(frame, /FLOATING_FRAME: \{ width: 924 \/ 1122, height: 1204 \/ 1402 \}/)
  assert.match(styles, /\.frame-composite--floating-frame\.frame-composite--landscape \.frame-composite__artwork[\s\S]*inset: 12\.3886% 6\.5621% 12\.4777%/)
  assert.match(styles, /\.frame-composite--floating-frame \.frame-composite__artwork[\s\S]*z-index: 4/)
  assert.doesNotMatch(frameRenderer, /role="inner-depth"/)
  assert.match(styles, /\.frame-composite--mat-board\.frame-composite--portrait \.frame-composite__artwork[\s\S]*inset: 16\.7127% 18\.5083%/)
  assert.match(styles, /\.frame-composite--mat-board\.frame-composite--portrait \.frame-composite__mat-board[\s\S]*clip-path: inset\(5\.8011% 4\.8803%\)/)
  assert.doesNotMatch(styles, /\.mounted-artwork-detail::after/)
})

test('Home maps only the latest four artworks into configured image slots', () => {
  assert.match(homeScene, /artworks\.slice\(0, 4\)/)
  assert.match(homeScene, /HOME_SLOTS\[index\]/)
  assert.match(definitions, /gallery-home-scene\.webp/)
  assert.match(homeScene, /offsetWidth/)
  assert.match(homeScene, /fitPhysicalArtworkInWallRegion/)
  assert.match(homeScene, /slot\.wallRegionCm/)
  assert.match(homeScene, /framedMountDimensions/)
  assert.match(homeScene, /data-projection-corner/)
  assert.match(homeScene, /cropOffsetX/)
  assert.doesNotMatch(styles, /slot-responsive-scale/)
  assert.doesNotMatch(styles, /mounted-artwork-home[^}]*transform:\s*scale/)
})

test('Home exposes a width-matched horizontal artwork rail that opens the selected work in Works', () => {
  assert.match(pages, /<HomeArtworkRail artworks=\{collection\}/)
  assert.match(pages, /pageSize: '60'/)
  assert.match(homeRail, /className="home-artwork-rail-viewport"/)
  assert.match(homeRail, /const WORKS_PAGE_SIZE = 24/)
  assert.match(homeRail, /Math\.floor\(index \/ WORKS_PAGE_SIZE\)/)
  assert.match(homeRail, /to=\{`\/works\?\$\{targetParams\}`\}/)
  assert.match(homeRail, /scrollBy/)
  assert.match(homeRail, /disabled=\{!bounds\.canScrollLeft\}/)
  assert.match(homeRail, /disabled=\{!bounds\.canScrollRight\}/)
  assert.match(styles, /\.home-artwork-rail \{[\s\S]*max-width: 1500px;/)
  assert.match(styles, /\.home-artwork-rail-viewport \{[\s\S]*overflow-x: auto;[\s\S]*scroll-snap-type: x mandatory;/)
  assert.match(styles, /\.home-artwork-tile \{[\s\S]*aspect-ratio: 16 \/ 9;/)
  assert.match(homeRail, /carouselCropStyle\(createCarouselCropLayout\(/)
  assert.match(carouselCrop, /const coverScale = Math\.max\(FRAME_WIDTH \/ width, FRAME_HEIGHT \/ height\)/)
  assert.match(carouselCrop, /left: \(FRAME_WIDTH \/ 2\) - \(safeFocalX \* renderedWidth\)/)
  assert.match(carouselCrop, /top: \(FRAME_HEIGHT \/ 2\) - \(safeFocalY \* renderedHeight\)/)
  assert.match(styles, /\.home-artwork-tile img \{[\s\S]*left: var\(--carousel-image-left, 0\);[\s\S]*width: var\(--carousel-image-width, 100%\);/)
  assert.match(styles, /\.home-artwork-rail-button \{[\s\S]*opacity: 0;[\s\S]*pointer-events: none;/)
  assert.match(styles, /\.home-artwork-rail:hover \.home-artwork-rail-button:not\(:disabled\),[\s\S]*\.home-artwork-rail:focus-within[\s\S]*opacity: 1;/)
})

test('detail scenes use approved backgrounds and keep artwork front-facing', () => {
  for (const sceneNumber of [1, 2, 3]) assert.match(definitions, new RegExp(`gallery-detail-scene-${sceneNumber}\\.webp`))
  assert.doesNotMatch(frame, /rotateY|skew|perspective/)
  assert.match(styles, /mounted-artwork-detail[\s\S]*transform: none/)
})

test('scene zoom supports ten levels, reset, Escape and reduced-motion handling', () => {
  assert.match(scene, /setZoomLevel/)
  assert.match(scene, /event\.key === 'Escape'/)
  assert.match(scene, /className="scene-zoom-control"/)
  assert.match(scene, /const MAX_ZOOM_LEVEL = 10/)
  assert.match(scene, /aria-label="확대" disabled=\{zoomLevel >= MAX_ZOOM_LEVEL\}/)
  assert.match(scene, /Math\.min\(MAX_ZOOM_LEVEL, level \+ 1\)/)
  assert.match(scene, /aria-label="축소" disabled=\{zoomLevel <= 1\}/)
  assert.match(scene, /Math\.max\(1, level - 1\)/)
  assert.match(scene, /className="scene-zoom-slider"[\s\S]*type="range"[\s\S]*min="1"[\s\S]*max=\{MAX_ZOOM_LEVEL\}/)
  assert.match(scene, /onChange=\{\(event\) => setZoomLevel\(Number\(event\.target\.value\)\)\}/)
  assert.match(scene, /const zoomWithWheel = \(event: WheelEvent\)/)
  assert.match(scene, /event\.deltaY < 0 \? 1 : -1/)
  assert.match(scene, /const WHEEL_ZOOM_THRESHOLD = 90/)
  assert.match(scene, /gesture\.streak = Math\.min\(6, gesture\.streak \+ 1\)/)
  assert.match(scene, /const acceleration = 1 \+ Math\.max\(0, gesture\.streak - 1\) \* \.18/)
  assert.match(scene, /const steps = Math\.min\(3, Math\.floor\(gesture\.accumulatedDelta \/ WHEEL_ZOOM_THRESHOLD\)\)/)
  assert.match(scene, /nextZoom === currentZoom/)
  assert.ok(scene.indexOf('event.preventDefault()') < scene.indexOf('nextZoom === currentZoom'))
  assert.match(scene, /addEventListener\('wheel', zoomWithWheel, \{ passive: false \}\)/)
  assert.doesNotMatch(scene, /scene-zoom-toggle/)
  assert.match(scene, /useEffect\(\(\) => \{ setZoomLevel\(1\); setPan\(\{ x: 0, y: 0 \}\)/)
  assert.match(scene, /if \(zoomLevel === 1\) setPan\(\{ x: 0, y: 0 \}\)/)
  assert.match(scene, /aria-controls="gallery-scene-space"/)
  assert.match(styles, /scale\(var\(--scene-zoom-scale, 1\)\)/)
  assert.match(styles, /scene-zoom-control[\s\S]*flex-direction: column/)
  assert.match(styles, /scene-zoom-control button[\s\S]*min-width: 29px;[\s\S]*min-height: 36px/)
  assert.match(styles, /transition: transform 190ms cubic-bezier\(\.2, \.72, \.25, 1\)/)
  assert.doesNotMatch(styles, /\.scene-camera[\s\S]*will-change: transform/)
  assert.match(styles, /\.scene-zoom-control \{[\s\S]*right: clamp\([\s\S]*bottom: clamp\([\s\S]*width: 35px;[\s\S]*border-radius: 14px;[\s\S]*background: #f4f1ea;[\s\S]*transform: none/)
  assert.match(styles, /\.scene-zoom-scale[\s\S]*height: 112px;[\s\S]*repeating-linear-gradient/)
  assert.match(styles, /\.scene-zoom-slider[\s\S]*writing-mode: vertical-lr;[\s\S]*touch-action: none/)
})

test('detail scenes size artwork against a physical wall width', () => {
  assert.match(definitions, /EXHIBITION_WALL_SIZE_CM = \{ width: 450, height: 300 \}/)
  assert.match(scene, /const physicalPlaneCm = EXHIBITION_WALL_SIZE_CM/)
  assert.match(scene, /physicalPlaneCm=\{physicalPlaneCm\}/)
  assert.match(scene, /const mountWidthPx = SCENE_REFERENCE_SIZE\.width/)
  assert.match(frame, /artworkPhysicalMountPercent/)
  assert.doesNotMatch(frame, /minimumFrameEdgePx|DETAIL_MINIMUM_FRAME_EDGE_PX/)
  assert.match(frame, /physicalPlaneCm\.height, \.9, mountWidthPx, mountHeightPx\)/)
})

test('cropped and zoomed detail scenes support drag panning with centered cropping and frame-anchored captions', () => {
  assert.match(scene, /onPointerDown=\{startPan\}/)
  assert.match(scene, /setPointerCapture/)
  assert.match(scene, /camera\.offsetHeight \* scale - space\.clientHeight/)
  assert.match(scene, /sceneCropped \? '화면 비율에 따라 전시 장면 일부가 잘려 있습니다/)
  assert.match(scene, /--scene-pan-x/)
  assert.match(scene, /화면을 드래그해 이동/)
  assert.match(styles, /\.scene-camera[\s\S]*top: 50%;[\s\S]*--scene-pan-x/)
  assert.match(styles, /\.home-scene-canvas \.scene-background img,[\s\S]*object-position: center center/)
  assert.match(styles, /\.scene-space\.is-pannable[\s\S]*touch-action: none/)
  assert.match(styles, /\.scene-space \{[\s\S]*overscroll-behavior: contain;[\s\S]*touch-action: none;/)
  assert.match(styles, /\.scene-caption[\s\S]*left: calc\(100% \+[\s\S]*bottom: 0/)
  assert.match(frame, /\{children\}/)
})

test('Works artwork cards contain every image inside the neutral stage and the Scene rail scrolls horizontally only', () => {
  assert.match(styles, /\.artwork-card-image-stage[\s\S]*inset: 5px;[\s\S]*place-items: center[\s\S]*overflow: hidden/)
  assert.match(styles, /\.artwork-card-image-stage img[\s\S]*width: 100%;[\s\S]*height: 100%;[\s\S]*object-fit: contain/)
  assert.match(styles, /\.scene-thumbnails \{[\s\S]*overflow-x: auto;[\s\S]*overflow-y: hidden;/)
  assert.match(styles, /\.scene-thumbnails img \{[\s\S]*inset: 5px;[\s\S]*width: calc\(100% - 10px\);[\s\S]*height: calc\(100% - 10px\);[\s\S]*object-fit: contain/)
})

test('closing the Works lightbox reveals the artwork card that was being viewed', () => {
  assert.match(pages, /pendingRevealArtworkId/)
  assert.match(pages, /artworkCards\.current\.get\(artworkId\)\?\.scrollIntoView/)
  assert.match(pages, /block: 'center'/)
  assert.match(pages, /if \(selectedArtwork\) pendingRevealArtworkId\.current = selectedArtwork\.id/)
  assert.match(pages, /className="artwork-card-anchor"/)
  assert.match(styles, /\.artwork-card-anchor \{[\s\S]*scroll-margin-block: 80px;/)
})

test('Scene rail preserves context when changing artworks and exposes accurate navigation state', () => {
  assert.match(scene, /pendingRailScrollLeft\.current = rail\.current\?\.scrollLeft/)
  assert.match(scene, /element\.scrollLeft = pendingRailScrollLeft\.current/)
  assert.match(scene, /activeRailItem\.current\?\.scrollIntoView\(\{ block: 'nearest', inline: 'nearest' \}\)/)
  assert.match(scene, /navigate\(`\/artworks\/\$\{selectedId\}\?scene=\$\{scene\}`\)/)
  assert.match(scene, /disabled=\{!railBounds\.canScrollLeft\}/)
  assert.match(scene, /disabled=\{!railBounds\.canScrollRight\}/)
  assert.match(scene, /aria-current=\{active \? 'true' : undefined\}/)
  assert.match(styles, /\.scene-thumbnails \{[\s\S]*scroll-snap-type: x proximity;/)
})

test('detail scenes omit the secondary toolbar and return its height to the exhibition space', () => {
  assert.doesNotMatch(scene, /className="scene-toolbar"/)
  assert.doesNotMatch(scene, /Scene \{scene\} \/ 3/)
  assert.doesNotMatch(scene, /다음 공간/)
  assert.match(styles, /\.scene-space \{[\s\S]*height: min\(calc\(68svh \+ 126px\), 826px\)/)
})

test('mobile scene assets and long-description drawer remain available', () => {
  assert.match(scene, /<source media="\(max-width: 700px\)"/)
  assert.match(styles, /@media \(max-width: 700px\)/)
  assert.match(story, /story-description/)
  assert.match(story, /role="dialog"/)
})
