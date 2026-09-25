import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const pages = await readFile(new URL('../src/gallery/PublicPages.tsx', import.meta.url), 'utf8')
const inquiry = await readFile(new URL('../src/gallery/InquiryDrawer.tsx', import.meta.url), 'utf8')

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
