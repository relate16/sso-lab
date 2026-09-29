import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import type { Artwork } from '../types'
import { formatPrice, primaryImage, saleLabels } from './ArtworkCard'

type Point = { x: number; y: number }

export function ArtworkLightbox({ artwork, hasNext, hasPrevious, onClose, onInquiry, onNext, onPrevious }: {
  artwork: Artwork
  hasNext: boolean
  hasPrevious: boolean
  onClose: () => void
  onInquiry: (artwork: Artwork) => void
  onNext: () => void
  onPrevious: () => void
}) {
  const dialog = useRef<HTMLDivElement>(null)
  const closeButton = useRef<HTMLButtonElement>(null)
  const drag = useRef<{ origin: Point; pan: Point } | null>(null)
  const [zoom, setZoom] = useState(1)
  const [pan, setPan] = useState<Point>({ x: 0, y: 0 })
  const [dragging, setDragging] = useState(false)
  const image = primaryImage(artwork)

  useEffect(() => {
    setZoom(1)
    setPan({ x: 0, y: 0 })
  }, [artwork.id])

  useEffect(() => {
    const previousOverflow = document.body.style.overflow
    const previousFocus = document.activeElement as HTMLElement | null
    document.body.style.overflow = 'hidden'
    closeButton.current?.focus()
    const keydown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose()
      else if (event.key === 'ArrowLeft' && hasPrevious) onPrevious()
      else if (event.key === 'ArrowRight' && hasNext) onNext()
      else if (event.key === 'Tab' && dialog.current) {
        const focusable = [...dialog.current.querySelectorAll<HTMLElement>('button:not([disabled]), a[href]')]
        if (!focusable.length) return
        const first = focusable[0]
        const last = focusable.at(-1)!
        if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus() }
        else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus() }
      }
    }
    document.addEventListener('keydown', keydown)
    return () => {
      document.removeEventListener('keydown', keydown)
      document.body.style.overflow = previousOverflow
      previousFocus?.focus()
    }
  }, [hasNext, hasPrevious, onClose, onNext, onPrevious])

  function changeZoom(next: number) {
    const value = Math.min(4, Math.max(1, next))
    setZoom(value)
    if (value === 1) setPan({ x: 0, y: 0 })
  }

  function startDrag(event: React.PointerEvent<HTMLDivElement>) {
    if (zoom === 1) return
    event.currentTarget.setPointerCapture(event.pointerId)
    drag.current = { origin: { x: event.clientX, y: event.clientY }, pan }
    setDragging(true)
  }

  function moveDrag(event: React.PointerEvent<HTMLDivElement>) {
    if (!drag.current) return
    setPan({ x: drag.current.pan.x + event.clientX - drag.current.origin.x, y: drag.current.pan.y + event.clientY - drag.current.origin.y })
  }

  function endDrag(event: React.PointerEvent<HTMLDivElement>) {
    if (event.currentTarget.hasPointerCapture(event.pointerId)) event.currentTarget.releasePointerCapture(event.pointerId)
    drag.current = null
    setDragging(false)
  }

  return <div className="artwork-lightbox" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
    <div className="artwork-lightbox-dialog" ref={dialog} role="dialog" aria-modal="true" aria-labelledby="lightbox-title">
      <header className="artwork-lightbox-header">
        <p className="eyebrow">Artwork view</p>
        <button ref={closeButton} type="button" onClick={onClose} aria-label="작품 상세 닫기">×</button>
      </header>
      <div className="artwork-lightbox-layout">
        <div className={`artwork-lightbox-stage${zoom > 1 ? ' is-zoomed' : ''}${dragging ? ' is-dragging' : ''}`} onPointerDown={startDrag} onPointerMove={moveDrag} onPointerUp={endDrag} onPointerCancel={endDrag} onWheel={(event) => { event.preventDefault(); changeZoom(zoom + (event.deltaY < 0 ? .5 : -.5)) }}>
          <div className="artwork-lightbox-image-fit" style={{ transform: `translate(${pan.x}px, ${pan.y}px) scale(${zoom})` }}>
            {image ? <img src={image.webUrl} alt={`${artwork.title} 작품`} /> : <span>Image awaiting</span>}
          </div>
          {zoom > 1 && <span className="artwork-lightbox-pan-hint">드래그해 이동</span>}
        </div>
        <aside className="artwork-lightbox-info">
          <p className="artwork-state">{saleLabels[artwork.saleStatus]}</p>
          <h2 id="lightbox-title">{artwork.title}</h2>
          <p className="artwork-lightbox-meta">{[artwork.year, artwork.material, `${artwork.widthCm} × ${artwork.heightCm} cm`].filter(Boolean).join(' · ')}</p>
          {artwork.description && <p className="artwork-lightbox-description">{artwork.description}</p>}
          <p className="artwork-lightbox-price">{formatPrice(artwork.price)}</p>
          <div className="artwork-lightbox-actions">
            <Link to={`/artworks/${artwork.id}?scene=1`}>전시장에서 보기</Link>
            <button type="button" onClick={() => onInquiry(artwork)}>작품 문의</button>
          </div>
        </aside>
      </div>
      <button className="artwork-lightbox-previous" type="button" onClick={onPrevious} disabled={!hasPrevious} aria-label="이전 작품">‹</button>
      <button className="artwork-lightbox-next" type="button" onClick={onNext} disabled={!hasNext} aria-label="다음 작품">›</button>
      <div className="artwork-lightbox-zoom" aria-label="작품 확대 조절">
        <button type="button" onClick={() => changeZoom(zoom - .5)} disabled={zoom <= 1} aria-label="축소">−</button>
        <button type="button" onClick={() => changeZoom(1)} disabled={zoom === 1} aria-label="원래 크기">{Math.round(zoom * 100)}%</button>
        <button type="button" onClick={() => changeZoom(zoom + .5)} disabled={zoom >= 4} aria-label="확대">＋</button>
      </div>
    </div>
  </div>
}
