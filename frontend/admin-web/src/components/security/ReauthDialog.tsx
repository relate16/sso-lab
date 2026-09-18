import {
  Button, Dialog, DialogActions, DialogContent, DialogTitle, Tab, Tabs, TextField,
  Typography,
} from '@mui/material'
import { FormEvent, useEffect, useState } from 'react'
import { adminApi } from '../../api/admin'
import { userFacingError } from '../../api/client'
import type { Csrf, ElevatedStatus, ReauthStart } from '../../types'
import { useToast } from '../../contexts/ToastContext'

export function ReauthDialog({ csrf, open, onClose }: {
  csrf: Csrf
  open: boolean
  onClose: (verified: boolean, status?: ElevatedStatus) => void
}) {
  const [method, setMethod] = useState<'EMAIL_OTP' | 'TOTP'>('EMAIL_OTP')
  const [start, setStart] = useState<ReauthStart | null>(null)
  const [busy, setBusy] = useState(false)
  const { showToast } = useToast()
  useEffect(() => { if (!open) { setStart(null); setMethod('EMAIL_OTP') } }, [open])

  const sendEmail = async () => {
    setBusy(true)
    try {
      setStart(await adminApi.startReauth(csrf))
      showToast('관리자 이메일로 인증 코드를 보냈습니다.', 'info')
    } catch (error) { showToast(userFacingError(error, '인증 요청에 실패했습니다.'), 'error') }
    finally { setBusy(false) }
  }
  const verify = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    setBusy(true)
    try {
      const status = await adminApi.verifyReauth(
        csrf, method, String(data.get('code')), start?.challengeId,
      )
      onClose(true, status)
    } catch (error) { showToast(userFacingError(error, '인증에 실패했습니다.'), 'error') }
    finally { setBusy(false) }
  }
  return <Dialog open={open} onClose={() => onClose(false)} fullWidth maxWidth="xs">
    <DialogTitle>마스킹 해제 인증</DialogTitle>
    <DialogContent>
      <Typography color="text.secondary" sx={{ mb: 2 }}>
        마스킹된 사용자 정보를 확인하려면 관리자 본인 인증이 필요합니다.
      </Typography>
      <Tabs value={method} onChange={(_, value) => setMethod(value)} aria-label="보안 인증 방식">
        <Tab value="EMAIL_OTP" label="Email OTP" />
        <Tab value="TOTP" label="TOTP" />
      </Tabs>
      {method === 'EMAIL_OTP' && !start
        ? <Button sx={{ mt: 2 }} variant="contained" onClick={() => void sendEmail()} disabled={busy}>
            인증 코드 받기
          </Button>
        : <form onSubmit={verify}>
            <TextField
              autoFocus fullWidth required name="code" label="인증 코드" margin="normal"
              slotProps={{ htmlInput: { inputMode: 'numeric', minLength: 6, maxLength: method === 'TOTP' ? 6 : 10 } }}
              autoComplete="one-time-code"
            />
            <DialogActions sx={{ px: 0 }}>
              <Button onClick={() => onClose(false)}>취소</Button>
              <Button type="submit" variant="contained" disabled={busy}>확인</Button>
            </DialogActions>
          </form>}
    </DialogContent>
  </Dialog>
}
