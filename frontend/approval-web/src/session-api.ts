export type Fetcher = typeof fetch
export type Session = {
  authenticated: boolean
  name?: string
  username?: string
  userId?: string
  email?: string
  roles?: string[]
  groups?: string[]
  amr?: string[]
  acr?: string
}
export type Csrf = { parameterName: string; token: string }

export async function loadBffState(fetcher: Fetcher = fetch): Promise<{
  session: Session | null
  csrf: Csrf | null
}> {
  const [sessionResponse, csrfResponse] = await Promise.all([
    fetcher('/api/v1/session', { credentials: 'include', cache: 'no-store' }),
    fetcher('/api/v1/csrf', { credentials: 'include', cache: 'no-store' }),
  ])
  return {
    session: sessionResponse.ok ? await sessionResponse.json() as Session : null,
    csrf: csrfResponse.ok ? await csrfResponse.json() as Csrf : null,
  }
}
