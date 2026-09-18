import { IconButton, Tooltip } from '@mui/material'
import { Eye, EyeOff } from 'lucide-react'
import { useEffect, useState } from 'react'
import { adminApi } from '../../api/admin'
import { userFacingError } from '../../api/client'
import { useSecurity } from '../../contexts/SecurityContext'
import { useToast } from '../../contexts/ToastContext'
import type { Csrf } from '../../types'

export function SensitiveEmail({ csrf, userId, masked }: {
  csrf: Csrf; userId: string; masked: string
}) {
  const [revealed, setRevealed] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const security = useSecurity()
  const { showToast } = useToast()

  useEffect(() => {
    let active = true
    if (!security.elevated) {
      setRevealed(null)
      return () => { active = false }
    }
    setRevealed(null)
    setBusy(true)
    void adminApi.revealEmail(csrf, userId)
      .then(result => { if (active) setRevealed(result.email) })
      .catch(() => { if (active) setRevealed(null) })
      .finally(() => { if (active) setBusy(false) })
    return () => { active = false }
  }, [csrf, security.elevated, userId])

  const toggle = async () => {
    if (revealed) { setRevealed(null); return }
    setBusy(true)
    try {
      if (!await security.requestElevated()) return
      setRevealed((await adminApi.revealEmail(csrf, userId)).email)
    } catch (error) { showToast(userFacingError(error, '이메일을 불러오지 못했습니다.'), 'error') }
    finally { setBusy(false) }
  }
  return <span className="sensitive-value"><span>{revealed ?? masked}</span><Tooltip title={revealed ? '이메일 다시 가리기' : '이메일 마스킹 해제'}><span><IconButton size="small" disabled={busy} onClick={() => void toggle()} aria-label={revealed ? '이메일 다시 가리기' : '이메일 마스킹 해제'}>{revealed ? <Eye size={17} /> : <EyeOff size={17} />}</IconButton></span></Tooltip></span>
}
