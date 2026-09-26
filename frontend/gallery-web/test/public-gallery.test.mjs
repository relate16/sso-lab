import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const pages = await readFile(new URL('../src/gallery/PublicPages.tsx', import.meta.url), 'utf8')
const inquiry = await readFile(new URL('../src/gallery/InquiryDrawer.tsx', import.meta.url), 'utf8')
const api = await readFile(new URL('../src/api.ts', import.meta.url), 'utf8')
const frame = await readFile(new URL('../src/gallery/ArtworkFrame.tsx', import.meta.url), 'utf8')
const scene = await readFile(new URL('../src/gallery/GalleryScene.tsx', import.meta.url), 'utf8')
const homeScene = await readFile(new URL('../src/gallery/HomeExhibition.tsx', import.meta.url), 'utf8')
const definitions = await readFile(new URL('../src/gallery/sceneDefinitions.ts', import.meta.url), 'utf8')
const story = await readFile(new URL('../src/gallery/ArtworkStoryDrawer.tsx', import.meta.url), 'utf8')
const styles = await readFile(new URL('../src/styles.css', import.meta.url), 'utf8')

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
  assert.match(frame, /fetchPriority=\{priority \? 'high' : 'auto'\}/)
  assert.match(scene, /prefers-reduced-motion: reduce/)
  assert.match(styles, /@media \(prefers-reduced-motion: reduce\)/)
})

test('Home maps only the latest four artworks into configured image slots', () => {
  assert.match(homeScene, /artworks\.slice\(0, 4\)/)
  assert.match(homeScene, /HOME_SLOTS\[index\]/)
  assert.match(definitions, /gallery-home-scene\.webp/)
})

test('detail scenes use approved backgrounds and keep artwork front-facing', () => {
  for (const sceneNumber of [1, 2, 3]) assert.match(definitions, new RegExp(`gallery-detail-scene-${sceneNumber}\\.webp`))
  assert.doesNotMatch(frame, /rotateY|skew|perspective/)
  assert.match(styles, /mounted-artwork-detail[\s\S]*transform: none/)
})

test('scene zoom includes reset, Escape and reduced-motion handling', () => {
  assert.match(scene, /setZoomed/)
  assert.match(scene, /event\.key === 'Escape'/)
  assert.match(scene, /원래 보기/)
  assert.match(styles, /scene-space\.is-zoomed \.scene-camera/)
  assert.match(styles, /transition: transform 340ms ease/)
})

test('mobile scene assets and long-description drawer remain available', () => {
  assert.match(scene, /<source media="\(max-width: 700px\)"/)
  assert.match(styles, /@media \(max-width: 700px\)/)
  assert.match(story, /story-description/)
  assert.match(story, /role="dialog"/)
})
