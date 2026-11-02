import assert from 'node:assert/strict'
import test from 'node:test'
import { studioApi } from '../src/api.ts'

const csrf = { headerName: 'X-CSRF-TOKEN', parameterName: '_csrf', token: 'test-csrf-token' }

test('logout posts with session credentials and CSRF and returns the provider URL', async (t) => {
  const logoutUrl = 'https://auth.example.test/connect/logout?id_token_hint=test-token'
  const fetch = t.mock.method(globalThis, 'fetch', async () => Response.json({ logoutUrl }))

  assert.deepEqual(await studioApi.logout(csrf), { logoutUrl })
  const [path, options] = fetch.mock.calls[0].arguments
  assert.equal(path, '/api/v1/logout')
  assert.equal(options.method, 'POST')
  assert.equal(options.credentials, 'include')
  assert.equal(options.headers['X-CSRF-TOKEN'], csrf.token)
  assert.equal(options.redirect, 'error')
  assert.equal(options.cache, 'no-store')
})

test('logout reports a rejected CSRF token as a failure', async (t) => {
  t.mock.method(globalThis, 'fetch', async () => new Response(null, { status: 403 }))
  await assert.rejects(studioApi.logout(csrf), /권한이 없습니다/)
})

test('logout propagates connection failures instead of treating them as success', async (t) => {
  t.mock.method(globalThis, 'fetch', async () => { throw new TypeError('Failed to fetch') })
  await assert.rejects(studioApi.logout(csrf), /Failed to fetch/)
})
