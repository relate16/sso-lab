import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import createCache from '@emotion/cache'
import { CacheProvider } from '@emotion/react'
import { CssBaseline, ThemeProvider } from '@mui/material'
import App from './App'
import { adminTheme } from './theme/theme'
import './styles.css'

const configuredNonce = document.querySelector<HTMLMetaElement>('meta[name="csp-nonce"]')?.content
const nonce = configuredNonce && configuredNonce !== '__CSP_NONCE__' ? configuredNonce : undefined
const emotionCache = createCache({ key: 'sso-admin', nonce })

createRoot(document.getElementById('root')!).render(
  <StrictMode><CacheProvider value={emotionCache}><ThemeProvider theme={adminTheme}><CssBaseline /><App /></ThemeProvider></CacheProvider></StrictMode>,
)
