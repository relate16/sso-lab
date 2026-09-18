import { createTheme } from '@mui/material/styles'
import { tokens } from './tokens'

export const adminTheme = createTheme({
  palette: {
    mode: 'light',
    primary: { main: tokens.petrol, dark: tokens.petrolDark, light: tokens.petrolSoft },
    background: { default: tokens.canvas, paper: tokens.surface },
    text: { primary: tokens.text, secondary: tokens.muted },
    divider: tokens.border,
    error: { main: tokens.danger },
    warning: { main: tokens.warning },
    success: { main: tokens.success },
  },
  shape: { borderRadius: 12 },
  typography: {
    fontFamily: 'Inter, Pretendard, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif',
    h4: { fontWeight: 750, letterSpacing: '-0.035em' },
    h5: { fontWeight: 720, letterSpacing: '-0.025em' },
    h6: { fontWeight: 700 },
    button: { textTransform: 'none', fontWeight: 700 },
  },
  components: {
    MuiButton: { styleOverrides: { root: { boxShadow: 'none' } } },
    MuiPaper: { styleOverrides: { root: { backgroundImage: 'none' } } },
    MuiTableCell: { styleOverrides: { head: { fontWeight: 750, color: tokens.muted } } },
  },
})
