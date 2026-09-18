import type { Csrf } from '../types'

export type Fetcher = typeof fetch

export class AdminApiError extends Error {
  readonly status: number
  readonly code?: string

  constructor(status: number, code?: string) {
    super(userMessage(status, code))
    this.status = status
    this.code = code
  }
}

function userMessage(status: number, code?: string) {
  if (status === 401) return '로그인이 만료되었습니다. 다시 로그인해주세요.'
  if (status === 403 && code === 'reauthentication_required') return '보안 인증이 필요합니다.'
  if (status === 403) return '이 작업을 수행할 권한이 없습니다.'
  if (status === 404) return '요청한 항목을 찾을 수 없습니다.'
  if (status === 409) return '현재 상태에서는 요청을 완료할 수 없습니다.'
  if (status === 429) return '요청이 너무 많습니다. 잠시 후 다시 시도해주세요.'
  return '요청을 처리하지 못했습니다. 잠시 후 다시 시도해주세요.'
}

export function userFacingError(error: unknown, fallback = '요청을 처리하지 못했습니다. 잠시 후 다시 시도해주세요.') {
  return error instanceof AdminApiError ? error.message : fallback
}

export async function adminJson<T>(
  url: string,
  init: RequestInit = {},
  fetcher: Fetcher = fetch,
): Promise<T> {
  const response = await fetcher(url, { credentials: 'include', cache: 'no-store', ...init })
  if (!response.ok) {
    let code: string | undefined
    try { code = (await response.json() as { error?: string }).error } catch { code = undefined }
    throw new AdminApiError(response.status, code)
  }
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

export function queryString(values: Record<string, string | number | boolean | null | undefined>) {
  const params = new URLSearchParams()
  Object.entries(values).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') params.set(key, String(value))
  })
  const text = params.toString()
  return text ? `?${text}` : ''
}
