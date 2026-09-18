import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { adminApi } from '../api/admin'
import type { Csrf, ElevatedStatus } from '../types'
import { ReauthDialog } from '../components/security/ReauthDialog'
import { useToast } from './ToastContext'

type SecurityContextValue = ElevatedStatus & {
  remainingSeconds: number
  requestElevated: () => Promise<boolean>
  refreshElevated: () => Promise<void>
}
const SecurityContext = createContext<SecurityContextValue | null>(null)

export function SecurityProvider({ csrf, children }: { csrf: Csrf; children: ReactNode }) {
  const [status, setStatus] = useState<ElevatedStatus>({ elevated: false })
  const [dialogOpen, setDialogOpen] = useState(false)
  const [resolver, setResolver] = useState<((value: boolean) => void) | null>(null)
  const [now, setNow] = useState(Date.now())
  const { showToast } = useToast()

  const refreshElevated = useCallback(async () => {
    try { setStatus(await adminApi.elevatedStatus()) }
    catch { setStatus({ elevated: false }) }
  }, [])
  useEffect(() => { void refreshElevated() }, [refreshElevated])
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(timer)
  }, [])
  const remainingSeconds = status.elevated && status.expiresAt
    ? Math.max(0, Math.ceil((new Date(status.expiresAt).getTime() - now) / 1000)) : 0
  const actuallyElevated = status.elevated && remainingSeconds > 0

  const requestElevated = useCallback(async () => {
    const current: ElevatedStatus = await adminApi.elevatedStatus().catch(() => ({ elevated: false }))
    setStatus(current)
    if (current.elevated && current.expiresAt && new Date(current.expiresAt).getTime() > Date.now()) {
      return true
    }
    setDialogOpen(true)
    return new Promise<boolean>(resolve => setResolver(() => resolve))
  }, [])

  const close = (verified: boolean, next?: ElevatedStatus) => {
    if (next) setStatus(next)
    setDialogOpen(false)
    resolver?.(verified)
    setResolver(null)
    if (verified) showToast('보안 인증이 활성화되었습니다.', 'success')
  }

  const value = useMemo(() => ({
    elevated: actuallyElevated,
    expiresAt: status.expiresAt,
    remainingSeconds,
    requestElevated,
    refreshElevated,
  }), [actuallyElevated, status.expiresAt, remainingSeconds, requestElevated, refreshElevated])

  return <SecurityContext.Provider value={value}>
    {children}
    <ReauthDialog csrf={csrf} open={dialogOpen} onClose={close} />
  </SecurityContext.Provider>
}

export function useSecurity() {
  const value = useContext(SecurityContext)
  if (!value) throw new Error('SecurityProvider is required')
  return value
}
