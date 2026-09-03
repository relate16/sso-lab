import assert from 'node:assert/strict'
import test from 'node:test'
import { requestSignupOtp, resendSignupOtp, verifySignupOtp } from '../src/signup.ts'

function response(body, status = 200) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: body === undefined ? {} : { 'Content-Type': 'application/json' },
  })
}

function csrfAwareFetcher(calls, result) {
  return async (url, init) => {
    calls.push({ url, init })
    return calls.length === 1
      ? response({ headerName: 'X-CSRF-TOKEN', token: 'test-csrf' })
      : response(result)
  }
}

test('signup start sends only identity fields and Turnstile token with CSRF', async () => {
  const calls = []
  const result = await requestSignupOtp({
    userId: 'new-user',
    username: 'New User',
    email: 'new-user@example.test',
    turnstileToken: 'test-turnstile',
  }, csrfAwareFetcher(calls, {
    challengeId: 'challenge',
    expiresAt: '2026-09-03T00:05:00Z',
    resendAvailableAt: '2026-09-03T00:01:00Z',
    message: 'generic',
  }))

  assert.equal(result.challengeId, 'challenge')
  assert.equal(calls[0].url, '/api/v1/csrf')
  assert.equal(calls[1].url, '/api/v1/signup/start')
  assert.equal(calls[1].init.credentials, 'include')
  assert.equal(calls[1].init.headers['X-CSRF-TOKEN'], 'test-csrf')
  assert.deepEqual(JSON.parse(calls[1].init.body), {
    userId: 'new-user',
    username: 'New User',
    email: 'new-user@example.test',
    turnstileToken: 'test-turnstile',
  })
  assert.equal(calls[1].init.body.includes('role'), false)
  assert.equal(calls[1].init.body.includes('bootstrap'), false)
})

test('signup resend uses the existing challenge and a fresh Turnstile token', async () => {
  const calls = []
  await resendSignupOtp('challenge', 'fresh-turnstile', csrfAwareFetcher(calls, {
    challengeId: 'challenge',
    expiresAt: '2026-09-03T00:06:00Z',
    resendAvailableAt: '2026-09-03T00:02:00Z',
    message: 'generic',
  }))

  assert.equal(calls[1].url, '/api/v1/signup/resend')
  assert.deepEqual(JSON.parse(calls[1].init.body), {
    challengeId: 'challenge',
    turnstileToken: 'fresh-turnstile',
  })
})

test('signup verification sends only challenge and OTP and does not create a browser session', async () => {
  const calls = []
  const result = await verifySignupOtp('challenge', '123456', csrfAwareFetcher(calls, {
    registered: true,
  }))

  assert.equal(result.registered, true)
  assert.equal(calls[1].url, '/api/v1/signup/verify')
  assert.deepEqual(JSON.parse(calls[1].init.body), {
    challengeId: 'challenge',
    code: '123456',
  })
})
