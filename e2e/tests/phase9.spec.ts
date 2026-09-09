import { createHmac } from 'node:crypto'
import { expect, test, type APIRequestContext, type Page } from '@playwright/test'

const authUrl = required('AUTH_URL')
const adminUrl = required('ADMIN_URL')
const hrUrl = required('HR_URL')
const approvalUrl = required('APPROVAL_URL')
const testKey = required('TEST_SUPPORT_API_KEY')

test.describe.configure({ mode: 'serial' })

test('Email OTP signup, HR to Approval SSO and central logout', async ({ page, request }) => {
  await setClock(request, new Date())
  await clearMail(request)
  await signupThroughUi(page, request, 'phase9-email-user', 'Phase Nine Email',
    'phase9-email@example.test')

  const hrClaims = await loginWithEmailOtp(page, request, hrUrl, 'Passwordless SSO 로그인',
    'phase9-email-user')
  expect(hrClaims.authenticated).toBe(true)
  expect(hrClaims.amr).toEqual(['email_otp'])
  expect(hrClaims.acr).toBe('urn:jb:loa:1')
  expect(hrClaims.roles).toEqual(['USER'])

  await page.goto(approvalUrl)
  await page.getByRole('link', { name: 'Passwordless SSO 로그인' }).click()
  await expect(page).toHaveURL(`${approvalUrl}/`)
  const approvalClaims = await visibleClaims(page)
  expect(approvalClaims.amr).toEqual(['email_otp'])
  expect(approvalClaims.acr).toBe('urn:jb:loa:1')

  await page.goto(hrUrl)
  await expect(page.getByText('SSO authenticated')).toBeVisible()
  await page.getByRole('button', { name: '현재 SSO Session 로그아웃' }).click()
  await page.waitForURL(`${hrUrl}/`)
  await expect(page.getByRole('link', { name: 'Passwordless SSO 로그인' })).toBeVisible()

  await expect.poll(async () => {
    await page.goto(approvalUrl)
    return page.getByRole('link', { name: 'Passwordless SSO 로그인' }).isVisible()
  }).toBe(true)
})

test('controllable-clock TOTP login keeps loa1 SSO claims', async ({ browser, request }) => {
  const baseTime = new Date()
  await setClock(request, baseTime)
  await clearMail(request)
  await signup(request, 'phase9-totp-user', 'Phase Nine Totp', 'phase9-totp@example.test')

  const enrollmentContext = await browser.newContext({ ignoreHTTPSErrors: true })
  const enrollmentPage = await enrollmentContext.newPage()
  await loginWithEmailOtp(enrollmentPage, request, hrUrl, 'Passwordless SSO 로그인',
    'phase9-totp-user')
  await enrollmentPage.goto(authUrl)
  await expect(enrollmentPage.getByRole('heading', { name: '내 정보' })).toBeVisible()

  await expect(enrollmentPage.getByRole('button', { name: 'TOTP 등록' })).toBeVisible()
  await enrollmentPage.getByRole('button', { name: 'TOTP 등록' }).click()
  const reauthStartResponse = enrollmentPage.waitForResponse(response =>
    response.url().endsWith('/api/v1/me/reauth/email/start')
    && response.request().method() === 'POST')
  await enrollmentPage.getByRole('button', { name: '재인증 이메일 코드 보내기' }).click()
  const reauthChallenge = await (await reauthStartResponse).json() as { challengeId: string }
  await enrollmentPage.getByLabel('TOTP 관리 재인증 코드').fill(
    await capturedCode(request, reauthChallenge.challengeId, 'LOGIN'))

  const enrollmentStartResponse = enrollmentPage.waitForResponse(response =>
    response.url().endsWith('/api/v1/me/totp/enroll/start')
    && response.request().method() === 'POST')
  await enrollmentPage.getByRole('button', { name: '재인증 확인' }).click()
  expect((await enrollmentStartResponse).ok()).toBeTruthy()
  await expect(enrollmentPage.getByTestId('totp-qr')).toBeVisible()
  await enrollmentPage.getByText('QR을 스캔할 수 없나요?').click()
  const otpauthUri = await enrollmentPage.getByTestId('otpauth-uri').innerText()
  expect(otpauthUri).toMatch(/^otpauth:\/\/totp\//)
  const secret = new URL(otpauthUri).searchParams.get('secret')
  expect(secret).toBeTruthy()
  const firstCode = totp(secret!, baseTime)

  await enrollmentPage.getByLabel('인증 앱의 6자리 코드').fill(differentTotp(firstCode))
  const invalidResponse = enrollmentPage.waitForResponse(response =>
    response.url().endsWith('/api/v1/me/totp/enroll/confirm')
    && response.request().method() === 'POST')
  await enrollmentPage.getByRole('button', { name: 'TOTP 등록 확인' }).click()
  expect((await invalidResponse).status()).toBe(400)
  await expect(enrollmentPage.getByText(
    '요청을 처리하지 못했습니다. 입력과 인증 상태를 확인해주세요.',
  )).toBeVisible()

  await enrollmentPage.getByLabel('인증 앱의 6자리 코드').fill(firstCode)
  const confirmResponse = enrollmentPage.waitForResponse(response =>
    response.url().endsWith('/api/v1/me/totp/enroll/confirm')
    && response.request().method() === 'POST')
  await enrollmentPage.getByRole('button', { name: 'TOTP 등록 확인' }).click()
  expect((await confirmResponse).ok()).toBeTruthy()
  const recoveryList = enrollmentPage.getByRole('list', { name: 'Recovery Code 목록' })
  await expect(recoveryList.getByRole('listitem')).toHaveCount(10)
  await expect(enrollmentPage.getByRole('region', { name: 'Recovery Code' })
    .getByText('복구 코드는 다시 표시되지 않습니다.', { exact: false })).toBeVisible()
  await assertCredentialStorageEmpty(enrollmentPage)
  await enrollmentPage.getByRole('button', { name: '보관 완료하고 닫기' }).click()
  await expect(recoveryList).toHaveCount(0)
  await expect(enrollmentPage.locator('.totp-panel').getByText('등록됨')).toBeVisible()

  const loginTime = new Date(baseTime.getTime() + 31_000)
  await setClock(request, loginTime)
  const context = await browser.newContext({ ignoreHTTPSErrors: true })
  const page = await context.newPage()
  await page.goto(hrUrl)
  await page.getByRole('link', { name: 'Passwordless SSO 로그인' }).click()
  await page.getByRole('button', { name: 'TOTP' }).click()
  await page.getByLabel('User ID').fill('phase9-totp-user')
  await page.getByLabel('인증 코드').fill(totp(secret!, loginTime))
  await page.getByRole('button', { name: '로그인' }).click()
  await page.waitForURL(`${hrUrl}/`)
  const hrClaims = await visibleClaims(page)
  expect(hrClaims.amr).toEqual(['totp'])
  expect(hrClaims.acr).toBe('urn:jb:loa:1')

  await page.goto(approvalUrl)
  await page.getByRole('link', { name: 'Passwordless SSO 로그인' }).click()
  await page.waitForURL(`${approvalUrl}/`)
  expect((await visibleClaims(page)).amr).toEqual(['totp'])

  const regenerateTime = new Date(baseTime.getTime() + 62_000)
  await setClock(request, regenerateTime)
  await enrollmentPage.goto(authUrl)
  await enrollmentPage.getByRole('button', { name: 'Recovery Code 재발급' }).click()
  await enrollmentPage.getByRole('group', { name: 'TOTP 관리 재인증 방법' })
    .getByRole('button', { name: 'TOTP' }).click()
  await enrollmentPage.getByLabel('TOTP 관리 재인증 코드').fill(totp(secret!, regenerateTime))
  const regenerateResponse = enrollmentPage.waitForResponse(response =>
    response.url().endsWith('/api/v1/me/recovery-codes/regenerate')
    && response.request().method() === 'POST')
  await enrollmentPage.getByRole('button', { name: '재인증 확인' }).click()
  expect((await regenerateResponse).ok()).toBeTruthy()
  await expect(enrollmentPage.getByRole('list', { name: 'Recovery Code 목록' })
    .getByRole('listitem')).toHaveCount(10)
  await enrollmentPage.getByRole('button', { name: '보관 완료하고 닫기' }).click()

  const disableTime = new Date(baseTime.getTime() + 93_000)
  await setClock(request, disableTime)
  await enrollmentPage.getByRole('button', { name: 'TOTP 해제' }).click()
  await enrollmentPage.getByRole('group', { name: 'TOTP 관리 재인증 방법' })
    .getByRole('button', { name: 'TOTP' }).click()
  await enrollmentPage.getByLabel('TOTP 관리 재인증 코드').fill(totp(secret!, disableTime))
  const disableResponse = enrollmentPage.waitForResponse(response =>
    response.url().endsWith('/api/v1/me/totp')
    && response.request().method() === 'DELETE')
  await enrollmentPage.getByRole('button', { name: '재인증 확인' }).click()
  expect((await disableResponse).status()).toBe(204)
  await expect(enrollmentPage.locator('.totp-panel').getByText('미등록')).toBeVisible()
  await expect(enrollmentPage.getByRole('button', { name: 'TOTP 등록' })).toBeVisible()
  await assertCredentialStorageEmpty(enrollmentPage)

  await enrollmentContext.close()
  await context.close()
})

test('profile username, verified email change revocation and hard delete', async ({ browser, request }) => {
  await setClock(request, new Date())
  await clearMail(request)
  await signup(request, 'phase9-self-user', 'Phase Nine Self', 'phase9-self@example.test')

  const primary = await browser.newContext({ ignoreHTTPSErrors: true })
  const secondary = await browser.newContext({ ignoreHTTPSErrors: true })
  const primaryPage = await primary.newPage()
  const secondaryPage = await secondary.newPage()
  await loginWithEmailOtp(primaryPage, request, hrUrl, 'Passwordless SSO 로그인',
    'phase9-self-user')
  await loginWithEmailOtp(secondaryPage, request, hrUrl, 'Passwordless SSO 로그인',
    'phase9-self-user')

  await primaryPage.goto(authUrl)
  await primaryPage.getByLabel('Username').fill('Phase Nine Updated')
  await primaryPage.getByRole('button', { name: 'username 변경' }).click()
  await expect(primaryPage.getByText('username을 변경했습니다.')).toBeVisible()

  const startResponse = primaryPage.waitForResponse(response =>
    response.url().endsWith('/api/v1/me/email-change/start') && response.request().method() === 'POST')
  await primaryPage.getByLabel('새 Email').fill('phase9-updated@example.test')
  await primaryPage.getByRole('button', { name: '새 이메일 인증 시작' }).click()
  const challenge = await (await startResponse).json() as { challengeId: string }
  const code = await capturedCode(request, challenge.challengeId, 'EMAIL_CHANGE')
  await primaryPage.getByLabel('새 Email OTP').fill(code)
  await primaryPage.getByRole('button', { name: '이메일 변경 확인' }).click()
  await expect(primaryPage.getByText('이메일을 변경하고 다른 Auth/BFF Session과 Refresh Token을 폐기했습니다.')).toBeVisible()
  await expect(primaryPage.getByText('phase9-updated@example.test')).toBeVisible()

  await expect.poll(async () => {
    await secondaryPage.goto(hrUrl)
    return secondaryPage.getByRole('link', { name: 'Passwordless SSO 로그인' }).isVisible()
  }).toBe(true)

  await primaryPage.goto(approvalUrl)
  await primaryPage.getByRole('link', { name: 'Passwordless SSO 로그인' }).click()
  await primaryPage.waitForURL(`${approvalUrl}/`)
  const changedClaims = await visibleClaims(primaryPage)
  expect(changedClaims.email).toBe('phase9-updated@example.test')
  expect(changedClaims.username).toBe('Phase Nine Updated')

  await primaryPage.goto(authUrl)
  const reauthResponse = primaryPage.waitForResponse(response =>
    response.url().endsWith('/api/v1/me/reauth/email/start') && response.request().method() === 'POST')
  await primaryPage.getByRole('button', { name: '탈퇴 재인증 OTP 보내기' }).click()
  const reauth = await (await reauthResponse).json() as { challengeId: string }
  await primaryPage.getByLabel('EMAIL_OTP 코드').fill(
    await capturedCode(request, reauth.challengeId, 'LOGIN'))
  await primaryPage.getByRole('button', { name: 'fresh re-authentication' }).click()
  await expect(primaryPage.getByText('fresh re-authentication을 완료했습니다.')).toBeVisible()
  await primaryPage.getByLabel('탈퇴하려면 DELETE 입력').fill('DELETE')
  await primaryPage.getByRole('button', { name: '계정 영구 삭제' }).click()
  await primaryPage.waitForURL(`${authUrl}/`)
  await expect(primaryPage.getByRole('heading', { name: '내 정보' })).toHaveCount(0)

  await primary.close()
  await secondary.close()
})

test('ADMIN can suspend and fresh-email-reauth before unmasking', async ({ page, request }) => {
  await setClock(request, new Date())
  await clearMail(request)
  await signup(request, 'phase9-admin-user', 'Phase Nine Admin', 'phase9-admin@example.test')
  await signup(request, 'phase9-admin-target', 'Phase Nine Target', 'phase9-target@example.test')

  await loginWithEmailOtp(page, request, adminUrl, 'Passwordless SSO 로그인',
    'phase9-admin-user', false)
  await expect(page.getByRole('heading', { name: 'Identity control plane' })).toBeVisible()
  await page.getByRole('button', { name: /Phase Nine Target/ }).click()
  await page.getByRole('button', { name: 'Suspend' }).click()
  await expect(page.getByRole('status')).toContainText('사용자 정지 완료')

  const reauthResponse = page.waitForResponse(response =>
    response.url().endsWith('/api/v1/admin/reauth/email/start')
    && response.request().method() === 'POST')
  await page.getByRole('button', { name: 'Email OTP 발급' }).click()
  const challenge = await (await reauthResponse).json() as { challengeId: string }
  await page.getByLabel('OTP', { exact: true }).fill(
    await capturedCode(request, challenge.challengeId, 'ADMIN_REAUTH'))
  await page.getByRole('button', { name: '검증', exact: true }).click()
  await expect(page.getByRole('status')).toContainText('재인증 완료')
  await page.getByRole('button', { name: 'Email reveal' }).click()
  await expect(page.getByText('phase9-target@example.test')).toBeVisible()
})

async function signup(
  request: APIRequestContext,
  userId: string,
  username: string,
  email: string,
) {
  const started = await csrfPost(request, `${authUrl}/api/v1/signup/start`, {
    userId, username, email, turnstileToken: '',
  })
  expect(started.ok()).toBeTruthy()
  const challenge = await started.json() as { challengeId: string }
  const verified = await csrfPost(request, `${authUrl}/api/v1/signup/verify`, {
    challengeId: challenge.challengeId,
    code: await capturedCode(request, challenge.challengeId, 'SIGNUP'),
  })
  expect(verified.ok()).toBeTruthy()
}

async function signupThroughUi(
  page: Page,
  request: APIRequestContext,
  userId: string,
  username: string,
  email: string,
) {
  await page.goto(authUrl)
  await page.getByRole('tab', { name: '회원가입' }).click()
  await page.getByLabel('User ID').fill(userId)
  await page.getByLabel('Username').fill(username)
  await page.getByLabel('Email', { exact: true }).fill(email)

  const startResponse = page.waitForResponse(response =>
    response.url().endsWith('/api/v1/signup/start') && response.request().method() === 'POST')
  await page.getByRole('button', { name: '회원가입 OTP 보내기' }).click()
  const challenge = await (await startResponse).json() as { challengeId: string }
  await page.getByLabel('회원가입 인증 코드').fill(
    await capturedCode(request, challenge.challengeId, 'SIGNUP'))
  await page.getByRole('button', { name: '회원가입 완료' }).click()

  await expect(page.getByRole('tab', { name: '로그인' })).toHaveAttribute('aria-selected', 'true')
  await expect(page.getByLabel('User ID')).toHaveValue(userId)
  await expect(page.getByText('회원가입이 완료되었습니다. Email OTP 또는 등록된 TOTP로 로그인해주세요.')).toBeVisible()
  await expect(page.getByRole('heading', { name: '내 정보' })).toHaveCount(0)
}

async function loginWithEmailOtp(
  page: Page,
  request: APIRequestContext,
  clientUrl: string,
  linkName: string,
  userId: string,
  claimsExpected = true,
) {
  await page.goto(clientUrl)
  await page.getByRole('link', { name: linkName }).click()
  await expect(page.getByRole('heading', { name: 'Passwordless sign in' })).toBeVisible()
  await page.getByLabel('User ID').fill(userId)
  const sentResponse = page.waitForResponse(response =>
    response.url().endsWith('/api/v1/login/email/send') && response.request().method() === 'POST')
  await page.getByRole('button', { name: 'OTP 보내기' }).click()
  const sent = await (await sentResponse).json() as { challengeId: string }
  await page.getByLabel('인증 코드').fill(
    await capturedCode(request, sent.challengeId, 'LOGIN'))
  await page.getByRole('button', { name: '로그인' }).click()
  await page.waitForURL(`${clientUrl}/`)
  return claimsExpected ? visibleClaims(page) : {}
}

async function visibleClaims(page: Page): Promise<Record<string, unknown>> {
  await expect(page.getByText('SSO authenticated')).toBeVisible()
  return JSON.parse(await page.locator('pre').innerText()) as Record<string, unknown>
}

async function csrfPost(request: APIRequestContext, url: string, data: unknown) {
  const csrfResponse = await request.get(`${authUrl}/api/v1/csrf`)
  expect(csrfResponse.ok()).toBeTruthy()
  const csrf = await csrfResponse.json() as { headerName: string; token: string }
  return request.post(url, { data, headers: { [csrf.headerName]: csrf.token } })
}

async function capturedCode(
  request: APIRequestContext,
  challengeId: string,
  purpose: string,
): Promise<string> {
  const response = await request.get(
    `${authUrl}/test-support/v1/mail/${challengeId}?purpose=${purpose}`,
    { headers: { 'X-Test-Support-Key': testKey } },
  )
  expect(response.ok()).toBeTruthy()
  return (await response.json() as { code: string }).code
}

async function clearMail(request: APIRequestContext) {
  const response = await request.delete(`${authUrl}/test-support/v1/mail`, {
    headers: { 'X-Test-Support-Key': testKey },
  })
  expect(response.ok()).toBeTruthy()
}

async function setClock(request: APIRequestContext, instant: Date) {
  const response = await request.post(`${authUrl}/test-support/v1/clock`, {
    headers: { 'X-Test-Support-Key': testKey },
    data: { instant: instant.toISOString() },
  })
  expect(response.ok()).toBeTruthy()
}

async function browserMutation<T>(
  page: Page,
  path: string,
  method: string,
  body?: unknown,
): Promise<T> {
  return page.evaluate(async ({ path, method, body }) => {
    const csrfResponse = await fetch('/api/v1/csrf', { credentials: 'include' })
    const csrf = await csrfResponse.json() as { headerName: string; token: string }
    const response = await fetch(path, {
      method,
      credentials: 'include',
      headers: {
        [csrf.headerName]: csrf.token,
        ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
      },
      body: body === undefined ? undefined : JSON.stringify(body),
    })
    if (!response.ok) throw new Error(`${response.status} ${await response.text()}`)
    return response.json()
  }, { path, method, body }) as Promise<T>
}

function totp(encodedSecret: string, instant: Date): string {
  const secret = decodeBase32(encodedSecret)
  const counter = BigInt(Math.floor(instant.getTime() / 1000 / 30))
  const buffer = Buffer.alloc(8)
  buffer.writeBigUInt64BE(counter)
  const digest = createHmac('sha1', secret).update(buffer).digest()
  const offset = digest[digest.length - 1] & 0x0f
  const binary = ((digest[offset] & 0x7f) << 24)
    | ((digest[offset + 1] & 0xff) << 16)
    | ((digest[offset + 2] & 0xff) << 8)
    | (digest[offset + 3] & 0xff)
  return String(binary % 1_000_000).padStart(6, '0')
}

function differentTotp(current: string): string {
  return current === '000000' ? '000001' : '000000'
}

async function assertCredentialStorageEmpty(page: Page) {
  const state = await page.evaluate(async () => ({
    localEntries: window.localStorage.length,
    sessionEntries: window.sessionStorage.length,
    databases: (await window.indexedDB.databases()).length,
  }))
  expect(state).toEqual({ localEntries: 0, sessionEntries: 0, databases: 0 })
}

function decodeBase32(value: string): Buffer {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567'
  let bits = ''
  for (const character of value.replace(/=+$/, '').toUpperCase()) {
    const index = alphabet.indexOf(character)
    if (index < 0) throw new Error('invalid test TOTP secret')
    bits += index.toString(2).padStart(5, '0')
  }
  const bytes = []
  for (let index = 0; index + 8 <= bits.length; index += 8) {
    bytes.push(Number.parseInt(bits.slice(index, index + 8), 2))
  }
  return Buffer.from(bytes)
}

function required(name: string): string {
  const value = process.env[name]
  if (!value) throw new Error(`${name} must be set`)
  return value
}
