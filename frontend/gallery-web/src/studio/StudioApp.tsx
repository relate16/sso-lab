import { useEffect, useState } from 'react'
import { Link, Navigate, Route, Routes } from 'react-router-dom'
import { loadStudioState } from '../api'
import type { Csrf, Session } from '../types'
import { StudioArtworks } from './StudioArtworks'
import { ArtworkEditor } from './ArtworkEditor'
import { StudioInquiries } from './StudioInquiries'

export function StudioApp() {
  const [state, setState] = useState<{ session: Session; csrf: Csrf } | null>(null)
  const [error, setError] = useState('')
  useEffect(() => { void loadStudioState().then(setState).catch(() => setError('Studio 연결 상태를 확인하지 못했습니다.')) }, [])
  if (error) return <section className="studio-gate"><h1>Studio</h1><p>{error}</p></section>
  if (!state) return <section className="studio-gate" aria-busy="true"><p>Studio를 준비하고 있습니다.</p></section>
  if (!state.session.authenticated) return <section className="studio-gate"><p className="eyebrow">Quiet Winter Gallery</p><h1>Artist Studio</h1><p>작품과 문의를 관리하려면 중앙 Auth로 본인을 확인해주세요.</p><a className="primary-action" href="/oauth2/authorization/gallery-client">SSO로 Studio 입장</a></section>
  if (!state.session.roles?.includes('ADMIN')) return <section className="studio-gate"><h1>접근 권한이 없습니다.</h1><Link to="/">Gallery로 돌아가기</Link></section>
  return <div className="studio-shell">
    <aside><Link className="studio-wordmark" to="/studio">Quiet Winter<br /><span>Artist Studio</span></Link><nav aria-label="Studio 메뉴"><Link to="/studio/artworks">작품</Link><Link to="/studio/inquiries">문의</Link><Link to="/">공개 Gallery</Link></nav><form method="post" action="/api/v1/logout"><input type="hidden" name={state.csrf.parameterName} value={state.csrf.token} /><button className="text-button" type="submit">로그아웃</button></form></aside>
    <main><Routes>
      <Route index element={<Navigate to="artworks" replace />} />
      <Route path="artworks" element={<StudioArtworks csrf={state.csrf} />} />
      <Route path="artworks/new" element={<ArtworkEditor csrf={state.csrf} />} />
      <Route path="artworks/:id" element={<ArtworkEditor csrf={state.csrf} />} />
      <Route path="inquiries" element={<StudioInquiries csrf={state.csrf} />} />
    </Routes></main>
  </div>
}
