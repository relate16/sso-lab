import { Alert, Snackbar } from '@mui/material'
import { createContext, useCallback, useContext, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import type { ToastKind } from '../types'

type Toast = { message: string; kind: ToastKind } | null
type ToastContextValue = { showToast: (message: string, kind?: ToastKind) => void }
const ToastContext = createContext<ToastContextValue | null>(null)

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toast, setToast] = useState<Toast>(null)
  const showToast = useCallback((message: string, kind: ToastKind = 'info') => {
    setToast({ message, kind })
  }, [])
  const value = useMemo(() => ({ showToast }), [showToast])
  return <ToastContext.Provider value={value}>
    {children}
    <Snackbar
      open={Boolean(toast)}
      autoHideDuration={4200}
      onClose={() => setToast(null)}
      anchorOrigin={{ vertical: 'top', horizontal: 'center' }}
    >
      <Alert severity={toast?.kind ?? 'info'} variant="filled" onClose={() => setToast(null)}>
        {toast?.message}
      </Alert>
    </Snackbar>
  </ToastContext.Provider>
}

export function useToast() {
  const value = useContext(ToastContext)
  if (!value) throw new Error('ToastProvider is required')
  return value
}
