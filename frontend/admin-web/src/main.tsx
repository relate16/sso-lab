import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { CssBaseline, ThemeProvider } from '@mui/material'
import App from './App'
import { adminTheme } from './theme/theme'
import './styles.css'
createRoot(document.getElementById('root')!).render(<StrictMode><ThemeProvider theme={adminTheme}><CssBaseline /><App /></ThemeProvider></StrictMode>)
