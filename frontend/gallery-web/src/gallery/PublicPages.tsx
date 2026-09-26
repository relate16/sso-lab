import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { publicApi } from '../api'
import type { Artwork, Page } from '../types'
import { ArtworkCard } from './ArtworkCard'
import { InquiryDrawer } from './InquiryDrawer'
import { HomeExhibition } from './HomeExhibition'

function useHomeArtworks() {
  const [state, setState] = useState<{ loading: boolean; error: boolean; data: Artwork[] }>({ loading: true, error: false, data: [] })
  useEffect(() => { let active = true; publicApi.home().then((data) => active && setState({ loading: false, error: false, data })).catch(() => active && setState({ loading: false, error: true, data: [] })); return () => { active = false } }, [])
  return state
}

export function HomePage() {
  const { loading, error, data } = useHomeArtworks()
  return <>
    <section className="home-intro"><div><p className="eyebrow">Online exhibition · Seoul</p><h1>Quiet Winter<br />Gallery</h1></div><p>겨울의 고요와 빛을 기록한 작품을, 벽과 여백이 있는 하나의 전시 공간으로 소개합니다.</p></section>
    <section className="home-exhibition" aria-labelledby="recent-works"><header><div><p className="eyebrow">Current wall</p><h2 id="recent-works">최근 공개 작품</h2></div><Link to="/works">모든 작품 보기 <span aria-hidden="true">→</span></Link></header>
      {loading && <p className="public-status">전시장을 준비하고 있습니다…</p>}
      {error && <p className="public-status" role="alert">작품을 불러오지 못했습니다.</p>}
      {!loading && !error && data.length === 0 && <p className="public-status">현재 공개 중인 작품이 없습니다.</p>}
      {!loading && !error && <HomeExhibition artworks={data} />}
    </section>
    <section className="home-note"><p className="eyebrow">Viewing note</p><p>작품을 선택하면 서로 다른 세 개의 전시 장면에서 크기와 액자, 벽면 설명을 함께 살펴볼 수 있습니다.</p><Link to="/works">전시 작품 탐색</Link></section>
  </>
}

export function WorksPage() {
  const [params, setParams] = useSearchParams()
  const [result, setResult] = useState<Page<Artwork> | null>(null)
  const [error, setError] = useState(false)
  const [inquiry, setInquiry] = useState<Artwork | null>(null)
  const navigate = useNavigate()
  const queryKey = params.toString()
  useEffect(() => { let active = true; setError(false); publicApi.artworks(new URLSearchParams(queryKey)).then((data) => active && setResult(data)).catch(() => active && setError(true)); return () => { active = false } }, [queryKey])
  function submit(event: React.FormEvent<HTMLFormElement>) { event.preventDefault(); const form = new FormData(event.currentTarget); const next = new URLSearchParams(); for (const [key, value] of form) if (String(value).trim()) next.set(key, String(value).trim()); setParams(next) }
  function clear() { setParams({}) }
  return <section className="works-page"><header className="public-page-header"><p className="eyebrow">The collection</p><h1>Works</h1><p>제목과 설명으로 작품을 찾고, 크기와 판매 상태에 따라 천천히 살펴보세요.</p></header>
    <form className="works-filter" onSubmit={submit} key={queryKey}>
      <label className="search-field">작품 검색<input name="q" defaultValue={params.get('q') ?? ''} placeholder="작품명 또는 설명" /></label>
      <label>판매 상태<select name="saleStatus" defaultValue={params.get('saleStatus') ?? ''}><option value="">전체</option><option value="AVAILABLE">구매 가능</option><option value="RESERVED">예약 중</option><option value="SOLD">판매 완료</option><option value="NOT_FOR_SALE">소장 작품</option></select></label>
      <label>작품 크기<select name="size" defaultValue={params.get('size') ?? ''}><option value="">전체</option><option value="SMALL">소형 · 50cm 이하</option><option value="MEDIUM">중형 · 50–100cm</option><option value="LARGE">대형 · 100cm 초과</option></select></label>
      <label>최소 가격<input name="minPrice" inputMode="numeric" defaultValue={params.get('minPrice') ?? ''} /></label>
      <label>최대 가격<input name="maxPrice" inputMode="numeric" defaultValue={params.get('maxPrice') ?? ''} /></label>
      <label>정렬<select name="sort" defaultValue={params.get('sort') ?? 'recent'}><option value="recent">최신 공개순</option><option value="price-asc">낮은 가격순</option><option value="price-desc">높은 가격순</option></select></label>
      <div className="filter-actions"><button type="submit">적용</button><button type="button" className="text-button" onClick={clear}>초기화</button></div>
    </form>
    <div className="works-summary"><span>{result ? `${result.totalElements} works` : 'Loading'}</span></div>
    {error && <div className="public-status" role="alert"><p>작품을 불러오지 못했습니다.</p><button type="button" onClick={() => window.location.reload()}>다시 시도</button></div>}
    {!error && result?.content.length === 0 && <p className="public-status">조건에 맞는 작품이 없습니다.</p>}
    <div className="works-grid">{result?.content.map((artwork) => <ArtworkCard key={artwork.id} artwork={artwork} onInquiry={setInquiry} onView={(selected) => navigate(`/artworks/${selected.id}?scene=1`)} />)}</div>
    {result && result.totalPages > 1 && <nav className="pagination" aria-label="작품 목록 페이지">{Array.from({ length: result.totalPages }, (_, page) => <button key={page} type="button" aria-current={result.page === page ? 'page' : undefined} onClick={() => { const next = new URLSearchParams(params); next.set('page', String(page)); setParams(next) }}>{page + 1}</button>)}</nav>}
    <InquiryDrawer artwork={inquiry} onClose={() => setInquiry(null)} />
  </section>
}

export function AboutPage() {
  return <section className="about-page"><header className="public-page-header"><p className="eyebrow">About the gallery</p><h1>Quiet moments,<br />held in paint.</h1></header><div className="about-story"><p className="about-lead">Quiet Winter Gallery는 계절의 적막, 남겨진 빛, 오래 바라본 풍경을 그리는 한 작가의 온라인 전시 공간입니다.</p><div><h2>작품과 공간 사이</h2><p>화면 안에서도 작품이 놓이는 높이와 주변의 여백, 실제 크기의 관계를 느낄 수 있도록 구성했습니다. 작품 정보는 필요한 만큼만 벽면에 두고, 더 긴 이야기는 별도의 설명으로 이어집니다.</p></div><div><h2>작품과 연락</h2><p>각 작품의 재료와 크기, 판매 상태는 작품 페이지에서 확인할 수 있습니다. 소장, 전시, 작업에 관한 연락은 작품별 문의를 통해 남겨주세요.</p></div><Link className="about-link" to="/works">작품 보러 가기 <span aria-hidden="true">→</span></Link></div></section>
}
