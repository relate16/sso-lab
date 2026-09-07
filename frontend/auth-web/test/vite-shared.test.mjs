import assert from 'node:assert/strict'
import test from 'node:test'
import { createFrontendViteConfig } from '../../vite.shared.mts'

const env = {
  SSO_LOCAL_AUTH_SERVER_URL: 'http://127.0.0.1:18080',
  SSO_LOCAL_ADMIN_SERVER_URL: 'http://127.0.0.1:18081',
  SSO_LOCAL_HR_SERVER_URL: 'http://127.0.0.1:18082',
  SSO_LOCAL_APPROVAL_SERVER_URL: 'http://127.0.0.1:18083',
  SSO_LOCAL_AUTH_WEB_PORT: '5173',
  SSO_LOCAL_ADMIN_WEB_PORT: '5174',
  SSO_LOCAL_HR_WEB_PORT: '5175',
  SSO_LOCAL_APPROVAL_WEB_PORT: '5176',
}

const expected = {
  auth: { target: env.SSO_LOCAL_AUTH_SERVER_URL, port: 5173 },
  admin: { target: env.SSO_LOCAL_ADMIN_SERVER_URL, port: 5174 },
  hr: { target: env.SSO_LOCAL_HR_SERVER_URL, port: 5175 },
  approval: { target: env.SSO_LOCAL_APPROVAL_SERVER_URL, port: 5176 },
}

test('each dev server is loopback-only and proxies only its configured backend paths', () => {
  for (const [service, settings] of Object.entries(expected)) {
    const config = createFrontendViteConfig({
      service,
      command: 'serve',
      reactPlugin: { name: 'react-test' },
      loadEnv: () => env,
      repoRoot: '/repo',
    })
    assert.equal(config.server.host, '127.0.0.1')
    assert.equal(config.server.port, settings.port)
    assert.equal(config.server.strictPort, true)
    assert.ok(Object.keys(config.server.proxy).every(path => !path.includes('internal')))
    for (const proxy of Object.values(config.server.proxy)) {
      assert.equal(proxy.target, settings.target)
      assert.equal(proxy.changeOrigin, false)
      assert.equal(proxy.secure, true)
      assert.equal('ws' in proxy, false)
      assert.equal('rewriteWsOrigin' in proxy, false)
    }
  }
})

test('internal paths are rejected locally without reaching a proxy', () => {
  const config = createFrontendViteConfig({
    service: 'auth', command: 'serve', reactPlugin: { name: 'react-test' },
    loadEnv: () => env, repoRoot: '/repo',
  })
  const plugin = config.plugins.find(candidate => candidate.name === 'sso-lab-deny-internal-paths')
  let middleware
  plugin.configureServer({ middlewares: { use: handler => { middleware = handler } } })
  const response = {
    statusCode: 0,
    headers: {},
    setHeader(name, value) { this.headers[name] = value },
    end(value) { this.body = value },
  }
  let nextCalled = false
  middleware({ url: '/internal/admin/v1/users' }, response, () => { nextCalled = true })
  assert.equal(response.statusCode, 404)
  assert.equal(response.body, 'Not Found')
  assert.equal(nextCalled, false)
})

test('development fails clearly for missing or invalid targets and ports', () => {
  const config = overrides => createFrontendViteConfig({
    service: 'auth', command: 'serve', reactPlugin: { name: 'react-test' },
    loadEnv: () => ({ ...env, ...overrides }), repoRoot: '/repo',
  })
  assert.throws(() => config({ SSO_LOCAL_AUTH_SERVER_URL: '' }), /SSO_LOCAL_AUTH_SERVER_URL is required/)
  assert.throws(() => config({ SSO_LOCAL_AUTH_SERVER_URL: 'ftp:\/\/127.0.0.1' }), /must use http:\/\/ or https:\/\//)
  assert.throws(() => config({ SSO_LOCAL_AUTH_SERVER_URL: 'http:\/\/127.0.0.1\/api' }), /only an origin/)
  assert.throws(() => config({ SSO_LOCAL_AUTH_WEB_PORT: 'not-a-port' }), /between 1024 and 65535/)
})

test('production build does not load or embed local development settings', () => {
  let loaded = false
  const config = createFrontendViteConfig({
    service: 'auth', command: 'build', reactPlugin: { name: 'react-test' },
    loadEnv: () => { loaded = true; return env }, repoRoot: '/repo',
  })
  assert.equal(loaded, false)
  assert.deepEqual(Object.keys(config), ['plugins'])
})
