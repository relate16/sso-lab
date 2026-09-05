import assert from 'node:assert/strict'
import test from 'node:test'
import {
  confirmTotpEnrollment,
  disableTotp,
  recoveryCodesForClipboard,
  regenerateRecoveryCodes,
  startTotpEnrollment,
  startTotpManagementEmailReauthentication,
  verifyTotpManagementReauthentication,
} from '../src/totp.ts'

function response(body, status = 200) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: body === undefined ? {} : { 'Content-Type': 'application/json' },
  })
}

function csrfAwareFetcher(calls, result, resultStatus = 200) {
  return async (url, init) => {
    calls.push({ url, init })
    return calls.length === 1
      ? response({ headerName: 'X-CSRF-TOKEN', token: 'test-csrf' })
      : response(result, resultStatus)
  }
}

test('TOTP enrollment start and six-digit confirmation use authenticated CSRF mutations', async () => {
  const startCalls = []
  const enrollment = await startTotpEnrollment(csrfAwareFetcher(startCalls, {
    otpauthUri: 'otpauth://totp/Example:test-user?secret=EXAMPLEONLY',
  }))
  assert.match(enrollment.otpauthUri, /^otpauth:\/\/totp\//)
  assert.equal(startCalls[1].url, '/api/v1/me/totp/enroll/start')
  assert.equal(startCalls[1].init.method, 'POST')
  assert.equal(startCalls[1].init.credentials, 'include')
  assert.equal(startCalls[1].init.headers['X-CSRF-TOKEN'], 'test-csrf')

  const confirmCalls = []
  const result = await confirmTotpEnrollment('123456', csrfAwareFetcher(confirmCalls, {
    recoveryCodes: Array.from({ length: 10 }, (_, index) => `example-code-${index}`),
  }))
  assert.equal(result.recoveryCodes.length, 10)
  assert.equal(confirmCalls[1].url, '/api/v1/me/totp/enroll/confirm')
  assert.deepEqual(JSON.parse(confirmCalls[1].init.body), { code: '123456' })
})

test('TOTP management reauthentication supports email OTP and registered TOTP', async () => {
  const emailCalls = []
  await startTotpManagementEmailReauthentication(csrfAwareFetcher(emailCalls, {
    challengeId: 'example-challenge',
    expiresAt: '2026-09-04T00:05:00Z',
    resendAvailableAt: '2026-09-04T00:01:00Z',
  }))
  assert.equal(emailCalls[1].url, '/api/v1/me/reauth/email/start')

  for (const [method, challengeId] of [['EMAIL_OTP', 'example-challenge'], ['TOTP', null]]) {
    const calls = []
    await verifyTotpManagementReauthentication(
      method,
      '123456',
      challengeId,
      csrfAwareFetcher(calls, { reauthenticated: true, expiresAt: '2026-09-04T00:05:00Z' }),
    )
    assert.equal(calls[1].url, '/api/v1/me/reauth/verify')
    assert.deepEqual(JSON.parse(calls[1].init.body), { method, challengeId, code: '123456' })
  }
})

test('Recovery Code regeneration and TOTP disable use their existing protected endpoints', async () => {
  const regenerateCalls = []
  await regenerateRecoveryCodes(csrfAwareFetcher(regenerateCalls, {
    recoveryCodes: Array.from({ length: 10 }, (_, index) => `replacement-${index}`),
  }))
  assert.equal(regenerateCalls[1].url, '/api/v1/me/recovery-codes/regenerate')
  assert.equal(regenerateCalls[1].init.method, 'POST')

  const disableCalls = []
  await disableTotp(csrfAwareFetcher(disableCalls, undefined, 204))
  assert.equal(disableCalls[1].url, '/api/v1/me/totp')
  assert.equal(disableCalls[1].init.method, 'DELETE')
})

test('Recovery Code clipboard payload is explicit newline text without persistence', () => {
  assert.equal(recoveryCodesForClipboard(['one', 'two', 'three']), 'one\ntwo\nthree')
})
