import { useEffect, useState } from 'react'
import { studioApi } from '../api'
import type { AnalyticsOverview, AnalyticsRecentView, Page } from '../types'

const surfaceLabels: Record<string, string> = {
  HOME_CAROUSEL: '홈 캐러셀',
  HOME_EXHIBITION: '홈 전시 벽면',
  WORKS_GRID: '작품 목록',
  WORKS_LIGHTBOX: '작품 라이트박스',
  DETAIL_SCENE: '상세 Scene',
}
const sourceLabels: Record<string, string> = {
  DIRECT: '직접 접속',
  HOME_CAROUSEL: '홈 캐러셀',
  HOME_EXHIBITION: '홈 전시 벽면',
  WORKS_GRID: '작품 목록',
  WORKS_LIGHTBOX: '작품 라이트박스',
  INTERNAL: '내부 이동',
  EXTERNAL: '외부 사이트',
  UNKNOWN: '알 수 없음',
}

export function StudioAnalytics() {
  const [overview, setOverview] = useState<AnalyticsOverview | null>(null)
  const [error, setError] = useState('')
  useEffect(() => { void studioApi.analytics().then(setOverview).catch(() => setError('조회 통계를 불러오지 못했습니다.')) }, [])
  if (error) return <section className="studio-page"><p className="inline-notice" role="alert">{error}</p></section>
  if (!overview) return <section className="studio-page" aria-busy="true"><p>조회 통계를 불러오고 있습니다.</p></section>
  const metrics = [
    ['전체 누적', overview.lifetime],
    ['오늘', overview.today],
    ['최근 7일', overview.last7Days],
    ['최근 30일', overview.last30Days],
  ] as const
  return <section className="studio-page analytics-page">
    <header className="studio-page-header"><div><p className="eyebrow">Audience</p><h1>작품 조회 통계</h1><p>30분 중복 제거 후 인정된 조회이며 순 방문자는 브라우저 쿠키 기준입니다.</p></div></header>
    <div className="analytics-metrics">{metrics.map(([label, metric]) => <article key={label}><span>{label}</span><strong>{metric.views.toLocaleString('ko-KR')}</strong><small>순 방문자 {metric.visitors.toLocaleString('ko-KR')}</small></article>)}</div>
    <div className="analytics-columns">
      <section><h2>가장 많이 본 작품</h2><div className="analytics-table">{overview.topArtworks.map((item, index) => <div key={item.id}><span>{index + 1}</span><strong>{item.title}{item.archived && <em>보관됨</em>}</strong><span>{item.views.toLocaleString('ko-KR')}회</span><small>{item.visitors.toLocaleString('ko-KR')}명</small></div>)}{!overview.topArtworks.length && <p>아직 기록된 조회가 없습니다.</p>}</div></section>
      <section><h2>화면별 조회</h2><div className="analytics-table">{overview.surfaces.map(item => <div key={item.key}><strong>{surfaceLabels[item.key] ?? item.key}</strong><span>{item.views.toLocaleString('ko-KR')}회</span><small>{item.visitors.toLocaleString('ko-KR')}명</small></div>)}</div></section>
      <section><h2>유입 경로</h2><div className="analytics-table">{overview.sources.map(item => <div key={item.key}><strong>{sourceLabels[item.key] ?? item.key}</strong><span>{item.views.toLocaleString('ko-KR')}회</span><small>{item.visitors.toLocaleString('ko-KR')}명</small></div>)}</div></section>
      <section><h2>유입 도메인</h2><div className="analytics-table">{overview.referrers.map(item => <div key={item.key}><strong>{item.key}</strong><span>{item.views.toLocaleString('ko-KR')}회</span><small>{item.visitors.toLocaleString('ko-KR')}명</small></div>)}</div></section>
    </div>
    <RecentViews />
    <section className="analytics-daily"><h2>최근 30일 일별 조회</h2><div className="analytics-table">{overview.daily.map(item => <div key={item.date}><strong>{new Date(`${item.date}T00:00:00+09:00`).toLocaleDateString('ko-KR')}</strong><span>{item.views.toLocaleString('ko-KR')}회</span><small>{item.visitors.toLocaleString('ko-KR')}명</small></div>)}{!overview.daily.length && <p>아직 기록된 조회가 없습니다.</p>}</div></section>
  </section>
}

function RecentViews() {
  const [page, setPage] = useState(0)
  const [result, setResult] = useState<Page<AnalyticsRecentView> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [reload, setReload] = useState(0)
  useEffect(() => {
    let active = true
    setLoading(true)
    setError('')
    void studioApi.analyticsViews(page).then(data => {
      if (active) setResult(data)
    }).catch(() => {
      if (active) setError('최근 조회 기록을 불러오지 못했습니다.')
    }).finally(() => {
      if (active) setLoading(false)
    })
    return () => { active = false }
  }, [page, reload])

  return <section className="analytics-records" aria-busy={loading}>
    <h2>최근 조회 기록</h2>
    <p className="analytics-records-note">조회가 인정된 기록을 최신순으로 표시합니다. 시각은 한국 시간이며, IP 수집 전 기록이나 확인할 수 없는 IP는 ‘미수집’으로 표시됩니다.</p>
    {loading && <p role="status">조회 기록을 불러오고 있습니다.</p>}
    {error && <p className="inline-notice" role="alert">{error} <button type="button" onClick={() => setReload(value => value + 1)}>다시 시도</button></p>}
    {!loading && !error && result && <>
      <div className="analytics-records-scroll" role="region" aria-label="최근 작품 조회 기록" tabIndex={0}>
        <table className="analytics-records-table">
          <thead><tr><th scope="col">조회 시각</th><th scope="col">작품</th><th scope="col">조회 화면</th><th scope="col">접속 IP</th></tr></thead>
          <tbody>{result.content.map(item => <tr key={item.id}>
            <td><time dateTime={item.viewedAt}>{new Date(item.viewedAt).toLocaleString('ko-KR', { timeZone: 'Asia/Seoul', hour12: false })}</time></td>
            <td>{item.artworkTitle}{item.archived && <small> · 보관됨</small>}</td>
            <td>{surfaceLabels[item.surface] ?? item.surface}</td>
            <td className="analytics-records-ip">{item.visitorIp ?? '미수집'}</td>
          </tr>)}{!result.content.length && <tr><td colSpan={4}>아직 기록된 조회가 없습니다.</td></tr>}</tbody>
        </table>
      </div>
      <nav className="analytics-records-pagination" aria-label="최근 조회 기록 페이지">
        <button type="button" disabled={page === 0} onClick={() => setPage(value => value - 1)}>이전</button>
        <span aria-live="polite">{page + 1} / {Math.max(1, result.totalPages)} · 총 {result.totalElements.toLocaleString('ko-KR')}건</span>
        <button type="button" disabled={page + 1 >= result.totalPages} onClick={() => setPage(value => value + 1)}>다음</button>
      </nav>
    </>}
  </section>
}
