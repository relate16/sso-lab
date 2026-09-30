import { useCallback, useEffect, useRef, useState, type CSSProperties } from 'react'
import { Link } from 'react-router-dom'
import type { Artwork } from '../types'
import { primaryImage } from './ArtworkCard'

const WORKS_PAGE_SIZE = 24

export function HomeArtworkRail({ artworks }: { artworks: Artwork[] }) {
  const viewport = useRef<HTMLDivElement>(null)
  const [bounds, setBounds] = useState({ canScrollLeft: false, canScrollRight: false })

  const updateBounds = useCallback(() => {
    const element = viewport.current
    if (!element) return
    setBounds({
      canScrollLeft: element.scrollLeft > 1,
      canScrollRight: element.scrollLeft + element.clientWidth < element.scrollWidth - 1,
    })
  }, [])

  useEffect(() => {
    const element = viewport.current
    if (!element) return
    const frame = requestAnimationFrame(updateBounds)
    const observer = new ResizeObserver(updateBounds)
    observer.observe(element)
    return () => { cancelAnimationFrame(frame); observer.disconnect() }
  }, [artworks.length, updateBounds])

  const scroll = (direction: -1 | 1) => {
    const element = viewport.current
    if (!element) return
    element.scrollBy({
      left: direction * Math.max(240, element.clientWidth * .82),
      behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth',
    })
  }

  if (!artworks.length) return null

  return <div className="home-artwork-rail" aria-label="최근 공개 작품 목록">
    <button className="home-artwork-rail-button previous" type="button" aria-label="이전 작품 보기" disabled={!bounds.canScrollLeft} onClick={() => scroll(-1)}>‹</button>
    <div className="home-artwork-rail-viewport" ref={viewport} onScroll={updateBounds}>
      {artworks.map((artwork, index) => {
        const image = primaryImage(artwork)
        const targetParams = new URLSearchParams({ artwork: artwork.id })
        const page = Math.floor(index / WORKS_PAGE_SIZE)
        if (page > 0) targetParams.set('page', String(page))
        const focalX = Number.isFinite(artwork.carouselFocalX) ? artwork.carouselFocalX : .5
        const focalY = Number.isFinite(artwork.carouselFocalY) ? artwork.carouselFocalY : .5
        const zoom = Number.isFinite(artwork.carouselZoom) ? artwork.carouselZoom : 1
        const cropStyle = {
          '--carousel-focal-x': `${focalX * 100}%`,
          '--carousel-focal-y': `${focalY * 100}%`,
          '--carousel-zoom': zoom,
        } as CSSProperties
        return <Link className="home-artwork-tile" style={cropStyle} to={`/works?${targetParams}`} key={artwork.id} aria-label={`${artwork.title} 작품 메뉴에서 보기`}>
          <span className="home-artwork-tile-media">
            {image ? <img src={image.thumbnailUrl} alt="" loading="lazy" width={image.widthPx} height={image.heightPx} /> : <span className="home-artwork-tile-empty">이미지 준비 중</span>}
          </span>
          <strong>{artwork.title}</strong>
        </Link>
      })}
    </div>
    <button className="home-artwork-rail-button next" type="button" aria-label="다음 작품 보기" disabled={!bounds.canScrollRight} onClick={() => scroll(1)}>›</button>
  </div>
}
