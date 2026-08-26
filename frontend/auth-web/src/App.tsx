import { FormEvent, useEffect, useRef, useState } from 'react'

declare global {
  interface Window {
    ssoLabRuntimeConfig?: {
      turnstileEnabled?: boolean
      turnstileSiteKey?: string
    }
    ssoLabTurnstileCallback?: (token: string) => void
    ssoLabTurnstileExpired?: () => void
  }
}

type Csrf = { headerName: string; token: string }
type LoginResult = { authenticated: boolean; continuationPath?: string | null }
type ManagedSession = { id: string; device: string; authenticationMethod: string; loginAt: string; lastActivityAt: string; expiresAt: string; current: boolean }

const turnstileEnabled = window.ssoLabRuntimeConfig?.turnstileEnabled
  ?? import.meta.env.VITE_TURNSTILE_ENABLED === 'true'
const turnstileSiteKey = window.ssoLabRuntimeConfig?.turnstileSiteKey
  ?? import.meta.env.VITE_TURNSTILE_SITE_KEY
  ?? ''

function Turnstile({ onToken }: { onToken: (token: string) => void }) {
  const container = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!turnstileEnabled) return
    window.ssoLabTurnstileCallback = token => onToken(token)
    window.ssoLabTurnstileExpired = () => onToken('')
    if (!document.querySelector('script[data-sso-lab-turnstile]')) {
      const script = document.createElement('script')
      script.src = 'https://challenges.cloudflare.com/turnstile/v0/api.js'
      script.async = true
      script.defer = true
      script.dataset.ssoLabTurnstile = 'true'
      document.head.appendChild(script)
    }
    return () => {
      delete window.ssoLabTurnstileCallback
      delete window.ssoLabTurnstileExpired
    }
  }, [onToken])

  if (!turnstileEnabled) return null
  if (!turnstileSiteKey) return <p role="alert">Turnstile Site Key 설정이 필요합니다.</p>
  return <div ref={container} className="cf-turnstile" data-sitekey={turnstileSiteKey}
    data-callback="ssoLabTurnstileCallback" data-expired-callback="ssoLabTurnstileExpired" />
}

async function postJson<T>(path: string, body: unknown): Promise<T> {
  const csrfResponse = await fetch('/api/v1/csrf', { credentials: 'include' })
  if (!csrfResponse.ok) throw new Error('CSRF 토큰을 가져오지 못했습니다.')
  const csrf = await csrfResponse.json() as Csrf
  const response = await fetch(path, {
    method: 'POST', credentials: 'include',
    headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token },
    body: JSON.stringify(body),
  })
  if (!response.ok) throw new Error('인증 요청을 처리하지 못했습니다.')
  return response.json() as Promise<T>
}

function App() {
  const [method, setMethod] = useState<'EMAIL_OTP' | 'TOTP'>('EMAIL_OTP')
  const [userId, setUserId] = useState('')
  const [challengeId, setChallengeId] = useState('')
  const [code, setCode] = useState('')
  const [message, setMessage] = useState('Passwordless 인증 후 요청한 OIDC Client로 돌아갑니다.')
  const [busy, setBusy] = useState(false)
  const [sessions, setSessions] = useState<ManagedSession[]>([])
  const [turnstileToken, setTurnstileToken] = useState('')

  const loadSessions = () => fetch('/api/v1/me/sessions', { credentials: 'include' })
    .then(async response => { if (response.ok) setSessions(await response.json() as ManagedSession[]) })
  useEffect(() => { void loadSessions() }, [])

  async function sessionMutation(path: string, method: 'DELETE' | 'POST') {
    setBusy(true)
    try {
      const csrfResponse = await fetch('/api/v1/csrf', { credentials: 'include' })
      const csrf = await csrfResponse.json() as Csrf
      const response = await fetch(path, { method, credentials: 'include', headers: { [csrf.headerName]: csrf.token } })
      if (!response.ok) throw new Error('세션 종료 요청을 처리하지 못했습니다.')
      await loadSessions(); if (sessions.find(item => item.current && path.endsWith(item.id)) || method === 'POST') window.location.assign('/')
    } catch (error) { setMessage(error instanceof Error ? error.message : '요청에 실패했습니다.') }
    finally { setBusy(false) }
  }

  async function sendEmailOtp() {
    setBusy(true)
    try {
      const result = await postJson<{ challengeId: string }>('/api/v1/login/email/send', { userId, turnstileToken })
      setChallengeId(result.challengeId)
      setTurnstileToken('')
      setMessage('등록된 이메일로 OTP를 보냈습니다. 코드는 저장하거나 로그에 남기지 않습니다.')
    } catch (error) { setMessage(error instanceof Error ? error.message : '요청에 실패했습니다.') }
    finally { setBusy(false) }
  }

  async function verify(event: FormEvent) {
    event.preventDefault(); setBusy(true)
    try {
      const path = method === 'EMAIL_OTP' ? '/api/v1/login/email/verify' : '/api/v1/login/totp/verify'
      const body = method === 'EMAIL_OTP' ? { challengeId, code } : { userId, code }
      if (method === 'TOTP') await postJson('/api/v1/login/start', { turnstileToken })
      const result = await postJson<LoginResult>(path, body)
      if (result.authenticated) window.location.assign(result.continuationPath || '/')
    } catch (error) { setMessage(error instanceof Error ? error.message : '인증에 실패했습니다.') }
    finally { setCode(''); setBusy(false) }
  }

  return <main className="page-shell"><section className="service-card" aria-labelledby="page-title">
    <p className="eyebrow">SSO LAB · AUTH SERVICE</p><h1 id="page-title">Passwordless sign in</h1>
    <p className="summary">Email OTP 또는 TOTP 중 등록된 방법 하나로 인증합니다. 비밀번호와 Browser Token Storage는 사용하지 않습니다.</p>
    <div className="method-tabs" role="group" aria-label="인증 방법">
      <button type="button" className={method === 'EMAIL_OTP' ? 'selected' : ''} onClick={() => setMethod('EMAIL_OTP')}>Email OTP</button>
      <button type="button" className={method === 'TOTP' ? 'selected' : ''} onClick={() => setMethod('TOTP')}>TOTP</button>
    </div>
    <form onSubmit={verify}>
      <label>User ID<input autoComplete="username" value={userId} onChange={event => setUserId(event.target.value)} required /></label>
      <Turnstile onToken={setTurnstileToken} />
      {method === 'EMAIL_OTP' && <button type="button" onClick={sendEmailOtp} disabled={busy || !userId || (turnstileEnabled && !turnstileToken)}>OTP 보내기</button>}
      <label>인증 코드<input inputMode="numeric" autoComplete="one-time-code" value={code} onChange={event => setCode(event.target.value)} required /></label>
      <button type="submit" disabled={busy || !code || (method === 'EMAIL_OTP' && !challengeId) || (method === 'TOTP' && turnstileEnabled && !turnstileToken)}>로그인</button>
    </form>
    <p className="notice" role="status">{message}</p>
    {sessions.length > 0 && <section aria-labelledby="sessions-title"><h2 id="sessions-title">내 로그인 세션</h2>
      <p>세션 원문과 Token은 표시하지 않습니다.</p>
      <ul>{sessions.map(item => <li key={item.id}><strong>{item.device}{item.current ? ' · 현재' : ''}</strong><br />로그인 {new Date(item.loginAt).toLocaleString()} · 최근 활동 {new Date(item.lastActivityAt).toLocaleString()} · 만료 {new Date(item.expiresAt).toLocaleString()} <button type="button" disabled={busy} onClick={() => void sessionMutation(`/api/v1/me/sessions/${item.id}`, 'DELETE')}>종료</button></li>)}</ul>
      <button type="button" disabled={busy} onClick={() => void sessionMutation('/api/v1/me/logout-all', 'POST')}>모든 기기에서 로그아웃</button>
    </section>}
  </section></main>
}

export default App
