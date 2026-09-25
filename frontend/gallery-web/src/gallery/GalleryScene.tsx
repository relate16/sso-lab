import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { publicApi } from '../api'
import type { Artwork, Page } from '../types'
import { ArtworkFrame } from './ArtworkFrame'
import { InquiryDrawer } from './InquiryDrawer'
import { ArtworkStoryDrawer } from './ArtworkStoryDrawer'
import { isValidScene, nextScene, parseScene } from './sceneState'

const sceneNames = { 1: 'Front wall', 2: 'Corner room', 3: 'Long gallery' }

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
  const selectArtwork = useCallback((selectedId: string) => navigate(`/artworks/${selectedId}?scene=${nextScene(scene)}`), [navigate, scene])
  if (error) return <section className="scene-status"><p>작품을 찾을 수 없습니다.</p><Link to="/works">Works로 돌아가기</Link></section>
  if (!artwork) return <section className="scene-status"><p>전시 공간을 준비하고 있습니다…</p></section>
  return <section className={`scene-viewer scene-${scene}`} aria-label={`${sceneNames[scene]} 전시 장면`}>
    <header className="scene-toolbar"><Link to="/works">← Works</Link><div><span>Scene {scene} / 3</span><strong>{sceneNames[scene]}</strong></div><button type="button" onClick={() => selectArtwork(artwork.id)}>다음 공간 <span aria-hidden="true">→</span></button></header>
    <div className="scene-space">
      <div className="scene-ceiling" aria-hidden="true" /><div className="scene-wall scene-wall-left" aria-hidden="true" /><div className="scene-wall scene-wall-right" aria-hidden="true" /><div className="scene-floor" aria-hidden="true" />
      <div className="scene-spotlight" aria-hidden="true" />
      <div className="scene-protagonist"><ArtworkFrame artwork={artwork} /><button className="scene-caption" type="button" onClick={() => setStoryOpen(true)}><strong>{artwork.title}</strong><span>{artwork.year ?? '연도 미상'} · {artwork.material ?? '재료 미상'}</span><span>{artwork.widthCm} × {artwork.heightCm} cm</span><em>작품 설명 보기</em></button></div>
      <p className="scene-number" aria-hidden="true">0{scene}</p>
    </div>
    <div className="scene-rail"><div><p className="eyebrow">Select a work</p><span>작품을 선택할 때마다 전시 공간이 바뀝니다.</span></div><div className="scene-thumbnails">{collection.map((item) => { const image = item.images.find((entry) => entry.primary) ?? item.images[0]; return <button className={item.id === artwork.id ? 'active' : ''} type="button" key={item.id} onClick={() => selectArtwork(item.id)} aria-label={`${item.title} 선택, 다음 Scene으로 이동`}>{image ? <img src={image.thumbnailUrl} alt="" loading="lazy" /> : <span />}</button> })}</div></div>
    <ArtworkStoryDrawer artwork={artwork} open={storyOpen} onClose={() => setStoryOpen(false)} onInquiry={() => { setStoryOpen(false); setInquiryOpen(true) }} />
    <InquiryDrawer artwork={inquiryOpen ? artwork : null} onClose={() => setInquiryOpen(false)} />
  </section>
}
