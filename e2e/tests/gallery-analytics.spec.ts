import { expect, test, type Page } from '@playwright/test'

const galleryUrl = process.env.GALLERY_URL ?? 'http://127.0.0.1:5189'
test.use({ launchOptions: process.env.E2E_BROWSER_EXECUTABLE
  ? { executablePath: process.env.E2E_BROWSER_EXECUTABLE } : {} })

async function analyticsPage(page: Page, failInitially = false) {
  let fail = failInitially
  const requestedPages: number[] = []
  const metric = { views: 21, visitors: 2 }
  await page.route('**/api/v1/**', async route => {
    const url = new URL(route.request().url())
    if (url.pathname === '/api/v1/session') {
      await route.fulfill({ json: { authenticated: true, roles: ['ADMIN'] } })
    } else if (url.pathname === '/api/v1/csrf') {
      await route.fulfill({ json: { headerName: 'X-CSRF-TOKEN', token: 'test-csrf' } })
    } else if (url.pathname === '/api/v1/gallery/studio/analytics') {
      await route.fulfill({ json: { lifetime: metric, today: metric, last7Days: metric,
        last30Days: metric, daily: [], topArtworks: [], surfaces: [], sources: [], referrers: [] } })
    } else if (url.pathname === '/api/v1/gallery/studio/analytics/views') {
      const index = Number(url.searchParams.get('page'))
      requestedPages.push(index)
      if (fail) {
        await route.fulfill({ status: 500, json: {} })
        return
      }
      const content = Array.from({ length: index === 0 ? 20 : 1 }, (_, i) => ({
        id: `view-${index * 20 + i}`, artworkId: 'test-work',
        artworkTitle: index === 0 ? `작품 ${i + 1}` : '이전 작품', archived: index === 1,
        surface: 'WORKS_GRID', viewedAt: '2026-11-05T01:00:00Z',
        visitorIp: index === 1 ? null : i === 0 ? '2001:db8::1' : '203.0.113.10',
      }))
      await route.fulfill({ json: { content, page: index, size: 20, totalElements: 21, totalPages: 2 } })
    } else {
      await route.fulfill({ status: 404, json: {} })
    }
  })
  await page.goto(`${galleryUrl}/studio/analytics`)
  return { requestedPages, allowRecords: () => { fail = false } }
}

test('Studio shows raw IPs and pages through records with missing IP and archived works', async ({ page }) => {
  const { requestedPages } = await analyticsPage(page)
  const records = page.getByRole('region', { name: '최근 작품 조회 기록' })
  await expect(records.locator('tbody tr')).toHaveCount(20)
  await expect(records.getByText('2001:db8::1', { exact: true })).toBeVisible()
  await expect(records.getByText('홈 전시 벽면', { exact: true })).toHaveCount(0)
  await expect(records.getByText('작품 목록', { exact: true })).toHaveCount(20)
  const pager = page.getByRole('navigation', { name: '최근 조회 기록 페이지' })
  await expect(pager.getByRole('button', { name: '이전' })).toBeDisabled()
  await pager.getByRole('button', { name: '다음' }).click()
  await expect(records.locator('tbody tr')).toHaveCount(1)
  await expect(records.getByText('미수집', { exact: true })).toBeVisible()
  await expect(records.getByText('· 보관됨')).toBeVisible()
  await expect(pager).toContainText('2 / 2 · 총 21건')
  await expect(pager.getByRole('button', { name: '다음' })).toBeDisabled()
  await pager.getByRole('button', { name: '이전' }).click()
  await expect(records.locator('tbody tr')).toHaveCount(20)
  // Development StrictMode may replay the initial effect.
  expect(requestedPages.filter((value, index) => index === 0 || value !== requestedPages[index - 1]))
    .toEqual([0, 1, 0])
})

test('record failures can be retried without losing the statistics overview', async ({ page }) => {
  const { allowRecords } = await analyticsPage(page, true)
  await expect(page.getByRole('alert')).toContainText('최근 조회 기록을 불러오지 못했습니다.')
  await expect(page.getByText('전체 누적', { exact: true })).toBeVisible()
  allowRecords()
  await page.getByRole('button', { name: '다시 시도' }).click()
  await expect(page.getByRole('region', { name: '최근 작품 조회 기록' }).locator('tbody tr')).toHaveCount(20)
})

test('mobile keeps the records table scrollable without widening the page', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await analyticsPage(page)
  const records = page.getByRole('region', { name: '최근 작품 조회 기록' })
  await expect(records.locator('tbody tr')).toHaveCount(20)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  expect(await records.evaluate(element => element.scrollWidth > element.clientWidth)).toBe(true)
})
