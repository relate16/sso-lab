import { FormEvent, useState } from 'react'
import { QRCodeSVG } from 'qrcode.react'
import {
  confirmTotpEnrollment,
  disableTotp,
  recoveryCodesForClipboard,
  regenerateRecoveryCodes,
  startTotpEnrollment,
  startTotpManagementEmailReauthentication,
  verifyTotpManagementReauthentication,
  type RecoveryCodeBatch,
} from './totp'

type TotpAction = 'ENROLL' | 'REGENERATE' | 'DISABLE'

type Props = {
  enrolled: boolean
  onStatusChanged: () => Promise<void>
}

const actionLabels: Record<TotpAction, string> = {
  ENROLL: 'TOTP 등록',
  REGENERATE: 'Recovery Code 재발급',
  DISABLE: 'TOTP 해제',
}

export default function TotpManagementPanel({ enrolled, onStatusChanged }: Props) {
  const [action, setAction] = useState<TotpAction | null>(null)
  const [reauthMethod, setReauthMethod] = useState<'EMAIL_OTP' | 'TOTP'>('EMAIL_OTP')
  const [reauthChallengeId, setReauthChallengeId] = useState<string | null>(null)
  const [reauthCode, setReauthCode] = useState('')
  const [otpauthUri, setOtpauthUri] = useState('')
  const [enrollmentCode, setEnrollmentCode] = useState('')
  const [recoveryCodes, setRecoveryCodes] = useState<string[]>([])
  const [notice, setNotice] = useState('')
  const [busy, setBusy] = useState(false)
  const [copied, setCopied] = useState(false)

  const clearSensitiveState = () => {
    setReauthChallengeId(null)
    setReauthCode('')
    setOtpauthUri('')
    setEnrollmentCode('')
    setRecoveryCodes([])
    setCopied(false)
  }

  const begin = (nextAction: TotpAction) => {
    clearSensitiveState()
    setAction(nextAction)
    setReauthMethod('EMAIL_OTP')
    setNotice(`${actionLabels[nextAction]} 전에 본인 확인이 필요합니다.`)
  }

  const cancel = () => {
    clearSensitiveState()
    setAction(null)
    setNotice('')
  }

  const run = async (operation: () => Promise<void>) => {
    setBusy(true)
    try {
      await operation()
    } catch {
      setNotice('요청을 처리하지 못했습니다. 입력과 인증 상태를 확인해주세요.')
    } finally {
      setBusy(false)
    }
  }

  const sendReauthenticationEmail = () => void run(async () => {
    const challenge = await startTotpManagementEmailReauthentication()
    setReauthChallengeId(challenge.challengeId)
    setReauthCode('')
    setNotice('현재 이메일로 본인 확인 코드를 보냈습니다.')
  })

  const selectReauthenticationMethod = (method: 'EMAIL_OTP' | 'TOTP') => {
    setReauthMethod(method)
    setReauthChallengeId(null)
    setReauthCode('')
    setNotice('')
  }

  const completeAction = async (completedAction: TotpAction) => {
    if (completedAction === 'ENROLL') {
      const enrollment = await startTotpEnrollment()
      setOtpauthUri(enrollment.otpauthUri)
      setAction(null)
      setNotice('인증 앱에 QR 코드를 등록한 뒤 현재 코드를 확인해주세요.')
      return
    }
    if (completedAction === 'REGENERATE') {
      const batch = await regenerateRecoveryCodes()
      showRecoveryCodes(batch)
      setAction(null)
      return
    }
    await disableTotp()
    clearSensitiveState()
    setAction(null)
    await onStatusChanged()
    setNotice('TOTP를 해제하고 기존 Recovery Code를 모두 폐기했습니다.')
  }

  const verifyReauthentication = (event: FormEvent) => {
    event.preventDefault()
    if (!action) return
    const pendingAction = action
    void run(async () => {
      await verifyTotpManagementReauthentication(
        reauthMethod,
        reauthCode,
        reauthChallengeId,
      )
      setReauthCode('')
      setReauthChallengeId(null)
      await completeAction(pendingAction)
    })
  }

  const confirmEnrollment = (event: FormEvent) => {
    event.preventDefault()
    void run(async () => {
      const batch = await confirmTotpEnrollment(enrollmentCode)
      setEnrollmentCode('')
      setOtpauthUri('')
      showRecoveryCodes(batch)
      await onStatusChanged()
    })
  }

  const showRecoveryCodes = (batch: RecoveryCodeBatch) => {
    setRecoveryCodes([...batch.recoveryCodes])
    setCopied(false)
    setNotice('복구 코드는 다시 표시되지 않습니다. 안전한 곳에 보관하세요.')
  }

  const copyRecoveryCodes = () => void run(async () => {
    await navigator.clipboard.writeText(recoveryCodesForClipboard(recoveryCodes))
    setCopied(true)
    setNotice('Recovery Code를 클립보드에 복사했습니다. 안전한 곳에 옮긴 뒤 클립보드를 비워주세요.')
  })

  const dismissRecoveryCodes = () => {
    setRecoveryCodes([])
    setCopied(false)
    setNotice('Recovery Code 표시를 닫았습니다. 이 코드는 다시 확인할 수 없습니다.')
  }

  return <section className="totp-panel" aria-labelledby="totp-management-title">
    <div className="totp-heading">
      <div>
        <h3 id="totp-management-title">TOTP 인증 앱</h3>
        <p>현재 상태: <strong>{enrolled ? '등록됨' : '미등록'}</strong></p>
      </div>
      {!enrolled && !action && !otpauthUri && recoveryCodes.length === 0
        && <button type="button" disabled={busy} onClick={() => begin('ENROLL')}>TOTP 등록</button>}
      {enrolled && !action && recoveryCodes.length === 0
        && <div className="form-actions">
          <button type="button" disabled={busy} onClick={() => begin('REGENERATE')}>Recovery Code 재발급</button>
          <button type="button" className="danger-button" disabled={busy}
            onClick={() => begin('DISABLE')}>TOTP 해제</button>
        </div>}
    </div>

    {action && <section className="sensitive-action" aria-labelledby="totp-reauth-title">
      <h4 id="totp-reauth-title">{actionLabels[action]} 본인 확인</h4>
      <p>민감한 인증 설정을 변경하기 전에 fresh re-authentication을 완료해주세요.</p>
      {enrolled && <div role="group" aria-label="TOTP 관리 재인증 방법" className="method-tabs">
        <button type="button" className={reauthMethod === 'EMAIL_OTP' ? 'selected' : ''}
          onClick={() => selectReauthenticationMethod('EMAIL_OTP')}>Email OTP</button>
        <button type="button" className={reauthMethod === 'TOTP' ? 'selected' : ''}
          onClick={() => selectReauthenticationMethod('TOTP')}>TOTP</button>
      </div>}
      {reauthMethod === 'EMAIL_OTP' && <button type="button" disabled={busy}
        onClick={sendReauthenticationEmail}>재인증 이메일 코드 보내기</button>}
      <form onSubmit={verifyReauthentication}>
        <label>TOTP 관리 재인증 코드<input inputMode="numeric" autoComplete="one-time-code"
          pattern="[0-9]{6}" maxLength={6} value={reauthCode}
          onChange={event => setReauthCode(event.target.value)} required /></label>
        <div className="form-actions">
          <button type="submit" disabled={busy || !/^[0-9]{6}$/.test(reauthCode)
            || (reauthMethod === 'EMAIL_OTP' && !reauthChallengeId)}>재인증 확인</button>
          <button type="button" disabled={busy} onClick={cancel}>취소</button>
        </div>
      </form>
      {action === 'DISABLE' && <p className="warning">TOTP를 해제하면 기존 Recovery Code도 모두 폐기됩니다.</p>}
    </section>}

    {otpauthUri && <section className="totp-enrollment" aria-labelledby="totp-enrollment-title">
      <h4 id="totp-enrollment-title">인증 앱에 QR 코드를 등록하세요</h4>
      <p>QR을 스캔해도 등록은 끝나지 않습니다. 인증 앱에 표시된 6자리 코드를 반드시 확인해야 합니다.</p>
      <div className="totp-qr" data-testid="totp-qr">
        <QRCodeSVG value={otpauthUri} size={220} level="M" marginSize={4}
          title="TOTP 등록 QR 코드" />
      </div>
      <details>
        <summary>QR을 스캔할 수 없나요?</summary>
        <p>인증 앱에 아래 일회성 등록 URI를 직접 입력하세요. 다른 사람과 공유하지 마세요.</p>
        <code className="sensitive-uri" data-testid="otpauth-uri">{otpauthUri}</code>
      </details>
      <form onSubmit={confirmEnrollment}>
        <label>인증 앱의 6자리 코드<input inputMode="numeric" autoComplete="one-time-code"
          pattern="[0-9]{6}" maxLength={6} value={enrollmentCode}
          onChange={event => setEnrollmentCode(event.target.value)} required /></label>
        <div className="form-actions">
          <button type="submit" disabled={busy || !/^[0-9]{6}$/.test(enrollmentCode)}>TOTP 등록 확인</button>
          <button type="button" disabled={busy} onClick={cancel}>취소</button>
        </div>
      </form>
    </section>}

    {recoveryCodes.length > 0 && <section className="recovery-codes" aria-labelledby="recovery-codes-title">
      <h4 id="recovery-codes-title">Recovery Code</h4>
      <p className="warning">복구 코드는 다시 표시되지 않습니다. 안전한 곳에 보관하세요.</p>
      <ol aria-label="Recovery Code 목록">
        {recoveryCodes.map((code, index) => <li key={index}><code>{code}</code></li>)}
      </ol>
      <div className="form-actions">
        <button type="button" disabled={busy} onClick={copyRecoveryCodes}>{copied ? '복사됨' : '복사'}</button>
        <button type="button" disabled={busy} onClick={dismissRecoveryCodes}>보관 완료하고 닫기</button>
      </div>
    </section>}

    {notice && <p className="notice" role="status">{notice}</p>}
  </section>
}
