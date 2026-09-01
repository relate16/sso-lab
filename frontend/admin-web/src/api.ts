export type Fetcher = typeof fetch

export type Csrf = {
  headerName: string
  parameterName: string
  token: string
}

export async function adminJson<T>(
  url: string,
  init: RequestInit = {},
  fetcher: Fetcher = fetch,
): Promise<T> {
  const response = await fetcher(url, { credentials: 'include', cache: 'no-store', ...init })
  if (!response.ok) throw new Error(`${response.status} ${response.statusText}`)
  if (response.status === 204) return undefined as T
  const text = await response.text()
  return text.length === 0 ? undefined as T : JSON.parse(text) as T
}

export function adminMutation<T>(
  csrf: Csrf | null,
  url: string,
  method: string,
  body?: unknown,
  fetcher: Fetcher = fetch,
): Promise<T> {
  if (!csrf) throw new Error('CSRF token unavailable')
  return adminJson<T>(url, {
    method,
    headers: {
      [csrf.headerName]: csrf.token,
      ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  }, fetcher)
}
