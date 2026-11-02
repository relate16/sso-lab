import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { studioApi } from '../api'
import type { Artwork, Csrf } from '../types'

export function StudioArtworks({ csrf }: { csrf: Csrf }) {
  const [artworks, setArtworks] = useState<Artwork[]>([])
  const [message, setMessage] = useState('')
  const dragIndex = useRef<number | null>(null)
  useEffect(() => { void studioApi.artworks().then(setArtworks).catch(() => setMessage('작품 목록을 불러오지 못했습니다.')) }, [])
  const move = async (from: number, to: number) => {
    if (to < 0 || to >= artworks.length || from === to) return
    const previous = artworks
    const next = [...artworks]; const [item] = next.splice(from, 1); next.splice(to, 0, item); setArtworks(next)
    try { await studioApi.reorder(csrf, next); setMessage('전시 순서를 저장했습니다.') }
    catch { setArtworks(previous); setMessage('순서를 저장하지 못해 이전 상태로 되돌렸습니다.') }
  }
  const restore = async (id: string) => {
    try {
      const restored = await studioApi.restore(csrf, id)
      setArtworks(items => items.map(item => item.id === id ? restored : item))
      setMessage('작품을 비공개 상태로 복원했습니다.')
    } catch {
      setMessage('작품을 복원하지 못했습니다.')
    }
  }
  return <section className="studio-page"><header className="studio-page-header"><div><p className="eyebrow">Collection</p><h1>작품 관리</h1><p>삭제한 작품은 통계와 문의를 보존한 채 보관되며 필요하면 다시 복원할 수 있습니다.</p></div><Link className="primary-action" to="new">새 작품 등록</Link></header>{message && <p className="inline-notice" role="status">{message}</p>}<div className="studio-list">{artworks.map((artwork, index) => <article key={artwork.id} className={`studio-row${artwork.archivedAt ? ' is-archived' : ''}`} draggable={!artwork.archivedAt} onDragStart={() => { dragIndex.current = index }} onDragOver={event => event.preventDefault()} onDrop={() => { if (dragIndex.current !== null) void move(dragIndex.current, index) }}>
    <span className="drag-handle" aria-hidden="true">{artwork.archivedAt ? '·' : '⠿'}</span>{artwork.images[0] ? <img src={artwork.images[0].thumbnailUrl.replace('/media/', '/studio/media/')} alt="" /> : <span className="image-placeholder">이미지 없음</span>}<div className="studio-row-main"><strong>{artwork.title}</strong><span>{artwork.archivedAt ? '보관됨' : artwork.published ? '공개' : '비공개'} · {artwork.saleStatus}</span></div>{artwork.archivedAt ? <button type="button" onClick={() => void restore(artwork.id)}>복원</button> : <div className="order-actions" aria-label={`${artwork.title} 순서 변경`}><button onClick={() => void move(index, index - 1)} disabled={index === 0} aria-label="위로 이동">↑</button><button onClick={() => void move(index, index + 1)} disabled={index === artworks.length - 1} aria-label="아래로 이동">↓</button></div>}<Link to={artwork.id}>수정</Link>
  </article>)}</div>{!artworks.length && <p className="empty-copy">등록된 작품이 없습니다.</p>}</section>
}
