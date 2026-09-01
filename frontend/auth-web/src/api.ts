export type Fetcher = typeof fetch

export type Csrf = {
  headerName: string
  token: string
}

export async function readJson<T>(path: string, fetcher: Fetcher = fetch): Promise<T> {
  const response = await fetcher(path, {
    credentials: 'include',
    cache: 'no-store',
  })
  if (!response.ok) throw new Error('요청을 처리하지 못했습니다.')
  return response.json() as Promise<T>
}

export async function csrfMutation<T>(
  path: string,
  method: string,
  body?: unknown,
  fetcher: Fetcher = fetch,
): Promise<T> {
  const csrfResponse = await fetcher('/api/v1/csrf', { credentials: 'include' })
  if (!csrfResponse.ok) throw new Error('CSRF 토큰을 가져오지 못했습니다.')
  const csrf = await csrfResponse.json() as Csrf
  const response = await fetcher(path, {
    method,
    credentials: 'include',
    headers: {
      [csrf.headerName]: csrf.token,
      ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  if (!response.ok) throw new Error('요청을 처리하지 못했습니다.')
  return response.status === 204 ? undefined as T : response.json() as Promise<T>
}
