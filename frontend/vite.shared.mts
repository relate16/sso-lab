type FrontendService = 'auth' | 'admin' | 'hr' | 'approval'

type LoadEnv = (
  mode: string,
  envDir: string,
  prefixes: string | string[],
) => Record<string, string>

type SharedConfigOptions = {
  service: FrontendService
  command: 'build' | 'serve'
  reactPlugin: any
  loadEnv: LoadEnv
  repoRoot: string
}

const serviceSettings = {
  auth: {
    targetKey: 'SSO_LOCAL_AUTH_SERVER_URL',
    portKey: 'SSO_LOCAL_AUTH_WEB_PORT',
    proxyPaths: [
      '^/api/',
      '^/\\.well-known/',
      '^/oauth2/',
      '^/userinfo(?:/|\\?|$)',
      '^/connect/',
      '^/error(?:\\?|$)',
    ],
  },
  admin: {
    targetKey: 'SSO_LOCAL_ADMIN_SERVER_URL',
    portKey: 'SSO_LOCAL_ADMIN_WEB_PORT',
    proxyPaths: [
      '^/api/',
      '^/oauth2/',
      '^/login/',
      '^/logout(?:\\?|$)',
      '^/error(?:\\?|$)',
    ],
  },
  hr: {
    targetKey: 'SSO_LOCAL_HR_SERVER_URL',
    portKey: 'SSO_LOCAL_HR_WEB_PORT',
    proxyPaths: [
      '^/api/',
      '^/oauth2/',
      '^/login/',
      '^/logout(?:\\?|$)',
      '^/error(?:\\?|$)',
    ],
  },
  approval: {
    targetKey: 'SSO_LOCAL_APPROVAL_SERVER_URL',
    portKey: 'SSO_LOCAL_APPROVAL_WEB_PORT',
    proxyPaths: [
      '^/api/',
      '^/oauth2/',
      '^/login/',
      '^/logout(?:\\?|$)',
      '^/error(?:\\?|$)',
    ],
  },
} as const

export function repositoryRootFromConfigUrl(configUrl: string): string {
  const pathname = decodeURIComponent(new URL('../..', configUrl).pathname)
  return /^\/[A-Za-z]:\//.test(pathname) ? pathname.slice(1) : pathname
}

function requiredValue(env: Record<string, string>, key: string): string {
  const value = env[key]?.trim()
  if (!value) {
    throw new Error(
      `${key} is required for Vite development. Copy .env.frontend.local.example to .env.frontend.local.`,
    )
  }
  return value
}

function validatedTarget(value: string, key: string): string {
  let target: URL
  try {
    target = new URL(value)
  } catch {
    throw new Error(`${key} must be an absolute http:// or https:// URL.`)
  }
  if (target.protocol !== 'http:' && target.protocol !== 'https:') {
    throw new Error(`${key} must use http:// or https://.`)
  }
  if (
    target.username
    || target.password
    || target.pathname !== '/'
    || target.search
    || target.hash
  ) {
    throw new Error(`${key} must contain only an origin without credentials, path, query, or fragment.`)
  }
  return target.origin
}

function validatedPort(value: string, key: string): number {
  if (!/^\d+$/.test(value)) {
    throw new Error(`${key} must be an integer between 1024 and 65535.`)
  }
  const port = Number(value)
  if (!Number.isSafeInteger(port) || port < 1024 || port > 65535) {
    throw new Error(`${key} must be an integer between 1024 and 65535.`)
  }
  return port
}

function isLoopbackTarget(target: string): boolean {
  const hostname = new URL(target).hostname.toLowerCase().replace(/^\[|\]$/g, '')
  return hostname === 'localhost'
    || hostname.endsWith('.localhost')
    || hostname === '::1'
    || /^127(?:\.\d{1,3}){3}$/.test(hostname)
}

function denyInternalPathsPlugin() {
  return {
    name: 'sso-lab-deny-internal-paths',
    configureServer(server: any) {
      server.middlewares.use((request: any, response: any, next: () => void) => {
        const pathname = new URL(request.url ?? '/', 'http://127.0.0.1').pathname
        if (pathname === '/internal' || pathname.startsWith('/internal/')) {
          response.statusCode = 404
          response.setHeader('Cache-Control', 'no-store')
          response.end('Not Found')
          return
        }
        next()
      })
    },
  }
}

export function createFrontendViteConfig(options: SharedConfigOptions) {
  const plugins = [options.reactPlugin]
  if (options.command !== 'serve') return { plugins }

  // Vite mode "frontend" loads the repository-root .env.frontend.local file.
  // Only SSO_LOCAL_* values are read; production .env values are not exposed.
  const env = options.loadEnv('frontend', options.repoRoot, 'SSO_LOCAL_')
  const settings = serviceSettings[options.service]
  const target = validatedTarget(requiredValue(env, settings.targetKey), settings.targetKey)
  const port = validatedPort(requiredValue(env, settings.portKey), settings.portKey)
  const proxy = Object.fromEntries(settings.proxyPaths.map(path => [path, {
    target,
    // Keep direct loopback development unchanged. Remote virtual hosts need the
    // validated target origin as Host/SNI while TLS verification remains enabled.
    changeOrigin: !isLoopbackTarget(target),
    secure: true,
  }]))

  return {
    plugins: [...plugins, denyInternalPathsPlugin()],
    server: {
      host: '127.0.0.1',
      port,
      strictPort: true,
      proxy,
    },
  }
}
