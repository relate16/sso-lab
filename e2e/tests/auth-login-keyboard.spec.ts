import { expect, test, type Page } from '@playwright/test'

const authUrl = process.env.AUTH_URL ?? 'http://127.0.0.1:5188'
test.use({ launchOptions: process.env.E2E_BROWSER_EXECUTABLE
  ? { executablePath: process.env.E2E_BROWSER_EXECUTABLE } : {} })

async function loginPage(page: Page, gates: { send?: Promise<void>; verify?: Promise<void> } = {}) {
  const calls: { path: string; body: Record<string, string> }[] = []
  await page.addInitScript(() => {
    Object.assign(window, { ssoLabRuntimeConfig: { turnstileEnabled: false } })
  })
  await page.route('**/api/v1/**', async route => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/v1/me/sessions') {
      await route.fulfill({ status: 401, json: {} }); return
    }
    if (path === '/api/v1/csrf') {
      await route.fulfill({ json: { headerName: 'X-CSRF-TOKEN', token: 'test-csrf' } }); return
    }
    const body = route.request().postDataJSON() ?? {}
    calls.push({ path, body })
    if (path === '/api/v1/login/email/send') {
      await gates.send
      await route.fulfill({ json: { challengeId: 'test-challenge',
        expiresAt: new Date(Date.now() + 300_000).toISOString(),
        resendAvailableAt: new Date(Date.now() + 60_000).toISOString(), message: 'sent' } })
    } else if (path.endsWith('/verify')) {
      await gates.verify
      const valid = body.code === '123456'
        && (body.challengeId === 'test-challenge' || body.userId === 'keyboard-user')
      await route.fulfill({ status: valid ? 200 : 400,
        json: valid ? { authenticated: true, continuationPath: '/?logged-in=1' } : {} })
    } else {
      await route.fulfill({ json: {} })
    }
  })
  await page.goto(authUrl)
  await page.getByLabel('User ID', { exact: true }).fill('keyboard-user')
  return calls
}

for (const action of ['Enter', 'click'] as const) {
  test(`TOTP login works via ${action}`, async ({ page }) => {
    const calls = await loginPage(page)
    await page.getByRole('button', { name: 'TOTP', exact: true }).click()
    const code = page.getByLabel('인증 코드', { exact: true })
    await code.fill('123456')
    if (action === 'Enter') await code.press('Enter')
    else await page.getByRole('button', { name: '로그인', exact: true }).click()
    await expect(page).toHaveURL(/\?logged-in=1$/)
    expect(calls.map(call => call.path)).toEqual([
      '/api/v1/login/start', '/api/v1/login/totp/verify',
    ])
  })
}

test('repeated submission while verification is pending sends only one request', async ({ page }) => {
  let release!: () => void
  const verify = new Promise<void>(resolve => { release = resolve })
  const calls = await loginPage(page, { verify })
  try {
    await page.getByRole('button', { name: 'OTP 보내기', exact: true }).click()
    await page.getByLabel('인증 코드', { exact: true }).fill('123456')
    await expect(page.getByRole('button', { name: '로그인', exact: true })).toBeEnabled()
    await page.locator('#login-panel form').evaluate((form: HTMLFormElement) => {
      form.requestSubmit()
      form.requestSubmit()
    })
    await expect.poll(() => calls.filter(call => call.path.endsWith('/verify')).length).toBe(1)
    await expect(page.getByRole('button', { name: '로그인', exact: true })).toBeDisabled()
  } finally { release() }
  await expect(page).toHaveURL(/\?logged-in=1$/)
})

test('submission before the OTP challenge is ready never verifies an empty challenge', async ({ page }) => {
  let release!: () => void
  const send = new Promise<void>(resolve => { release = resolve })
  const calls = await loginPage(page, { send })
  try {
    await page.getByRole('button', { name: 'OTP 보내기', exact: true }).click()
    await page.getByLabel('인증 코드', { exact: true }).fill('123456')
    await page.locator('#login-panel form').evaluate((form: HTMLFormElement) => form.requestSubmit())
    await expect(page.getByRole('button', { name: '로그인', exact: true })).toBeDisabled()
    expect(calls.filter(call => call.path.endsWith('/verify'))).toHaveLength(0)
  } finally { release() }
  await expect(page.getByRole('button', { name: '로그인', exact: true })).toBeEnabled()
  await page.getByLabel('인증 코드', { exact: true }).press('Enter')
  await expect(page).toHaveURL(/\?logged-in=1$/)
})

test('Enter with an incomplete OTP does not send a verification request', async ({ page }) => {
  const calls = await loginPage(page)
  await page.getByRole('button', { name: 'OTP 보내기', exact: true }).click()
  await page.getByLabel('인증 코드', { exact: true }).fill('123')
  await page.getByLabel('인증 코드', { exact: true }).press('Enter')
  await expect(page.getByRole('button', { name: '로그인', exact: true })).toBeDisabled()
  expect(calls.filter(call => call.path.endsWith('/verify'))).toHaveLength(0)
})

test('Enter on User ID sends the email OTP instead of verifying an empty code', async ({ page }) => {
  const calls = await loginPage(page)
  await page.getByLabel('User ID', { exact: true }).press('Enter')
  await expect(page.getByLabel('인증 코드', { exact: true })).toBeVisible()
  expect(calls.map(call => call.path)).toEqual(['/api/v1/login/email/send'])
})

for (const action of ['Enter', 'click'] as const) {
  test(`email OTP login works via ${action}`, async ({ page }) => {
    const calls = await loginPage(page)
    await page.getByRole('button', { name: 'OTP 보내기', exact: true }).click()
    const code = page.getByLabel('인증 코드', { exact: true })
    await code.fill('123456')
    await expect(page.getByRole('button', { name: '로그인', exact: true })).toBeEnabled()
    if (action === 'Enter') await code.press('Enter')
    else await page.getByRole('button', { name: '로그인', exact: true }).click()
    await expect(page).toHaveURL(/\?logged-in=1$/)
    expect(calls.filter(call => call.path.endsWith('/verify'))).toEqual([
      { path: '/api/v1/login/email/verify', body: { challengeId: 'test-challenge', code: '123456' } },
    ])
  })
}
