import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const hook = await readFile(new URL('../src/gallery/useArtworkView.ts', import.meta.url), 'utf8')
const api = await readFile(new URL('../src/api.ts', import.meta.url), 'utf8')
const studio = await readFile(new URL('../src/studio/StudioAnalytics.tsx', import.meta.url), 'utf8')
const studioApp = await readFile(new URL('../src/studio/StudioApp.tsx', import.meta.url), 'utf8')
const migration = await readFile(new URL('../../../backend/gallery-server/src/main/resources/db/migration/V3__add_artwork_analytics_and_archiving.sql', import.meta.url), 'utf8')
const artworkService = await readFile(new URL('../../../backend/gallery-server/src/main/java/com/ssolab/gallery/artwork/StudioArtworkService.java', import.meta.url), 'utf8')
const viewService = await readFile(new URL('../../../backend/gallery-server/src/main/java/com/ssolab/gallery/analytics/ArtworkViewService.java', import.meta.url), 'utf8')

test('artwork views require sustained visible exposure and fail silently', () => {
  assert.match(hook, /const VIEW_DELAY_MS = 1500/)
  assert.match(hook, /const VIEW_THRESHOLD = \.5/)
  assert.match(hook, /document\.visibilityState === 'visible'/)
  assert.match(hook, /IntersectionObserver/)
  assert.match(hook, /publicApi\.view[\s\S]*\.catch\(\(\) => undefined\)/)
  assert.match(api, /keepalive: true/)
})

test('analytics schema stores Seoul reporting dates and exact rolling dedup state', () => {
  assert.match(migration, /CREATE TABLE artwork_view_events/)
  assert.match(migration, /viewed_date DATE NOT NULL/)
  assert.match(migration, /CREATE TABLE artwork_view_dedup/)
  assert.match(migration, /PRIMARY KEY \(artwork_id, visitor_hash, surface\)/)
})

test('visitor analytics pseudonymizes the random cookie with plain SHA-256', () => {
  assert.match(viewService, /MessageDigest\.getInstance\("SHA-256"\)/)
  assert.doesNotMatch(viewService, /HmacSHA256|visitorHmacSecret/)
})

test('Studio exposes private real-time overview statistics', () => {
  assert.match(studioApp, /to="\/studio\/analytics"/)
  for (const copy of ['전체 누적', '오늘', '최근 7일', '최근 30일', '가장 많이 본 작품', '유입 경로']) assert.match(studio, new RegExp(copy))
  assert.match(api, /\/api\/v1\/gallery\/studio\/analytics/)
})

test('artwork removal archives records instead of physically deleting them', () => {
  assert.match(migration, /ADD COLUMN archived_at/)
  assert.match(artworkService, /artwork\.archive\(clock\.instant\(\)\)/)
  assert.doesNotMatch(artworkService, /artworks\.delete\(artwork\)/)
})
