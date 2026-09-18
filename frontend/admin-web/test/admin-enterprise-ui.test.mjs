import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import { adminApi } from '../src/api/admin.ts'
import { buildGroupTree } from '../src/app/groupTree.ts'
import { AdminApiError, queryString } from '../src/api/client.ts'

const csrf = { headerName: 'X-CSRF-TOKEN', parameterName: '_csrf', token: 'csrf-value' }

function jsonResponse(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

test('user and audit filters are encoded for server-side pagination', () => {
  assert.equal(
    queryString({ q: 'alice kim', status: 'ACTIVE', page: 2, empty: '' }),
    '?q=alice+kim&status=ACTIVE&page=2',
  )
})

test('bulk operation uses one CSRF-protected backend request', async () => {
  const originalFetch = globalThis.fetch
  const calls = []
  globalThis.fetch = async (url, init) => {
    calls.push({ url, init })
    return jsonResponse({ requestedCount: 2, succeededCount: 2, failedCount: 0 })
  }
  try {
    const result = await adminApi.bulkStatus(csrf, ['user-a', 'user-b'], 'SUSPENDED')
    assert.equal(result.succeededCount, 2)
    assert.equal(calls.length, 1)
    assert.equal(calls[0].url, '/api/v1/admin/users/bulk/status')
    assert.equal(calls[0].init.method, 'POST')
    assert.equal(calls[0].init.headers['X-CSRF-TOKEN'], 'csrf-value')
    assert.deepEqual(JSON.parse(calls[0].init.body), {
      userIds: ['user-a', 'user-b'], status: 'SUSPENDED',
    })
  } finally {
    globalThis.fetch = originalFetch
  }
})

test('group hierarchy is built from actual parent relationships and sorted', () => {
  const group = (id, name, parentId = null) => ({
    id, name, parentId, fullPath: `/${name}`, memberCount: 0,
    createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z',
  })
  const tree = buildGroupTree([
    group('child', '개발', 'root'), group('second', '재무'), group('root', '경영'),
  ])
  assert.deepEqual(tree.map(item => item.id), ['root', 'second'])
  assert.equal(tree[0].children[0].id, 'child')
})

test('API errors expose user-safe messages instead of backend details', () => {
  assert.equal(new AdminApiError(403).message, '이 작업을 수행할 권한이 없습니다.')
  assert.equal(new AdminApiError(429).message, '요청이 너무 많습니다. 잠시 후 다시 시도해주세요.')
  assert.doesNotMatch(new AdminApiError(500, 'DataIntegrityViolationException').message, /Exception/)
})

test('enterprise UI routes, dialogs, drawers, empty states, and elevated reveal remain wired', async () => {
  const files = await Promise.all([
    readFile(new URL('../src/app/router.tsx', import.meta.url), 'utf8'),
    readFile(new URL('../src/components/layout/AdminLayout.tsx', import.meta.url), 'utf8'),
    readFile(new URL('../src/pages/UsersPage.tsx', import.meta.url), 'utf8'),
    readFile(new URL('../src/pages/GroupsPage.tsx', import.meta.url), 'utf8'),
    readFile(new URL('../src/pages/AuditLogsPage.tsx', import.meta.url), 'utf8'),
    readFile(new URL('../src/components/security/SensitiveEmail.tsx', import.meta.url), 'utf8'),
    readFile(new URL('../src/App.tsx', import.meta.url), 'utf8'),
  ])
  const source = files.join('\n')
  assert.match(source, /<Route index/)
  for (const path of ['users', 'groups', 'audit-logs']) {
    assert.match(source, new RegExp(`path=["']${path}["']`))
  }
  assert.match(source, /UserDrawer/)
  assert.match(source, /GroupDialog/)
  assert.match(source, /EmptyState/)
  assert.match(source, /requestElevated/)
  assert.match(source, /SSO Lab 관리자 포털/)
  assert.match(source, /관리자 전용 보안 포털/)
  assert.match(source, /admin-login-link/)
  assert.match(source, /마스킹 해제 필요/)
  assert.match(source, /security\.elevated/)
  assert.match(source, /adminApi\.revealEmail\(csrf, userId\)/)
  assert.match(source, /<SensitiveEmail/)
  assert.doesNotMatch(source, /window\.(prompt|confirm)/)
  assert.doesNotMatch(source, /\/internal\//)
})

test('production CSP gives Emotion a request nonce without unsafe-inline styles', async () => {
  const [entrypoint, html, nginx] = await Promise.all([
    readFile(new URL('../src/main.tsx', import.meta.url), 'utf8'),
    readFile(new URL('../index.html', import.meta.url), 'utf8'),
    readFile(new URL('../nginx.conf', import.meta.url), 'utf8'),
  ])
  assert.match(html, /meta name="csp-nonce" content="__CSP_NONCE__"/)
  assert.match(entrypoint, /createCache\(\{ key: 'sso-admin', nonce \}\)/)
  assert.match(entrypoint, /<CacheProvider value=\{emotionCache\}>/)
  assert.match(nginx, /style-src 'self' 'nonce-\$request_id'/)
  assert.match(nginx, /sub_filter '__CSP_NONCE__' '\$request_id'/)
  assert.doesNotMatch(nginx, /unsafe-inline/)
})
