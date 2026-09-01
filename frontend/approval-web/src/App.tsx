import { useEffect, useState } from 'react'
import { loadBffState, type Csrf, type Session } from './session-api'
function App() {
  const [session, setSession] = useState<Session | null>(null); const [loading, setLoading] = useState(true)
  const [csrf, setCsrf] = useState<Csrf | null>(null)
  useEffect(() => { loadBffState().then(state => {
    setSession(state.session); setCsrf(state.csrf)
  }).finally(() => setLoading(false)) }, [])
  return <main className="page-shell"><section className="service-card" aria-labelledby="page-title">
    <p className="eyebrow">SSO LAB · APPROVAL DEMO</p><h1 id="page-title">Approval OIDC Client</h1>
    <p className="summary">HR과 동일한 loa:1 정책입니다. Auth SSO Session이 있으면 추가 인증 없이 승인 Client Session을 만듭니다.</p>
    {loading ? <p className="notice">세션 확인 중…</p> : session
      ? <><div className="status"><span className="status-dot" />SSO authenticated</div><pre>{JSON.stringify(session, null, 2)}</pre>{csrf && <form method="post" action="/api/v1/logout"><input type="hidden" name={csrf.parameterName} value={csrf.token} /><button type="submit">현재 SSO Session 로그아웃</button></form>}</>
      : <a className="login-link" href="/oauth2/authorization/approval-client">Passwordless SSO 로그인</a>}
  </section></main>
}
export default App
