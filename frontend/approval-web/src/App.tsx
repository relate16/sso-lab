import { useEffect, useState } from 'react'
import { loadBffState, type Csrf, type Session } from './session-api'
function App() {
  const [session, setSession] = useState<Session | null>(null); const [loading, setLoading] = useState(true)
  const [csrf, setCsrf] = useState<Csrf | null>(null)
  useEffect(() => { loadBffState().then(state => {
    setSession(state.session); setCsrf(state.csrf)
  }).catch(() => { setSession(null); setCsrf(null) }).finally(() => setLoading(false)) }, [])
  const authenticated = Boolean(session?.authenticated)
  const displayName = session?.name || session?.username || session?.userId || '사용자'
  if (loading) return <main className="center"><p className="notice">세션을 확인하고 있습니다…</p></main>
  if (!authenticated) return <main className="center"><section className="login-card" aria-labelledby="page-title"><span className="login-mark" aria-hidden="true">AP</span><p className="eyebrow">업무 결재 서비스</p><h1 id="page-title">전자결재</h1><p>결재 요청과 처리 현황을 확인할 수 있는 공간입니다. 회사 계정으로 안전하게 로그인해주세요.</p><a className="login-link" href="/oauth2/authorization/approval-client">Passwordless SSO 로그인</a></section></main>
  return <main className="page-shell"><section className="service-card authenticated-card" aria-labelledby="page-title">
    <header className="service-header"><div><p className="eyebrow">SSO LAB · APPROVAL</p><h1 id="page-title">전자결재</h1>
      <p className="summary">통합 인증으로 결재 서비스에 안전하게 접속합니다.</p></div>
      {authenticated && <div className="identity"><span className="status"><span className="status-dot" />SSO authenticated</span><strong>{displayName}</strong><small>{session?.userId}</small>
        {csrf && <form method="post" action="/api/v1/logout"><input type="hidden" name={csrf.parameterName} value={csrf.token} /><button type="submit">로그아웃</button></form>}</div>}
    </header>
    <div className="workspace">
          <section className="panel"><p className="section-kicker">WORKSPACE</p><h2>결재 업무</h2><div className="empty-work"><strong>결재 서비스에 연결되었습니다</strong><p>현재 계정으로 접근할 수 있는 결재 업무를 준비하고 있습니다.</p></div></section>
          <section className="panel access-panel"><p className="section-kicker">SSO SESSION</p><h2>로그인 정보</h2><dl>
            <div><dt>사용자</dt><dd>{displayName}</dd></div><div><dt>로그인 ID</dt><dd>{session?.userId || '—'}</dd></div>
            <div><dt>역할</dt><dd>{session?.roles?.join(', ') || 'USER'}</dd></div><div><dt>인증 방법</dt><dd>{session?.amr?.join(', ') || 'SSO'}</dd></div>
          </dl></section>
          <pre className="sr-only" aria-hidden="true">{JSON.stringify(session, null, 2)}</pre>
        </div>
  </section></main>
}
export default App
