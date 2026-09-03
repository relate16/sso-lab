import { FormEvent, useEffect, useState } from 'react'
import ProfilePanel from './ProfilePanel'
import Turnstile, { turnstileEnabled } from './Turnstile'
import { csrfMutation } from './api'
import { requestSignupOtp, resendSignupOtp, verifySignupOtp, type SignupChallenge } from './signup'

type LoginResult = { authenticated: boolean; continuationPath?: string | null }
type ManagedSession = { id: string; device: string; authenticationMethod: string; loginAt: string; lastActivityAt: string; expiresAt: string; current: boolean }

function App() {
  const [view, setView] = useState<'LOGIN' | 'SIGNUP'>('LOGIN')
  const [method, setMethod] = useState<'EMAIL_OTP' | 'TOTP'>('EMAIL_OTP')
  const [userId, setUserId] = useState('')
  const [challengeId, setChallengeId] = useState('')
  const [code, setCode] = useState('')
  const [message, setMessage] = useState('Passwordless 인증 후 요청한 OIDC Client로 돌아갑니다.')
  const [busy, setBusy] = useState(false)
  const [sessions, setSessions] = useState<ManagedSession[]>([])
  const [turnstileToken, setTurnstileToken] = useState('')
  const [loginTurnstileReset, setLoginTurnstileReset] = useState(0)
  const [signupUserId, setSignupUserId] = useState('')
  const [signupUsername, setSignupUsername] = useState('')
  const [signupEmail, setSignupEmail] = useState('')
  const [signupCode, setSignupCode] = useState('')
  const [signupChallenge, setSignupChallenge] = useState<SignupChallenge | null>(null)
  const [signupMessage, setSignupMessage] = useState('가입 정보 입력 후 이메일 인증을 완료해주세요.')
  const [signupTurnstileToken, setSignupTurnstileToken] = useState('')
  const [signupTurnstileReset, setSignupTurnstileReset] = useState(0)
  const [now, setNow] = useState(Date.now())

  const loadSessions = () => fetch('/api/v1/me/sessions', { credentials: 'include' })
    .then(async response => { if (response.ok) setSessions(await response.json() as ManagedSession[]) })
  useEffect(() => { void loadSessions() }, [])
  useEffect(() => {
    if (!signupChallenge) return
    const timer = window.setInterval(() => setNow(Date.now()), 1_000)
    return () => window.clearInterval(timer)
  }, [signupChallenge])

  async function sessionMutation(path: string, method: 'DELETE' | 'POST') {
    setBusy(true)
    try {
      await csrfMutation(path, method)
      await loadSessions(); if (sessions.find(item => item.current && path.endsWith(item.id)) || method === 'POST') window.location.assign('/')
    } catch (error) { setMessage(error instanceof Error ? error.message : '요청에 실패했습니다.') }
    finally { setBusy(false) }
  }

  async function sendEmailOtp() {
    setBusy(true)
    try {
      const result = await csrfMutation<{ challengeId: string }>('/api/v1/login/email/send', 'POST', { userId, turnstileToken })
      setChallengeId(result.challengeId)
      setMessage('등록된 이메일로 OTP를 보냈습니다. 코드는 저장하거나 로그에 남기지 않습니다.')
    } catch (error) { setMessage(error instanceof Error ? error.message : '요청에 실패했습니다.') }
    finally {
      setTurnstileToken('')
      setLoginTurnstileReset(value => value + 1)
      setBusy(false)
    }
  }

  async function verify(event: FormEvent) {
    event.preventDefault(); setBusy(true)
    try {
      const path = method === 'EMAIL_OTP' ? '/api/v1/login/email/verify' : '/api/v1/login/totp/verify'
      const body = method === 'EMAIL_OTP' ? { challengeId, code } : { userId, code }
      if (method === 'TOTP') await csrfMutation('/api/v1/login/start', 'POST', { turnstileToken })
      const result = await csrfMutation<LoginResult>(path, 'POST', body)
      if (result.authenticated) window.location.assign(result.continuationPath || '/')
    } catch (error) { setMessage(error instanceof Error ? error.message : '인증에 실패했습니다.') }
    finally {
      setCode('')
      if (method === 'TOTP') {
        setTurnstileToken('')
        setLoginTurnstileReset(value => value + 1)
      }
      setBusy(false)
    }
  }

  function selectView(nextView: 'LOGIN' | 'SIGNUP') {
    setView(nextView)
    setTurnstileToken('')
    setSignupTurnstileToken('')
    setLoginTurnstileReset(value => value + 1)
    setSignupTurnstileReset(value => value + 1)
  }

  function resetSignup() {
    setSignupChallenge(null)
    setSignupCode('')
    setSignupTurnstileToken('')
    setSignupTurnstileReset(value => value + 1)
    setSignupMessage('가입 정보를 수정한 뒤 새 인증을 시작해주세요.')
  }

  function consumeSignupTurnstile() {
    setSignupTurnstileToken('')
    setSignupTurnstileReset(value => value + 1)
  }

  async function startSignup() {
    setBusy(true)
    const normalizedUserId = signupUserId.trim().toLowerCase()
    try {
      const result = await requestSignupOtp({
        userId: normalizedUserId,
        username: signupUsername.trim(),
        email: signupEmail.trim(),
        turnstileToken: signupTurnstileToken,
      })
      setSignupUserId(normalizedUserId)
      setSignupChallenge(result)
      setNow(Date.now())
      setSignupMessage(result.message)
    } catch (error) {
      setSignupMessage(error instanceof Error ? error.message : '요청을 처리하지 못했습니다.')
    } finally {
      consumeSignupTurnstile()
      setBusy(false)
    }
  }

  async function resendSignup() {
    if (!signupChallenge) return
    setBusy(true)
    try {
      const result = await resendSignupOtp(signupChallenge.challengeId, signupTurnstileToken)
      setSignupChallenge(result)
      setNow(Date.now())
      setSignupMessage(result.message)
    } catch (error) {
      setSignupMessage(error instanceof Error ? error.message : '요청을 처리하지 못했습니다.')
    } finally {
      consumeSignupTurnstile()
      setBusy(false)
    }
  }

  async function completeSignup(event: FormEvent) {
    event.preventDefault()
    if (!signupChallenge) return
    setBusy(true)
    try {
      const result = await verifySignupOtp(signupChallenge.challengeId, signupCode)
      if (!result.registered) throw new Error('요청을 처리하지 못했습니다.')
      setUserId(signupUserId)
      setMethod('EMAIL_OTP')
      setChallengeId('')
      setSignupChallenge(null)
      setSignupCode('')
      setSignupUsername('')
      setSignupEmail('')
      setView('LOGIN')
      setMessage('회원가입이 완료되었습니다. Email OTP 또는 등록된 TOTP로 로그인해주세요.')
    } catch (error) {
      setSignupMessage(error instanceof Error ? error.message : '인증에 실패했습니다.')
    } finally {
      setSignupCode('')
      setBusy(false)
    }
  }

  const resendSeconds = signupChallenge
    ? Math.max(0, Math.ceil((Date.parse(signupChallenge.resendAvailableAt) - now) / 1_000))
    : 0

  return <main className="page-shell"><section className="service-card" aria-labelledby="page-title">
    <p className="eyebrow">SSO LAB · AUTH SERVICE</p>
    <h1 id="page-title">{view === 'LOGIN' ? 'Passwordless sign in' : 'Passwordless sign up'}</h1>
    <p className="summary">비밀번호 없이 이메일 인증 또는 TOTP로 계정을 만들고 로그인합니다. Browser Token Storage는 사용하지 않습니다.</p>
    <div className="auth-tabs" role="tablist" aria-label="계정 인증">
      <button type="button" role="tab" aria-selected={view === 'LOGIN'} aria-controls="login-panel"
        className={view === 'LOGIN' ? 'selected' : ''} onClick={() => selectView('LOGIN')}>로그인</button>
      <button type="button" role="tab" aria-selected={view === 'SIGNUP'} aria-controls="signup-panel"
        className={view === 'SIGNUP' ? 'selected' : ''} onClick={() => selectView('SIGNUP')}>회원가입</button>
    </div>
    {view === 'LOGIN' && <section id="login-panel" role="tabpanel">
      <div className="method-tabs" role="group" aria-label="인증 방법">
        <button type="button" className={method === 'EMAIL_OTP' ? 'selected' : ''} onClick={() => setMethod('EMAIL_OTP')}>Email OTP</button>
        <button type="button" className={method === 'TOTP' ? 'selected' : ''} onClick={() => setMethod('TOTP')}>TOTP</button>
      </div>
      <form onSubmit={verify}>
        <label>User ID<input autoComplete="username" value={userId} onChange={event => setUserId(event.target.value)} required /></label>
        <Turnstile onToken={setTurnstileToken} resetKey={loginTurnstileReset} />
        {method === 'EMAIL_OTP' && <button type="button" onClick={sendEmailOtp} disabled={busy || !userId || (turnstileEnabled && !turnstileToken)}>OTP 보내기</button>}
        <label>인증 코드<input inputMode="numeric" autoComplete="one-time-code" pattern="[0-9]{6}" maxLength={6}
          value={code} onChange={event => setCode(event.target.value)} required /></label>
        <button type="submit" disabled={busy || !code || (method === 'EMAIL_OTP' && !challengeId) || (method === 'TOTP' && turnstileEnabled && !turnstileToken)}>로그인</button>
      </form>
      <p className="notice" role="status">{message}</p>
    </section>}
    {view === 'SIGNUP' && <section id="signup-panel" role="tabpanel" aria-labelledby="signup-title">
      <h2 id="signup-title">Passwordless 회원가입</h2>
      <p className="panel-description">입력한 이메일로 받은 6자리 OTP를 인증하면 계정이 생성됩니다.</p>
      <form onSubmit={signupChallenge
        ? completeSignup
        : event => { event.preventDefault(); void startSignup() }}>
        <label>User ID
          <input autoComplete="username" value={signupUserId} minLength={4} maxLength={30}
            pattern="[a-z0-9._-]{4,30}" title="영문 소문자, 숫자, 마침표, 밑줄, 하이픈을 사용해 4~30자로 입력해주세요."
            onChange={event => setSignupUserId(event.target.value.toLowerCase())} disabled={Boolean(signupChallenge)} required />
          <span className="field-hint">영문 소문자, 숫자, 마침표, 밑줄, 하이픈 4~30자 · 가입 후 변경할 수 없습니다.</span>
        </label>
        <label>Username
          <input autoComplete="name" value={signupUsername} maxLength={100}
            onChange={event => setSignupUsername(event.target.value)} disabled={Boolean(signupChallenge)} required />
          <span className="field-hint">서비스에 표시되는 이름이며 가입 후 변경할 수 있습니다.</span>
        </label>
        <label>Email
          <input type="email" autoComplete="email" value={signupEmail} maxLength={320}
            onChange={event => setSignupEmail(event.target.value)} disabled={Boolean(signupChallenge)} required />
        </label>
        <Turnstile onToken={setSignupTurnstileToken} resetKey={signupTurnstileReset} />
        {!signupChallenge && <button type="submit"
          disabled={busy || !signupUserId || !signupUsername.trim() || !signupEmail || (turnstileEnabled && !signupTurnstileToken)}>회원가입 OTP 보내기</button>}
        {signupChallenge && <>
          <p className="challenge-status">OTP는 {new Date(signupChallenge.expiresAt).toLocaleTimeString()}까지 유효합니다.</p>
          <label>회원가입 인증 코드
            <input inputMode="numeric" autoComplete="one-time-code" pattern="[0-9]{6}" maxLength={6}
              value={signupCode} onChange={event => setSignupCode(event.target.value)} required />
          </label>
          <div className="form-actions">
            <button type="submit" disabled={busy || signupCode.length !== 6}>회원가입 완료</button>
            <button type="button" onClick={() => void resendSignup()}
              disabled={busy || resendSeconds > 0 || (turnstileEnabled && !signupTurnstileToken)}>
              {resendSeconds > 0 ? `OTP 재발송 (${resendSeconds}초)` : 'OTP 재발송'}
            </button>
            <button type="button" onClick={resetSignup} disabled={busy}>가입 정보 수정</button>
          </div>
        </>}
      </form>
      <p className="notice" role="status">{signupMessage}</p>
    </section>}
    {sessions.length > 0 && <section aria-labelledby="sessions-title"><h2 id="sessions-title">내 로그인 세션</h2>
      <p>세션 원문과 Token은 표시하지 않습니다.</p>
      <ul>{sessions.map(item => <li key={item.id}><strong>{item.device}{item.current ? ' · 현재' : ''}</strong><br />로그인 {new Date(item.loginAt).toLocaleString()} · 최근 활동 {new Date(item.lastActivityAt).toLocaleString()} · 만료 {new Date(item.expiresAt).toLocaleString()} <button type="button" disabled={busy} onClick={() => void sessionMutation(`/api/v1/me/sessions/${item.id}`, 'DELETE')}>종료</button></li>)}</ul>
      <button type="button" disabled={busy} onClick={() => void sessionMutation('/api/v1/me/logout-all', 'POST')}>모든 기기에서 로그아웃</button>
    </section>}
    <ProfilePanel />
  </section></main>
}

export default App
