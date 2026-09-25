import { useEffect, useState } from 'react'
import { studioApi } from '../api'
import type { Csrf, Inquiry, InquiryStatus } from '../types'

export function StudioInquiries({ csrf }: { csrf: Csrf }) {
  const [items, setItems] = useState<Inquiry[]>([]); const [message, setMessage] = useState('')
  const load = () => studioApi.inquiries().then(result => setItems(result.content)).catch(() => setMessage('문의 목록을 불러오지 못했습니다.'))
  useEffect(() => { void load() }, [])
  const update = async (id: string, status: InquiryStatus) => { await studioApi.inquiryStatus(csrf, id, status); await load() }
  return <section className="studio-page"><header className="studio-page-header"><div><p className="eyebrow">Correspondence</p><h1>작품 문의</h1><p>문의 개인정보는 Studio 관리자에게만 표시됩니다.</p></div></header>{message && <p role="status" className="inline-notice">{message}</p>}<div className="inquiry-list">{items.map(item => <article key={item.id}><header><strong>{item.artworkTitle}</strong><time>{new Date(item.createdAt).toLocaleString('ko-KR')}</time></header><dl><div><dt>이름</dt><dd>{item.name}</dd></div><div><dt>이메일</dt><dd>{item.email}</dd></div>{item.phone && <div><dt>연락처</dt><dd>{item.phone}</dd></div>}</dl><p className="plain-message">{item.message}</p><label>상태<select value={item.status} onChange={e => void update(item.id, e.target.value as InquiryStatus)}><option value="NEW">새 문의</option><option value="READ">확인함</option><option value="CLOSED">종료</option></select></label></article>)}</div>{!items.length && <p className="empty-copy">도착한 문의가 없습니다.</p>}</section>
}
