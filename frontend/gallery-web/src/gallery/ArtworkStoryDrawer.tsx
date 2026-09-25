import { useEffect, useRef } from 'react'
import type { Artwork } from '../types'

export function ArtworkStoryDrawer({ artwork, open, onClose, onInquiry }: { artwork: Artwork; open: boolean; onClose: () => void; onInquiry: () => void }) {
  const drawer = useRef<HTMLElement>(null)
  const close = useRef<HTMLButtonElement>(null)
  useEffect(() => {
    if (!open) return
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    const previous = document.activeElement as HTMLElement | null
    close.current?.focus()
    const keydown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose()
      if (event.key === 'Tab' && drawer.current) {
        const focusable = [...drawer.current.querySelectorAll<HTMLElement>('button')]
        const first = focusable[0]; const last = focusable.at(-1)
        if (event.shiftKey && document.activeElement === first && last) { event.preventDefault(); last.focus() }
        else if (!event.shiftKey && document.activeElement === last && first) { event.preventDefault(); first.focus() }
      }
    }
    document.addEventListener('keydown', keydown)
    return () => { document.removeEventListener('keydown', keydown); document.body.style.overflow = previousOverflow; previous?.focus() }
  }, [open, onClose])
  if (!open) return null
  return <div className="story-backdrop" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
    <aside className="story-drawer" ref={drawer} role="dialog" aria-modal="true" aria-labelledby="story-title">
      <button ref={close} className="drawer-close" type="button" onClick={onClose} aria-label="작품 설명 닫기">×</button>
      <p className="eyebrow">Wall text</p><h2 id="story-title">{artwork.title}</h2>
      <div className="story-meta"><span>{artwork.year ?? '연도 미상'}</span><span>{artwork.material ?? '재료 미상'}</span><span>{artwork.widthCm} × {artwork.heightCm} cm</span></div>
      <p className="story-description">{artwork.description}</p>
      <button className="story-inquiry" type="button" onClick={onInquiry}>이 작품 문의하기</button>
    </aside>
  </div>
}
