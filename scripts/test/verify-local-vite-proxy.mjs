import assert from 'node:assert/strict'
import { spawn } from 'node:child_process'
import { createServer } from 'node:http'
import { networkInterfaces } from 'node:os'
import { resolve } from 'node:path'

const repoRoot = resolve(import.meta.dirname, '../..')
const services = [
  { name: 'auth', app: 'auth-web', webPort: 5173, backendPort: 18080 },
  { name: 'admin', app: 'admin-web', webPort: 5174, backendPort: 18081 },
  { name: 'hr', app: 'hr-web', webPort: 5175, backendPort: 18082 },
  { name: 'approval', app: 'approval-web', webPort: 5176, backendPort: 18083 },
]

const localEnv = Object.fromEntries(services.flatMap(service => [
  [`SSO_LOCAL_${service.name.toUpperCase()}_SERVER_URL`, `http://127.0.0.1:${service.backendPort}`],
  [`SSO_LOCAL_${service.name.toUpperCase()}_WEB_PORT`, String(service.webPort)],
]))

const backendServers = []
const viteProcesses = []
const internalHits = new Map(services.map(service => [service.name, 0]))

function startBackend(service) {
  const token = `proxy-test-${service.name}`
  const cookie = `PROXY_TEST_${service.name.toUpperCase()}=session`
  const browserOrigin = `http://127.0.0.1:${service.webPort}`
  const server = createServer((request, response) => {
    response.setHeader('X-SSO-Test-Backend', service.name)
    if (request.url === '/api/v1/csrf' && request.method === 'GET') {
      response.setHeader('Content-Type', 'application/json')
      response.setHeader('Set-Cookie', `${cookie}; Path=/; HttpOnly; SameSite=Lax`)
      response.end(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token }))
      return
    }
    if (request.url === '/api/v1/test-mutation' && request.method === 'POST') {
      const valid = request.headers.cookie?.includes(cookie)
        && request.headers['x-csrf-token'] === token
        && request.headers.origin === browserOrigin
        && request.headers.host === `127.0.0.1:${service.webPort}`
      response.statusCode = valid ? 204 : 403
      response.end()
      return
    }
    if (request.url === '/internal' || request.url?.startsWith('/internal/')) {
      internalHits.set(service.name, internalHits.get(service.name) + 1)
      response.statusCode = 418
      response.end()
      return
    }
    response.statusCode = 404
    response.end()
  })
  return new Promise((resolveStart, reject) => {
    server.once('error', reject)
    server.listen(service.backendPort, '127.0.0.1', () => {
      backendServers.push(server)
      resolveStart()
    })
  })
}

function startVite(service) {
  const viteCli = resolve(repoRoot, 'frontend', service.app, 'node_modules', 'vite', 'bin', 'vite.js')
  const child = spawn(process.execPath, [viteCli], {
    cwd: resolve(repoRoot, 'frontend', service.app),
    env: { ...process.env, ...localEnv },
    stdio: ['ignore', 'pipe', 'pipe'],
    windowsHide: true,
  })
  let output = ''
  child.stdout.on('data', chunk => { output += chunk.toString() })
  child.stderr.on('data', chunk => { output += chunk.toString() })
  child.output = () => output
  viteProcesses.push(child)
}

async function waitForVite(service) {
  const deadline = Date.now() + 20_000
  while (Date.now() < deadline) {
    try {
      const response = await fetch(`http://127.0.0.1:${service.webPort}/`, {
        signal: AbortSignal.timeout(500),
      })
      if (response.ok) return
    } catch {
      // Startup polling only.
    }
    await new Promise(resolveWait => setTimeout(resolveWait, 100))
  }
  const output = viteProcesses.map(child => child.output()).join('\n')
  throw new Error(`${service.app} did not start on 127.0.0.1:${service.webPort}\n${output}`)
}

async function verifyProxy(service) {
  const origin = `http://127.0.0.1:${service.webPort}`
  const csrfResponse = await fetch(`${origin}/api/v1/csrf`)
  assert.equal(csrfResponse.status, 200)
  assert.equal(csrfResponse.headers.get('x-sso-test-backend'), service.name)
  const csrf = await csrfResponse.json()
  const cookie = csrfResponse.headers.get('set-cookie')?.split(';', 1)[0]
  assert.ok(cookie)

  const accepted = await fetch(`${origin}/api/v1/test-mutation`, {
    method: 'POST',
    headers: { Cookie: cookie, Origin: origin, [csrf.headerName]: csrf.token },
  })
  assert.equal(accepted.status, 204)
  assert.equal(accepted.headers.get('x-sso-test-backend'), service.name)

  const rejected = await fetch(`${origin}/api/v1/test-mutation`, {
    method: 'POST',
    headers: { Cookie: cookie, Origin: origin },
  })
  assert.equal(rejected.status, 403)

  const internal = await fetch(`${origin}/internal/admin/v1/users`)
  assert.equal(internal.status, 404)
  assert.equal(internalHits.get(service.name), 0)
}

function externalIpv4Addresses() {
  return Object.values(networkInterfaces())
    .flatMap(entries => entries ?? [])
    .filter(entry => entry.family === 'IPv4' && !entry.internal)
    .map(entry => entry.address)
}

async function assertNotReachable(address, port) {
  try {
    await fetch(`http://${address}:${port}/`, { signal: AbortSignal.timeout(500) })
    assert.fail(`${address}:${port} unexpectedly accepted a connection`)
  } catch (error) {
    if (error?.code === 'ERR_ASSERTION') throw error
  }
}

async function cleanup() {
  for (const child of viteProcesses) {
    if (child.exitCode === null) child.kill()
  }
  await Promise.all(backendServers.map(server => new Promise(resolveClose => server.close(resolveClose))))
}

try {
  await Promise.all(services.map(startBackend))
  services.forEach(startVite)
  await Promise.all(services.map(waitForVite))
  await Promise.all(services.map(verifyProxy))

  for (const address of externalIpv4Addresses()) {
    for (const service of services) {
      await assertNotReachable(address, service.webPort)
      await assertNotReachable(address, service.backendPort)
    }
  }

  for (const service of services) {
    console.log(`${service.app}|127.0.0.1:${service.webPort}|${service.name}-server:${service.backendPort}|pass`)
  }
  console.log('csrf_with_token|accepted|pass')
  console.log('csrf_without_token|rejected|pass')
  console.log('internal_path|not_proxied|pass')
  console.log('external_interface|not_bound|pass')
} finally {
  await cleanup()
}
