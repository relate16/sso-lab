import assert from 'node:assert/strict'
import test from 'node:test'
import { csrfMutation, readJson } from '../src/api.ts'

function response(body, status = 200) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: body === undefined ? {} : { 'Content-Type': 'application/json' },
  })
}

test('profile read uses same-origin credentials and disables browser cache', async () => {
  const calls = []
  const result = await readJson('/api/v1/me/profile', async (url, init) => {
    calls.push({ url, init })
    return response({ userId: 'test-user', email: 't***@example.test' })
  })
  assert.equal(result.userId, 'test-user')
  assert.deepEqual(calls, [{
    url: '/api/v1/me/profile',
    init: { credentials: 'include', cache: 'no-store' },
  }])
})

test('username, email change and hard delete mutations always acquire and send CSRF', async () => {
  for (const [path, method, body] of [
    ['/api/v1/me/profile/username', 'PATCH', { username: 'updated-name' }],
    ['/api/v1/me/email-change/start', 'POST', { newEmail: 'next@example.test' }],
    ['/api/v1/me/email-change/verify', 'POST', { challengeId: 'challenge', code: '000000' }],
    ['/api/v1/me/account', 'DELETE', { confirmation: 'DELETE' }],
  ]) {
    const calls = []
    await csrfMutation(path, method, body, async (url, init) => {
      calls.push({ url, init })
      return calls.length === 1
        ? response({ headerName: 'X-CSRF-TOKEN', token: 'test-csrf' })
        : response(undefined, 204)
    })
    assert.equal(calls[0].url, '/api/v1/csrf')
    assert.equal(calls[1].url, path)
    assert.equal(calls[1].init.credentials, 'include')
    assert.equal(calls[1].init.headers['X-CSRF-TOKEN'], 'test-csrf')
    assert.equal(calls[1].init.body, JSON.stringify(body))
  }
})

test('failed mutation returns a generic error without reflecting sensitive response content', async () => {
  const fetcher = async (_url, _init) => _url === '/api/v1/csrf'
    ? response({ headerName: 'X-CSRF-TOKEN', token: 'test-csrf' })
    : response({ detail: '000000 next@example.test' }, 400)
  await assert.rejects(
    csrfMutation('/api/v1/me/email-change/verify', 'POST', { code: '000000' }, fetcher),
    error => error.message === '요청을 처리하지 못했습니다.' && !error.message.includes('000000'),
  )
})
