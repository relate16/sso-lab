import { useEffect, useState } from 'react'
import { loadBffState, type Csrf, type Session } from './session-api'

function MarsLogo() {
  return <svg width="46" height="46" viewBox="0 0 48 48" fill="none" role="img" aria-label="SSO Lab HR">
    <circle cx="24" cy="24" r="12" fill="currentColor" opacity=".95" />
    <path d="M10 29.5c4.8 6.1 19.2 7.6 28.2 1.4 5.5-3.8 5.2-8.8-1.1-11.2-7.3-2.8-20.3-.2-27 5.1-4.5 3.5-3.5 7.2 2.2 8.4" stroke="currentColor" strokeWidth="2" strokeLinecap="round" opacity=".55" />
    <circle cx="20" cy="20" r="2.1" fill="white" opacity=".55" />
    <path d="m31.5 12.5 4-4m0 0h-3.2m3.2 0v3.2" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
  </svg>
}

function LoginIcon() {
  return <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <path d="m10 17 5-5-5-5" /><path d="M15 12H3" /><path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4" />
  </svg>
}

function App() {
  const [session, setSession] = useState<Session | null>(null); const [loading, setLoading] = useState(true)
  const [csrf, setCsrf] = useState<Csrf | null>(null)
  useEffect(() => { loadBffState().then(state => {
    setSession(state.session); setCsrf(state.csrf)
  }).catch(() => { setSession(null); setCsrf(null) }).finally(() => setLoading(false)) }, [])
  const authenticated = Boolean(session?.authenticated)
  const displayName = session?.name || session?.username || session?.userId || '사용자'
  if (loading) return <main className="center"><p className="notice">세션을 확인하고 있습니다…</p></main>
  if (!authenticated) return <main className="center"><section className="login-card" aria-labelledby="page-title">
    <div className="login-brand"><span className="login-logo"><MarsLogo /></span><div><strong>SSO Lab HR</strong><span>Human Resources Portal</span></div></div>
    <p className="eyebrow">임직원 전용 인사 포털</p><h1 id="page-title">SSO Lab HR 포털</h1><p>조직의 인사 정보와 업무를 확인합니다.</p>
    <a className="login-link" href="/oauth2/authorization/hr-client">Passwordless SSO 로그인<LoginIcon /></a>
  </section></main>
  return <main className="page-shell"><section className="service-card authenticated-card" aria-labelledby="page-title">
    <header className="service-header"><div><p className="eyebrow">SSO LAB · HR</p><h1 id="page-title">HR 포털</h1>
      <p className="summary">인사 서비스에 필요한 내 계정 정보와 접근 상태를 확인합니다.</p></div>
      {authenticated && <div className="identity"><span className="status"><span className="status-dot" />SSO authenticated</span><strong>{displayName}</strong><small>{session?.userId}</small>
        {csrf && <form method="post" action="/api/v1/logout"><input type="hidden" name={csrf.parameterName} value={csrf.token} /><button type="submit">로그아웃</button></form>}</div>}
    </header>
    <div className="workspace">
          <section className="panel"><p className="section-kicker">MY PROFILE</p><h2>사용자 정보</h2><dl>
            <div><dt>표시 이름</dt><dd>{displayName}</dd></div><div><dt>로그인 ID</dt><dd>{session?.userId || '—'}</dd></div>
            <div><dt>역할</dt><dd>{session?.roles?.join(', ') || 'USER'}</dd></div><div><dt>소속 그룹</dt><dd>{session?.groups?.join(', ') || '할당된 그룹 없음'}</dd></div>
          </dl></section>
          <section className="panel access-panel"><p className="section-kicker">ACCESS</p><h2>접근 상태</h2><p>통합 인증으로 HR 서비스에 안전하게 연결되었습니다.</p>
            <div className="access-row"><span>인증 수준</span><strong>{session?.acr || '확인됨'}</strong></div><div className="access-row"><span>인증 방법</span><strong>{session?.amr?.join(', ') || 'SSO'}</strong></div>
          </section>
          <pre className="sr-only" aria-hidden="true">{JSON.stringify(session, null, 2)}</pre>
        </div>
  </section></main>
}
export default App
