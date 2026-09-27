import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { publicApi } from '../api'
import type { Artwork, Page } from '../types'
import { ArtworkFrame } from './ArtworkFrame'
import { InquiryDrawer } from './InquiryDrawer'
import { ArtworkStoryDrawer } from './ArtworkStoryDrawer'
import { isValidScene, nextScene, parseScene } from './sceneState'
import { SCENE_DEFINITIONS, SCENE_REFERENCE_SIZE } from './sceneDefinitions'

export function GalleryScene() {
  const { id = '' } = useParams()
  const [search, setSearch] = useSearchParams()
  const navigate = useNavigate()
  const scene = parseScene(search.get('scene'))
  const [artwork, setArtwork] = useState<Artwork | null>(null)
  const [collection, setCollection] = useState<Artwork[]>([])
  const [error, setError] = useState(false)
  const [storyOpen, setStoryOpen] = useState(false)
  const [inquiryOpen, setInquiryOpen] = useState(false)
  const [zoomed, setZoomed] = useState(false)
  const [railQuery, setRailQuery] = useState('')
  const rail = useRef<HTMLDivElement>(null)
  const definition = SCENE_DEFINITIONS[scene]
  const visibleCollection = useMemo(() => collection.filter((item) => item.title.toLocaleLowerCase().includes(railQuery.trim().toLocaleLowerCase())), [collection, railQuery])
  useEffect(() => {
    if (!isValidScene(search.get('scene'))) setSearch({ scene: '1' }, { replace: true })
  }, [search, setSearch])
  useEffect(() => {
    let active = true; setError(false); setArtwork(null)
    Promise.all([publicApi.artwork(id), publicApi.artworks(new URLSearchParams({ pageSize: '60', sort: 'recent' }))])
      .then(([selected, page]: [Artwork, Page<Artwork>]) => { if (active) { setArtwork(selected); setCollection(page.content) } })
      .catch(() => active && setError(true))
    return () => { active = false }
  }, [id])
  useEffect(() => { if (artwork) document.title = `${artwork.title} · Quiet Winter Gallery`; return () => { document.title = 'Quiet Winter Gallery' } }, [artwork])
  useEffect(() => { setZoomed(false) }, [id, scene])
  useEffect(() => {
    const keydown = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && zoomed) setZoomed(false)
    }
    document.addEventListener('keydown', keydown)
    return () => document.removeEventListener('keydown', keydown)
  }, [zoomed])
  useEffect(() => {
    const mobile = window.matchMedia('(max-width: 700px)').matches
    for (const candidate of Object.values(SCENE_DEFINITIONS)) {
      if (candidate.id === scene) continue
      const image = new Image()
      image.src = mobile ? candidate.mobileAsset : candidate.asset
    }
  }, [scene])
  const selectArtwork = useCallback((selectedId: string) => navigate(`/artworks/${selectedId}?scene=${nextScene(scene)}`), [navigate, scene])
  const scrollRail = (left: number) => rail.current?.scrollBy({
    left,
    behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth',
  })
  if (error) return <section className="scene-status"><p>작품을 찾을 수 없습니다.</p><Link to="/works">Works로 돌아가기</Link></section>
  if (!artwork) return <section className="scene-status"><p>전시 공간을 준비하고 있습니다…</p></section>
  const sceneStyle = {
    '--detail-mount-center-x': `${definition.mount.centerX}%`,
    '--detail-mount-center-y': `${definition.mount.centerY}%`,
    '--detail-mount-width': `${definition.mount.width}%`,
    '--detail-mount-height': `${definition.mount.height}%`,
    '--scene-caption-left': `${definition.caption.left}%`,
    '--scene-caption-top': `${definition.caption.top}%`,
    '--scene-zoom-left': `${definition.zoomControl.left}%`,
    '--scene-zoom-top': `${definition.zoomControl.top}%`,
    '--scene-focal-x': `${definition.focalPoint.x}%`,
    '--scene-focal-y': `${definition.focalPoint.y}%`,
    '--scene-artwork-shadow': definition.artworkShadow,
  } as React.CSSProperties
  return <section className={`scene-viewer ${definition.className}`} aria-label={`${definition.name}, ${definition.spatialDescription}`}>
    <header className="scene-toolbar"><Link to="/works">← Works</Link><div><span>Scene {scene} / 3</span><strong>{definition.name}</strong></div><div className="scene-toolbar-actions"><button className="scene-next-control" type="button" onClick={() => selectArtwork(artwork.id)}>다음 공간 <span aria-hidden="true">→</span></button></div></header>
    <div className={`scene-space${zoomed ? ' is-zoomed' : ''}`} id="gallery-scene-space" style={sceneStyle}>
      <div className="scene-camera">
        <picture className="scene-background" aria-hidden="true">
          <source media="(max-width: 700px)" srcSet={definition.mobileAsset} />
          <img src={definition.asset} alt="" width="1672" height="941" fetchPriority="high" />
        </picture>
        <div className="detail-mount">
          <ArtworkFrame
            artwork={artwork}
            mountWidthPx={SCENE_REFERENCE_SIZE.width * definition.mount.width / 100}
            mountHeightPx={SCENE_REFERENCE_SIZE.height * definition.mount.height / 100}
            priority
          />
        </div>
        <div className="scene-zoom-control" role="group" aria-label="감상 거리 조절">
          <button type="button" aria-controls="gallery-scene-space" aria-label="가까이 보기" disabled={zoomed} onClick={() => setZoomed(true)}><span aria-hidden="true">+</span></button>
          <button type="button" aria-controls="gallery-scene-space" aria-label="원래 보기" disabled={!zoomed} onClick={() => setZoomed(false)}><span aria-hidden="true">−</span></button>
        </div>
        <button className="scene-caption" type="button" onClick={() => setStoryOpen(true)}><strong>{artwork.title}</strong><span>{artwork.year ?? '연도 미상'} · {artwork.material ?? '재료 미상'}</span><span>{artwork.widthCm} × {artwork.heightCm} cm</span><em>작품 설명 보기</em></button>
      </div>
      <p className="scene-zoom-status" aria-live="polite">{zoomed ? '가까이 보기 상태입니다. ESC 키로 원래 보기로 돌아갑니다.' : ''}</p>
    </div>
    <div className="scene-rail"><div className="scene-rail-tools"><p className="eyebrow">More works</p><label className="rail-search"><span>작품 검색</span><input value={railQuery} onChange={(event) => setRailQuery(event.target.value)} placeholder="작품명 검색" /></label><Link to="/works">전체 작품</Link></div><div className="rail-browser"><button type="button" onClick={() => scrollRail(-280)} aria-label="이전 작품 보기">‹</button><div ref={rail} className="scene-thumbnails">{visibleCollection.map((item) => { const image = item.images.find((entry) => entry.primary) ?? item.images[0]; return <button className={item.id === artwork.id ? 'active' : ''} type="button" key={item.id} onClick={() => selectArtwork(item.id)} aria-label={`${item.title} 선택, 다음 Scene으로 이동`}>{image ? <img src={image.thumbnailUrl} alt="" loading="lazy" width={image.widthPx} height={image.heightPx} /> : <span />}</button> })}</div><button type="button" onClick={() => scrollRail(280)} aria-label="다음 작품 보기">›</button></div></div>
    <ArtworkStoryDrawer artwork={artwork} open={storyOpen} onClose={() => setStoryOpen(false)} onInquiry={() => { setStoryOpen(false); setInquiryOpen(true) }} />
    <InquiryDrawer artwork={inquiryOpen ? artwork : null} onClose={() => setInquiryOpen(false)} />
  </section>
}
