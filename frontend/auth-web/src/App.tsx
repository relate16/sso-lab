import { FormEvent, useEffect, useRef, useState } from 'react'
import ProfilePanel from './ProfilePanel'
import Turnstile, { turnstileEnabled } from './Turnstile'
import { csrfMutation } from './api'
import { requestSignupOtp, resendSignupOtp, verifySignupOtp, type SignupChallenge } from './signup'

type LoginResult = { authenticated: boolean; continuationPath?: string | null }
type LoginOtpChallenge = { challengeId: string; expiresAt: string; resendAvailableAt: string; message: string }
type ManagedSession = { id: string; device: string; authenticationMethod: string; loginAt: string; lastActivityAt: string; expiresAt: string; current: boolean }
type AuthenticationState = 'CHECKING' | 'SIGNED_OUT' | 'SIGNED_IN'

function App() {
  const [view, setView] = useState<'LOGIN' | 'SIGNUP'>('LOGIN')
  const [method, setMethod] = useState<'EMAIL_OTP' | 'TOTP'>('EMAIL_OTP')
  const [userId, setUserId] = useState('')
  const [challengeId, setChallengeId] = useState('')
  const [loginResendAvailableAt, setLoginResendAvailableAt] = useState('')
  const [loginResendIntervalMs, setLoginResendIntervalMs] = useState(0)
  const [emailOtpRequested, setEmailOtpRequested] = useState(false)
  const [emailOtpInitialPending, setEmailOtpInitialPending] = useState(false)
  const emailOtpRequestSequence = useRef(0)
  const [code, setCode] = useState('')
  const [message, setMessage] = useState('Passwordless 인증 후 요청한 OIDC Client로 돌아갑니다.')
  const [busy, setBusy] = useState(false)
  const [sessions, setSessions] = useState<ManagedSession[]>([])
  const [authenticationState, setAuthenticationState] = useState<AuthenticationState>('CHECKING')
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

  const loadSessions = () => fetch('/api/v1/me/sessions', { credentials: 'include', cache: 'no-store' })
    .then(async response => {
      if (!response.ok) {
        setSessions([])
        setAuthenticationState('SIGNED_OUT')
        return
      }
      setSessions(await response.json() as ManagedSession[])
      setAuthenticationState('SIGNED_IN')
    })
    .catch(() => {
      setSessions([])
      setAuthenticationState('SIGNED_OUT')
    })
  useEffect(() => { void loadSessions() }, [])
  useEffect(() => {
    if (!signupChallenge && !loginResendAvailableAt) return
    const timer = window.setInterval(() => setNow(Date.now()), 1_000)
    return () => window.clearInterval(timer)
  }, [signupChallenge, loginResendAvailableAt])

  async function sessionMutation(path: string, method: 'DELETE' | 'POST') {
    const endsCurrentSession = method === 'DELETE'
      && sessions.some(item => item.current && path.endsWith(item.id))
    setBusy(true)
    try {
      await csrfMutation(path, method)
      await loadSessions()
      if (endsCurrentSession || method === 'POST') window.location.assign('/')
    } catch (error) { setMessage(error instanceof Error ? error.message : '요청에 실패했습니다.') }
    finally { setBusy(false) }
  }

  async function sendEmailOtp() {
    if (emailOtpInitialPending) return
    const requestSequence = ++emailOtpRequestSequence.current
    const requestTurnstileToken = turnstileToken
    setEmailOtpRequested(true)
    setEmailOtpInitialPending(true)
    setMessage('OTP를 보냈습니다. 이메일에서 6자리 인증 코드를 확인해주세요.')
    setTurnstileToken('')
    setLoginTurnstileReset(value => value + 1)
    try {
      const result = await csrfMutation<LoginOtpChallenge>('/api/v1/login/email/send', 'POST', { userId, turnstileToken: requestTurnstileToken })
      if (requestSequence !== emailOtpRequestSequence.current) return
      setChallengeId(result.challengeId)
      setLoginResendAvailableAt(result.resendAvailableAt)
      setLoginResendIntervalMs(Math.max(0, Date.parse(result.resendAvailableAt) - Date.now()))
      setNow(Date.now())
    } catch (error) {
      if (requestSequence !== emailOtpRequestSequence.current) return
      setChallengeId('')
      setEmailOtpRequested(false)
      setMessage(error instanceof Error ? error.message : '요청에 실패했습니다.')
    }
    finally {
      if (requestSequence === emailOtpRequestSequence.current) setEmailOtpInitialPending(false)
    }
  }

  function resendEmailOtp() {
    if (!challengeId || loginResendSeconds > 0 || (turnstileEnabled && !turnstileToken)) return
    const requestSequence = ++emailOtpRequestSequence.current
    const requestChallengeId = challengeId
    const requestTurnstileToken = turnstileToken
    const previousResendAvailableAt = loginResendAvailableAt
    setMessage('OTP를 다시 보냈습니다. 이메일에서 최신 인증 코드를 확인해주세요.')
    setLoginResendAvailableAt(new Date(Date.now() + Math.max(1_000, loginResendIntervalMs)).toISOString())
    setNow(Date.now())
    setTurnstileToken('')
    setLoginTurnstileReset(value => value + 1)
    void csrfMutation<LoginOtpChallenge>('/api/v1/login/email/resend', 'POST', {
      challengeId: requestChallengeId,
      turnstileToken: requestTurnstileToken,
    }).then(result => {
      if (requestSequence !== emailOtpRequestSequence.current) return
      setChallengeId(result.challengeId)
      setLoginResendAvailableAt(result.resendAvailableAt)
      setLoginResendIntervalMs(Math.max(0, Date.parse(result.resendAvailableAt) - Date.now()))
      setNow(Date.now())
    }).catch(error => {
      if (requestSequence === emailOtpRequestSequence.current) {
        setLoginResendAvailableAt(previousResendAvailableAt)
        setMessage(error instanceof Error ? error.message : '요청에 실패했습니다.')
      }
    })
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
      setLoginResendAvailableAt('')
      setLoginResendIntervalMs(0)
      setEmailOtpRequested(false)
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
  const loginResendSeconds = loginResendAvailableAt
    ? Math.max(0, Math.ceil((Date.parse(loginResendAvailableAt) - now) / 1_000))
    : 0

  if (authenticationState === 'CHECKING') return <main className="page-shell"><section className="service-card loading-card">
    <p className="eyebrow">SSO LAB · AUTH</p>
    <p className="loading-label">로그인 상태를 확인하고 있습니다…</p>
  </section></main>

  if (authenticationState === 'SIGNED_IN') {
    const currentSession = sessions.find(item => item.current)
    return <main className="account-shell">
      <header className="account-header">
        <div>
          <p className="eyebrow">SSO LAB · AUTH</p>
          <h1 id="page-title">계정 및 보안</h1>
          <p className="summary">내 프로필, 인증 수단과 로그인 세션을 안전하게 관리합니다.</p>
        </div>
        <div className="signed-in-summary" aria-label="현재 로그인 상태">
          <span className="status"><span className="status-dot" />로그인됨</span>
          <strong>{currentSession?.authenticationMethod || 'Passwordless'}</strong>
          <span>{currentSession?.device || '현재 브라우저'}</span>
          {currentSession && <button type="button" className="secondary-button" disabled={busy}
            onClick={() => void sessionMutation(`/api/v1/me/sessions/${currentSession.id}`, 'DELETE')}>로그아웃</button>}
        </div>
      </header>
      <div className="account-content">
        <ProfilePanel />
        <section className="panel sessions-panel" aria-labelledby="sessions-title">
          <div className="section-heading"><div><p className="section-kicker">SECURITY</p><h2 id="sessions-title">로그인 세션</h2></div><span>{sessions.length}개</span></div>
          <p className="panel-description">접속 중인 기기를 확인하고 필요하지 않은 세션을 종료할 수 있습니다. 세션 원문과 Token은 표시하지 않습니다.</p>
          <ul className="session-list">{sessions.map(item => <li key={item.id}>
            <div><strong>{item.device}</strong>{item.current && <span className="current-badge">현재 세션</span>}
              <small>{item.authenticationMethod} · 최근 활동 {new Date(item.lastActivityAt).toLocaleString()}</small></div>
            <button type="button" className="secondary-button" disabled={busy}
              onClick={() => void sessionMutation(`/api/v1/me/sessions/${item.id}`, 'DELETE')}>{item.current ? '로그아웃' : '종료'}</button>
          </li>)}</ul>
          <button type="button" className="danger-button" disabled={busy}
            onClick={() => void sessionMutation('/api/v1/me/logout-all', 'POST')}>모든 기기에서 로그아웃</button>
        </section>
      </div>
    </main>
  }

  return <main className="page-shell"><section className="service-card" aria-labelledby="page-title">
    <p className="eyebrow">SSO LAB · AUTH</p>
    <h1 id="page-title">{view === 'LOGIN' ? 'Passwordless sign in' : 'Passwordless sign up'}</h1>
    <p className="summary">비밀번호 없이 이메일 인증 또는 TOTP로 안전하게 로그인합니다.</p>
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
        <div className="step-label">{method === 'EMAIL_OTP' ? (emailOtpRequested ? '2단계 · 인증 코드 확인' : '1단계 · 로그인 ID 확인') : '인증 앱으로 로그인'}</div>
        <label>User ID<input autoComplete="username" value={userId} onChange={event => setUserId(event.target.value)}
          disabled={method === 'EMAIL_OTP' && emailOtpRequested} required /></label>
        {(method === 'TOTP' || method === 'EMAIL_OTP') && <Turnstile onToken={setTurnstileToken} resetKey={loginTurnstileReset} />}
        {method === 'EMAIL_OTP' && !emailOtpRequested && <button type="button" onClick={sendEmailOtp}
          disabled={emailOtpInitialPending || !userId || (turnstileEnabled && !turnstileToken)}>OTP 보내기</button>}
        {(method === 'TOTP' || emailOtpRequested) && <>
          <label>인증 코드<input inputMode="numeric" autoComplete="one-time-code" pattern="[0-9]{6}" maxLength={6}
            value={code} onChange={event => setCode(event.target.value)} required /></label>
          <button type="submit" disabled={busy || (method === 'EMAIL_OTP' && emailOtpInitialPending) || !code || (method === 'EMAIL_OTP' && !challengeId) || (method === 'TOTP' && turnstileEnabled && !turnstileToken)}>로그인</button>
        </>}
        {method === 'EMAIL_OTP' && emailOtpRequested && <div className="form-actions">
          <button type="button" className="text-button" onClick={() => void resendEmailOtp()}
            disabled={!challengeId || loginResendSeconds > 0 || (turnstileEnabled && !turnstileToken)}>
            {loginResendSeconds > 0 ? `OTP 다시 보내기 (${loginResendSeconds}초)` : 'OTP 다시 보내기'}
          </button>
          <button type="button" className="text-button" onClick={() => {
            emailOtpRequestSequence.current += 1
            setChallengeId(''); setLoginResendAvailableAt(''); setLoginResendIntervalMs(0)
            setEmailOtpRequested(false); setEmailOtpInitialPending(false)
            setCode(''); setTurnstileToken('')
            setLoginTurnstileReset(value => value + 1); setMessage('다른 로그인 ID를 입력해주세요.')
          }}>다른 ID로 로그인</button>
        </div>}
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
  </section></main>
}

export default App
