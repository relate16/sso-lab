import assert from 'node:assert/strict'
import test from 'node:test'
import { loadBffState } from '../src/session-api.ts'

test('Approval loads SSO claims and CSRF without browser token storage', async () => {
  const calls = []
  const state = await loadBffState(async (url, init) => {
    calls.push({ url, init })
    return new Response(JSON.stringify(url.endsWith('/session')
      ? { authenticated: true, amr: ['totp'], acr: 'urn:jb:loa:1' }
      : { parameterName: '_csrf', token: 'test-csrf' }), {
      headers: { 'Content-Type': 'application/json' },
    })
  })
  assert.ok(calls.every(call => call.init.credentials === 'include' && call.init.cache === 'no-store'))
  assert.deepEqual(state.session.amr, ['totp'])
  assert.equal(state.session.acr, 'urn:jb:loa:1')
  assert.equal(state.csrf.token, 'test-csrf')
})

test('Approval does not synthesize a session from a failed BFF response', async () => {
  const state = await loadBffState(async url => url.endsWith('/session')
    ? new Response(null, { status: 403 })
    : new Response(null, { status: 403 }))
  assert.deepEqual(state, { session: null, csrf: null })
})
