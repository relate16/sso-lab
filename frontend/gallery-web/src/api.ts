import type { Artwork, Csrf, Inquiry, InquiryCreate, InquiryStatus, Page, Session } from './types'

const jsonHeaders = { 'Content-Type': 'application/json' }
async function responseJson<T>(response: Response): Promise<T> {
  if (!response.ok) throw new Error(response.status === 403 ? '권한이 없습니다.' : '요청을 처리하지 못했습니다.')
  return response.status === 204 ? undefined as T : response.json() as Promise<T>
}
export async function loadStudioState(): Promise<{ session: Session; csrf: Csrf }> {
  const [session, csrf] = await Promise.all([
    fetch('/api/v1/session', { credentials: 'include', cache: 'no-store' }),
    fetch('/api/v1/csrf', { credentials: 'include', cache: 'no-store' }),
  ])
  return { session: await responseJson(session), csrf: await responseJson(csrf) }
}
async function mutation<T>(csrf: Csrf, path: string, method: string, body?: unknown): Promise<T> {
  return responseJson(await fetch(path, { method, credentials: 'include', headers: { ...jsonHeaders, [csrf.headerName]: csrf.token }, body: body === undefined ? undefined : JSON.stringify(body) }))
}
export const studioApi = {
  artworks: async () => responseJson<Artwork[]>(await fetch('/api/v1/gallery/studio/artworks', { credentials: 'include', cache: 'no-store' })),
  artwork: async (id: string) => responseJson<Artwork>(await fetch(`/api/v1/gallery/studio/artworks/${id}`, { credentials: 'include', cache: 'no-store' })),
  create: (csrf: Csrf, body: unknown) => mutation<Artwork>(csrf, '/api/v1/gallery/studio/artworks', 'POST', body),
  update: (csrf: Csrf, id: string, body: unknown) => mutation<Artwork>(csrf, `/api/v1/gallery/studio/artworks/${id}`, 'PATCH', body),
  remove: (csrf: Csrf, id: string) => mutation<void>(csrf, `/api/v1/gallery/studio/artworks/${id}`, 'DELETE'),
  reorder: (csrf: Csrf, artworks: Artwork[]) => mutation<void>(csrf, '/api/v1/gallery/studio/artworks/order', 'PATCH', { items: artworks.map((artwork, displayOrder) => ({ id: artwork.id, displayOrder })) }),
  upload: async (csrf: Csrf, id: string, file: File) => {
    const body = new FormData(); body.append('file', file)
    return responseJson(await fetch(`/api/v1/gallery/studio/artworks/${id}/images`, { method: 'POST', credentials: 'include', headers: { [csrf.headerName]: csrf.token }, body }))
  },
  primary: (csrf: Csrf, id: string, imageId: string) => mutation<void>(csrf, `/api/v1/gallery/studio/artworks/${id}/images/primary`, 'PATCH', { imageId }),
  removeImage: (csrf: Csrf, id: string, imageId: string) => mutation<void>(csrf, `/api/v1/gallery/studio/artworks/${id}/images/${imageId}`, 'DELETE'),
  inquiries: async () => responseJson<{ content: Inquiry[] }>(await fetch('/api/v1/gallery/studio/inquiries', { credentials: 'include', cache: 'no-store' })),
  inquiryStatus: (csrf: Csrf, id: string, status: InquiryStatus) => mutation<void>(csrf, `/api/v1/gallery/studio/inquiries/${id}`, 'PATCH', { status }),
}

export const publicApi = {
  home: async () => responseJson<Artwork[]>(await fetch('/api/v1/gallery/home', { cache: 'no-store' })),
  artworks: async (params: URLSearchParams) => responseJson<Page<Artwork>>(await fetch(`/api/v1/gallery/artworks?${params}`, { cache: 'no-store' })),
  artwork: async (id: string) => responseJson<Artwork>(await fetch(`/api/v1/gallery/artworks/${id}`, { cache: 'no-store' })),
  inquire: async (body: InquiryCreate) => {
    const csrf = await responseJson<Csrf>(await fetch('/api/v1/csrf', { credentials: 'include', cache: 'no-store' }))
    return mutation<{ id: string }>(csrf, '/api/v1/gallery/inquiries', 'POST', body)
  },
}
