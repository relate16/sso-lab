import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const pages = await readFile(new URL('../src/gallery/PublicPages.tsx', import.meta.url), 'utf8')
const inquiry = await readFile(new URL('../src/gallery/InquiryDrawer.tsx', import.meta.url), 'utf8')
const api = await readFile(new URL('../src/api.ts', import.meta.url), 'utf8')
const frame = await readFile(new URL('../src/gallery/ArtworkFrame.tsx', import.meta.url), 'utf8')
const scene = await readFile(new URL('../src/gallery/GalleryScene.tsx', import.meta.url), 'utf8')
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
  assert.match(frame, /fetchPriority="high"/)
  assert.match(scene, /prefers-reduced-motion: reduce/)
  assert.match(styles, /@media \(prefers-reduced-motion: reduce\)/)
})
