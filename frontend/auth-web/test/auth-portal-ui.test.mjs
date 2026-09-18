import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

test('Auth login presents the shared SSO Lab brand', async () => {
  const source = await readFile(new URL('../src/App.tsx', import.meta.url), 'utf8')
  assert.match(source, /SSO Lab Auth/)
  assert.match(source, /Passwordless Identity Platform/)
  assert.match(source, /사용자 전용 인증 포털/)
  assert.match(source, /MarsLogo/)
})
