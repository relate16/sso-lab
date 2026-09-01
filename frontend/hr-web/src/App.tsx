import { useEffect, useState } from 'react'
import { loadBffState, type Csrf, type Session } from './session-api'
function App() {
  const [session, setSession] = useState<Session | null>(null); const [loading, setLoading] = useState(true)
  const [csrf, setCsrf] = useState<Csrf | null>(null)
  useEffect(() => { loadBffState().then(state => {
    setSession(state.session); setCsrf(state.csrf)
  }).finally(() => setLoading(false)) }, [])
  return <main className="page-shell"><section className="service-card" aria-labelledby="page-title">
    <p className="eyebrow">SSO LAB · HR DEMO</p><h1 id="page-title">HR OIDC Client</h1>
    <p className="summary">Token은 BFF 서버 세션에서만 관리하며 React에는 검증된 OIDC Claim만 전달합니다.</p>
    {loading ? <p className="notice">세션 확인 중…</p> : session
      ? <><div className="status"><span className="status-dot" />SSO authenticated</div><pre>{JSON.stringify(session, null, 2)}</pre>
        {csrf && <form method="post" action="/api/v1/logout"><input type="hidden" name={csrf.parameterName} value={csrf.token} /><button type="submit">현재 SSO Session 로그아웃</button></form>}</>
      : <a className="login-link" href="/oauth2/authorization/hr-client">Passwordless SSO 로그인</a>}
  </section></main>
}
export default App
