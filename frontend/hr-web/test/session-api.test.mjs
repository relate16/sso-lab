import assert from 'node:assert/strict'
import test from 'node:test'
import { loadBffState } from '../src/session-api.ts'

test('HR loads only verified claims and CSRF from same-origin BFF', async () => {
  const calls = []
  const state = await loadBffState(async (url, init) => {
    calls.push({ url, init })
    return new Response(JSON.stringify(url.endsWith('/session')
      ? { authenticated: true, amr: ['email_otp'], acr: 'urn:jb:loa:1' }
      : { parameterName: '_csrf', token: 'test-csrf' }), {
      headers: { 'Content-Type': 'application/json' },
    })
  })
  assert.deepEqual(calls.map(call => call.url), ['/api/v1/session', '/api/v1/csrf'])
  assert.ok(calls.every(call => call.init.credentials === 'include' && call.init.cache === 'no-store'))
  assert.deepEqual(state.session.amr, ['email_otp'])
  assert.equal(state.session.acr, 'urn:jb:loa:1')
  assert.equal(state.csrf.parameterName, '_csrf')
})

test('HR treats an unauthorized session response as signed out', async () => {
  const state = await loadBffState(async url => url.endsWith('/session')
    ? new Response(null, { status: 401 })
    : new Response(JSON.stringify({ parameterName: '_csrf', token: 'test-csrf' })))
  assert.equal(state.session, null)
})
