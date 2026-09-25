import { useEffect, useRef, useState } from 'react'
import { publicApi } from '../api'
import type { Artwork } from '../types'

export function InquiryDrawer({ artwork, onClose }: { artwork: Artwork | null; onClose: () => void }) {
  const panel = useRef<HTMLDivElement>(null)
  const closeButton = useRef<HTMLButtonElement>(null)
  const [state, setState] = useState<'idle' | 'sending' | 'sent' | 'error'>('idle')
  useEffect(() => {
    if (!artwork) return
    closeButton.current?.focus()
    const previous = document.activeElement as HTMLElement | null
    const keydown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose()
      if (event.key === 'Tab' && panel.current) {
        const focusable = [...panel.current.querySelectorAll<HTMLElement>('button, input, textarea')].filter((element) => !element.hasAttribute('disabled'))
        if (!focusable.length) return
        const first = focusable[0]; const last = focusable.at(-1)!
        if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus() }
        else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus() }
      }
    }
    document.addEventListener('keydown', keydown)
    return () => { document.removeEventListener('keydown', keydown); previous?.focus() }
  }, [artwork, onClose])
  if (!artwork) return null
  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault(); setState('sending')
    const data = new FormData(event.currentTarget)
    try {
      await publicApi.inquire({ artworkId: artwork!.id, name: String(data.get('name')), email: String(data.get('email')), phone: String(data.get('phone')), message: String(data.get('message')), privacyAgreed: data.get('privacyAgreed') === 'on' })
      setState('sent')
    } catch { setState('error') }
  }
  return <div className="drawer-backdrop" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
    <div className="inquiry-drawer" ref={panel} role="dialog" aria-modal="true" aria-labelledby="inquiry-title">
      <header><div><p className="eyebrow">Artwork inquiry</p><h2 id="inquiry-title">{artwork.title}</h2></div><button ref={closeButton} className="drawer-close" type="button" onClick={onClose} aria-label="문의 닫기">×</button></header>
      {state === 'sent' ? <div className="inquiry-complete"><p>문의가 전해졌습니다.</p><span>작가가 확인한 뒤 입력하신 이메일로 답변드립니다.</span><button type="button" onClick={onClose}>닫기</button></div> : <form onSubmit={submit}>
        <p>작품의 구매 가능 여부와 배송, 전시에 관해 문의하실 수 있습니다.</p>
        <label>이름<input name="name" required maxLength={100} autoComplete="name" /></label>
        <label>이메일<input name="email" type="email" required maxLength={254} autoComplete="email" /></label>
        <label>연락처 <span>(선택)</span><input name="phone" maxLength={40} autoComplete="tel" pattern="[0-9+() .-]*" /></label>
        <label>문의 내용<textarea name="message" required maxLength={3000} rows={6} /></label>
        <label className="privacy-check"><input name="privacyAgreed" type="checkbox" required /> 답변을 위한 개인정보 수집·이용에 동의합니다.</label>
        {state === 'error' && <p className="form-error" role="alert">문의를 전송하지 못했습니다. 잠시 후 다시 시도해주세요.</p>}
        <button className="inquiry-submit" type="submit" disabled={state === 'sending'}>{state === 'sending' ? '전송 중…' : '문의 보내기'}</button>
      </form>}
    </div>
  </div>
}
