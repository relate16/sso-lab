import { Box, CircularProgress, Typography } from '@mui/material'
import { LogIn, ShieldCheck } from 'lucide-react'
import { useEffect, useState } from 'react'
import { BrowserRouter } from 'react-router-dom'
import { adminApi } from './api/admin'
import { AdminRouter } from './app/router'
import { MarsLogo } from './components/layout/MarsLogo'
import { SecurityProvider } from './contexts/SecurityContext'
import { ToastProvider } from './contexts/ToastContext'
import type { Csrf, Session } from './types'

function App() {
  const [session, setSession] = useState<Session | null>(null)
  const [csrf, setCsrf] = useState<Csrf | null>(null)
  const [loading, setLoading] = useState(true)
  useEffect(() => {
    Promise.all([adminApi.session(), adminApi.csrf()])
      .then(([activeSession, csrfToken]) => {
        setSession(activeSession.authenticated ? activeSession : null)
        setCsrf(csrfToken)
      })
      .catch(() => setSession(null))
      .finally(() => setLoading(false))
  }, [])

  if (loading) return <Box className="full-center"><CircularProgress size={28} /><Typography color="text.secondary">관리자 세션을 확인하고 있습니다.</Typography></Box>
  if (!session?.authenticated || !csrf) return <LoginPage />

  return <ToastProvider><SecurityProvider csrf={csrf}><BrowserRouter><AdminRouter session={session} csrf={csrf} /></BrowserRouter></SecurityProvider></ToastProvider>
}

function LoginPage() {
  return <main className="login-page"><section className="enterprise-login"><div className="login-brand"><span className="login-logo"><MarsLogo size={46} /></span><div><strong>SSO Lab Admin</strong><span>Enterprise SSO Management</span></div></div><div className="login-copy"><span className="login-label"><ShieldCheck size={16} />관리자 전용 보안 포털</span><Typography variant="h3" component="h1">SSO Lab 관리자 포털</Typography><Typography color="text.secondary">조직의 사용자와 접근 권한을 관리합니다.</Typography><a className="admin-login-link" href="/oauth2/authorization/admin-client">Passwordless SSO 로그인<LogIn size={18} aria-hidden="true" /></a></div><div className="login-assurance"><span>OIDC Authorization Code + PKCE</span><span>서버 세션 기반 토큰 보호</span><span>중요 작업 재인증</span></div></section></main>
}

export default App
