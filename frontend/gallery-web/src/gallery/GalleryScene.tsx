import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { publicApi } from '../api'
import type { Artwork, Page } from '../types'
import { ArtworkFrame } from './ArtworkFrame'
import { InquiryDrawer } from './InquiryDrawer'
import { ArtworkStoryDrawer } from './ArtworkStoryDrawer'
import { isValidScene, parseScene } from './sceneState'
import { SCENE_DEFINITIONS, SCENE_REFERENCE_SIZE } from './sceneDefinitions'

const MAX_ZOOM_LEVEL = 10
const WHEEL_ZOOM_THRESHOLD = 90

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
  const [zoomLevel, setZoomLevel] = useState(1)
  const [pan, setPan] = useState({ x: 0, y: 0 })
  const [sceneCropped, setSceneCropped] = useState(false)
  const [dragging, setDragging] = useState(false)
  const [railQuery, setRailQuery] = useState('')
  const [railBounds, setRailBounds] = useState({ canScrollLeft: false, canScrollRight: false })
  const rail = useRef<HTMLDivElement>(null)
  const activeRailItem = useRef<HTMLButtonElement>(null)
  const pendingRailScrollLeft = useRef<number | null>(null)
  const sceneSpace = useRef<HTMLDivElement>(null)
  const sceneCamera = useRef<HTMLDivElement>(null)
  const drag = useRef<{ pointerId: number; startX: number; startY: number; originX: number; originY: number } | null>(null)
  const wheelZoom = useRef({ accumulatedDelta: 0, direction: 0, lastAt: 0, streak: 0 })
  const zoomLevelRef = useRef(zoomLevel)
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
  useEffect(() => { setZoomLevel(1); setPan({ x: 0, y: 0 }); setDragging(false); drag.current = null }, [id, scene])
  useEffect(() => { zoomLevelRef.current = zoomLevel }, [zoomLevel])
  useEffect(() => { if (zoomLevel === 1) setPan({ x: 0, y: 0 }) }, [zoomLevel])
  const getPanLimits = useCallback((scale: number) => {
    const space = sceneSpace.current
    const camera = sceneCamera.current
    if (!space || !camera) return { x: 0, y: 0 }
    return {
      x: Math.max(0, (camera.offsetWidth * scale - space.clientWidth) * .5),
      y: Math.max(0, (camera.offsetHeight * scale - space.clientHeight) * .5),
    }
  }, [])
  useEffect(() => {
    const keydown = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && (zoomLevel > 1 || pan.x !== 0 || pan.y !== 0)) { setZoomLevel(1); setPan({ x: 0, y: 0 }) }
    }
    document.addEventListener('keydown', keydown)
    return () => document.removeEventListener('keydown', keydown)
  }, [pan.x, pan.y, zoomLevel])
  useEffect(() => {
    const space = sceneSpace.current
    const camera = sceneCamera.current
    if (!space || !camera) return
    const updatePanBounds = () => {
      const baseLimits = getPanLimits(1)
      const limits = getPanLimits(zoomLevel)
      setSceneCropped(baseLimits.x > .5 || baseLimits.y > .5)
      setPan((current) => ({
        x: Math.max(-limits.x, Math.min(limits.x, current.x)),
        y: Math.max(-limits.y, Math.min(limits.y, current.y)),
      }))
    }
    updatePanBounds()
    const observer = new ResizeObserver(updatePanBounds)
    observer.observe(space)
    observer.observe(camera)
    return () => observer.disconnect()
  }, [artwork, getPanLimits, scene, zoomLevel])
  useEffect(() => {
    const space = sceneSpace.current
    if (!space) return
    const zoomWithWheel = (event: WheelEvent) => {
      if (event.deltaY === 0) return
      event.preventDefault()
      const direction = event.deltaY < 0 ? 1 : -1
      const now = performance.now()
      const gesture = wheelZoom.current
      if (gesture.direction !== direction || now - gesture.lastAt > 220) {
        gesture.accumulatedDelta = 0
        gesture.streak = 0
      }
      gesture.direction = direction
      gesture.lastAt = now
      gesture.streak = Math.min(6, gesture.streak + 1)
      const deltaUnit = event.deltaMode === WheelEvent.DOM_DELTA_LINE ? 16 : event.deltaMode === WheelEvent.DOM_DELTA_PAGE ? space.clientHeight : 1
      const normalizedDelta = Math.min(180, Math.abs(event.deltaY) * deltaUnit)
      const acceleration = 1 + Math.max(0, gesture.streak - 1) * .18
      gesture.accumulatedDelta += normalizedDelta * acceleration
      const steps = Math.min(3, Math.floor(gesture.accumulatedDelta / WHEEL_ZOOM_THRESHOLD))
      if (steps === 0) return
      gesture.accumulatedDelta -= steps * WHEEL_ZOOM_THRESHOLD
      const currentZoom = zoomLevelRef.current
      const nextZoom = Math.max(1, Math.min(MAX_ZOOM_LEVEL, currentZoom + direction * steps))
      if (nextZoom === currentZoom) { gesture.accumulatedDelta = 0; return }
      zoomLevelRef.current = nextZoom
      setZoomLevel(nextZoom)
    }
    space.addEventListener('wheel', zoomWithWheel, { passive: false })
    return () => space.removeEventListener('wheel', zoomWithWheel)
  }, [artwork])
  useEffect(() => {
    const mobile = window.matchMedia('(max-width: 700px)').matches
    for (const candidate of Object.values(SCENE_DEFINITIONS)) {
      if (candidate.id === scene) continue
      const image = new Image()
      image.src = mobile ? candidate.mobileAsset : candidate.asset
    }
  }, [scene])
  const updateRailBounds = useCallback(() => {
    const element = rail.current
    if (!element) return
    setRailBounds({
      canScrollLeft: element.scrollLeft > 1,
      canScrollRight: element.scrollLeft + element.clientWidth < element.scrollWidth - 1,
    })
  }, [])
  useLayoutEffect(() => {
    const frame = requestAnimationFrame(() => {
      const element = rail.current
      if (!element) return
      if (pendingRailScrollLeft.current !== null) {
        element.scrollLeft = pendingRailScrollLeft.current
        pendingRailScrollLeft.current = null
      } else {
        activeRailItem.current?.scrollIntoView({ block: 'nearest', inline: 'nearest' })
      }
      updateRailBounds()
    })
    return () => cancelAnimationFrame(frame)
  }, [artwork?.id, updateRailBounds, visibleCollection.length])
  useEffect(() => {
    const element = rail.current
    if (!element) return
    const observer = new ResizeObserver(updateRailBounds)
    observer.observe(element)
    return () => observer.disconnect()
  }, [updateRailBounds])
  const selectArtwork = useCallback((selectedId: string) => {
    pendingRailScrollLeft.current = rail.current?.scrollLeft ?? null
    navigate(`/artworks/${selectedId}?scene=${scene}`)
  }, [navigate, scene])
  const scrollRail = (left: number) => rail.current?.scrollBy({
    left,
    behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth',
  })
  const startPan = (event: React.PointerEvent<HTMLDivElement>) => {
    if ((event.target as HTMLElement).closest('button, a, input, textarea, select')) return
    event.preventDefault()
    const limits = getPanLimits(zoomLevel)
    if (limits.x <= .5 && limits.y <= .5) return
    drag.current = { pointerId: event.pointerId, startX: event.clientX, startY: event.clientY, originX: pan.x, originY: pan.y }
    event.currentTarget.setPointerCapture(event.pointerId)
    setDragging(true)
  }
  const movePan = (event: React.PointerEvent<HTMLDivElement>) => {
    const active = drag.current
    if (!active || active.pointerId !== event.pointerId) return
    event.preventDefault()
    const limits = getPanLimits(zoomLevel)
    const nextX = active.originX + event.clientX - active.startX
    const nextY = active.originY + event.clientY - active.startY
    setPan({
      x: Math.max(-limits.x, Math.min(limits.x, nextX)),
      y: Math.max(-limits.y, Math.min(limits.y, nextY)),
    })
  }
  const stopPan = (event: React.PointerEvent<HTMLDivElement>) => {
    if (drag.current?.pointerId !== event.pointerId) return
    if (event.currentTarget.hasPointerCapture(event.pointerId)) event.currentTarget.releasePointerCapture(event.pointerId)
    drag.current = null
    setDragging(false)
  }
  if (error) return <section className="scene-status"><p>작품을 찾을 수 없습니다.</p><Link to="/works">Works로 돌아가기</Link></section>
  if (!artwork) return <section className="scene-status"><p>전시 공간을 준비하고 있습니다…</p></section>
  const mountWidthPx = SCENE_REFERENCE_SIZE.width
  const mountHeightPx = SCENE_REFERENCE_SIZE.height
  const physicalPlaneCm = {
    width: definition.wallWidthCm,
    height: definition.wallWidthCm * mountHeightPx / mountWidthPx,
  }
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
    '--scene-zoom-scale': zoomLevel,
    '--scene-pan-x': `${pan.x}px`,
    '--scene-pan-y': `${pan.y}px`,
  } as React.CSSProperties
  return <section className={`scene-viewer ${definition.className}`} aria-label={`${definition.name}, ${definition.spatialDescription}`}>
    <div
      className={`scene-space${zoomLevel > 1 ? ' is-zoomed' : ''}${zoomLevel > 1 || sceneCropped ? ' is-pannable' : ''}${dragging ? ' is-dragging' : ''}`}
      data-zoom-level={zoomLevel}
      id="gallery-scene-space"
      onPointerCancel={stopPan}
      onPointerDown={startPan}
      onPointerMove={movePan}
      onPointerUp={stopPan}
      ref={sceneSpace}
      style={sceneStyle}
    >
      <div className="scene-camera" ref={sceneCamera}>
        <picture className="scene-background" aria-hidden="true">
          <source media="(max-width: 700px)" srcSet={definition.mobileAsset} />
          <img src={definition.asset} alt="" width="1672" height="941" fetchPriority="high" />
        </picture>
        <div className="detail-mount">
          <ArtworkFrame
            artwork={artwork}
            mountWidthPx={mountWidthPx}
            mountHeightPx={mountHeightPx}
            physicalPlaneCm={physicalPlaneCm}
            priority
            zoomLevel={zoomLevel}
          >
            <button className="scene-caption" type="button" onClick={() => setStoryOpen(true)}><strong>{artwork.title}</strong><span>{artwork.year ?? '연도 미상'} · {artwork.material ?? '재료 미상'}</span><span>{artwork.widthCm} × {artwork.heightCm} cm</span><em>작품 설명 보기</em></button>
          </ArtworkFrame>
        </div>
      </div>
      <div className="scene-zoom-control" role="group" aria-label="감상 거리 조절">
        <button type="button" aria-controls="gallery-scene-space" aria-label="확대" disabled={zoomLevel >= MAX_ZOOM_LEVEL} onClick={() => setZoomLevel((level) => Math.min(MAX_ZOOM_LEVEL, level + 1))}><span aria-hidden="true">+</span></button>
        <div className="scene-zoom-scale">
          <input
            className="scene-zoom-slider"
            type="range"
            min="1"
            max={MAX_ZOOM_LEVEL}
            step="1"
            value={zoomLevel}
            aria-controls="gallery-scene-space"
            aria-label="감상 배율"
            onChange={(event) => setZoomLevel(Number(event.target.value))}
          />
        </div>
        <button type="button" aria-controls="gallery-scene-space" aria-label="축소" disabled={zoomLevel <= 1} onClick={() => setZoomLevel((level) => Math.max(1, level - 1))}><span aria-hidden="true">−</span></button>
      </div>
      <p className="scene-zoom-status" aria-live="polite">{zoomLevel > 1 ? `${zoomLevel}배 확대 상태입니다. 화면을 드래그해 이동할 수 있으며 ESC 키로 원래 보기로 돌아갑니다.` : sceneCropped ? '화면 비율에 따라 전시 장면 일부가 잘려 있습니다. 화면을 드래그해 둘러볼 수 있습니다.' : ''}</p>
    </div>
    <div className="scene-rail"><div className="scene-rail-tools"><p className="eyebrow">More works</p><label className="rail-search"><span>작품 검색</span><input value={railQuery} onChange={(event) => setRailQuery(event.target.value)} placeholder="작품명 검색" /></label><Link to="/works">전체 작품</Link></div><div className="rail-browser"><button type="button" disabled={!railBounds.canScrollLeft} onClick={() => scrollRail(-280)} aria-label="이전 작품 보기">‹</button><div ref={rail} className="scene-thumbnails" onScroll={updateRailBounds}>{visibleCollection.map((item) => { const image = item.images.find((entry) => entry.primary) ?? item.images[0]; const active = item.id === artwork.id; return <button ref={active ? activeRailItem : null} className={active ? 'active' : ''} aria-current={active ? 'true' : undefined} type="button" key={item.id} onClick={() => selectArtwork(item.id)} aria-label={`${item.title} 선택`}>{image ? <img src={image.thumbnailUrl} alt="" loading="lazy" width={image.widthPx} height={image.heightPx} /> : <span />}</button> })}</div><button type="button" disabled={!railBounds.canScrollRight} onClick={() => scrollRail(280)} aria-label="다음 작품 보기">›</button></div></div>
    <ArtworkStoryDrawer artwork={artwork} open={storyOpen} onClose={() => setStoryOpen(false)} onInquiry={() => { setStoryOpen(false); setInquiryOpen(true) }} />
    <InquiryDrawer artwork={inquiryOpen ? artwork : null} onClose={() => setInquiryOpen(false)} />
  </section>
}
