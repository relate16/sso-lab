import assert from 'node:assert/strict'
import test from 'node:test'
import { adminJson, adminMutation } from '../src/api.ts'

function response(body, status = 200, statusText = 'OK') {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    statusText,
    headers: body === undefined ? {} : { 'Content-Type': 'application/json' },
  })
}

test('admin reads keep tokens in the BFF session and disable cache', async () => {
  const calls = []
  await adminJson('/api/v1/admin/users', {}, async (url, init) => {
    calls.push({ url, init })
    return response({ content: [], totalElements: 0 })
  })
  assert.equal(calls[0].init.credentials, 'include')
  assert.equal(calls[0].init.cache, 'no-store')
  assert.equal(Object.hasOwn(calls[0].init, 'headers'), false)
})

test('suspend, role, group and email reveal require server-issued CSRF', async () => {
  const csrf = { headerName: 'X-CSRF-TOKEN', parameterName: '_csrf', token: 'test-csrf' }
  for (const [url, method, body] of [
    ['/api/v1/admin/users/user-id/suspend', 'POST', undefined],
    ['/api/v1/admin/users/user-id/roles', 'PUT', { roles: ['USER', 'ADMIN'] }],
    ['/api/v1/admin/users/user-id/groups', 'PUT', { groupIds: ['group-id'] }],
    ['/api/v1/admin/users/user-id/email/reveal', 'POST', undefined],
  ]) {
    const calls = []
    await adminMutation(csrf, url, method, body, async (path, init) => {
      calls.push({ path, init })
      return response(undefined, 204)
    })
    assert.equal(calls[0].init.credentials, 'include')
    assert.equal(calls[0].init.headers['X-CSRF-TOKEN'], 'test-csrf')
    assert.equal(calls[0].init.body, body === undefined ? undefined : JSON.stringify(body))
  }
})

test('mutation is blocked locally when CSRF was not loaded', async () => {
  await assert.rejects(
    Promise.resolve().then(() => adminMutation(null, '/api/v1/admin/users/id/suspend', 'POST')),
    /CSRF token unavailable/,
  )
})

test('successful void controller response may be HTTP 200 with an empty body', async () => {
  const result = await adminJson('/api/v1/admin/users/id/suspend', {}, async () =>
    response(undefined, 200))
  assert.equal(result, undefined)
})
