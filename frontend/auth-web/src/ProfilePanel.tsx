import { FormEvent, useCallback, useEffect, useState } from 'react'
import { csrfMutation, readJson } from './api'

type Session = {
  id: string
  device: string
  authenticationMethod: string
  loginAt: string
  lastActivityAt: string
  expiresAt: string
  current: boolean
}
type Profile = {
  userId: string
  username: string
  email: string
  roles: string[]
  groups: string[]
  totpEnrolled: boolean
  sessions: Session[]
}
type OtpRequest = { challengeId: string; expiresAt: string; resendAvailableAt: string }
type ReauthResponse = { reauthenticated: boolean; expiresAt: string }

export default function ProfilePanel() {
  const [profile, setProfile] = useState<Profile | null>(null)
  const [username, setUsername] = useState('')
  const [newEmail, setNewEmail] = useState('')
  const [emailChallenge, setEmailChallenge] = useState<OtpRequest | null>(null)
  const [emailCode, setEmailCode] = useState('')
  const [reauthMethod, setReauthMethod] = useState<'EMAIL_OTP' | 'TOTP'>('EMAIL_OTP')
  const [reauthChallenge, setReauthChallenge] = useState<OtpRequest | null>(null)
  const [reauthCode, setReauthCode] = useState('')
  const [reauthenticatedUntil, setReauthenticatedUntil] = useState<string | null>(null)
  const [confirmation, setConfirmation] = useState('')
  const [notice, setNotice] = useState('')
  const [busy, setBusy] = useState(false)

  const load = useCallback(async () => {
    try {
      const next = await readJson<Profile>('/api/v1/me/profile')
      setProfile(next)
      setUsername(next.username)
    } catch {
      setProfile(null)
    }
  }, [])

  useEffect(() => { void load() }, [load])

  if (!profile) return null

  const run = async (action: () => Promise<void>) => {
    setBusy(true)
    try { await action() }
    catch (error) { setNotice(error instanceof Error ? error.message : '요청에 실패했습니다.') }
    finally { setBusy(false) }
  }

  const changeUsername = (event: FormEvent) => {
    event.preventDefault()
    void run(async () => {
      const updated = await csrfMutation<Profile>('/api/v1/me/profile/username', 'PATCH', { username })
      setProfile(updated)
      setNotice('username을 변경했습니다. 현재 Session은 유지됩니다.')
    })
  }

  const startEmailChange = (event: FormEvent) => {
    event.preventDefault()
    void run(async () => {
      const challenge = await csrfMutation<OtpRequest>('/api/v1/me/email-change/start', 'POST', { newEmail })
      setEmailChallenge(challenge)
      setEmailCode('')
      setNotice('새 이메일로 인증 코드를 보냈습니다.')
    })
  }

  const verifyEmailChange = (event: FormEvent) => {
    event.preventDefault()
    if (!emailChallenge) return
    void run(async () => {
      await csrfMutation('/api/v1/me/email-change/verify', 'POST', {
        challengeId: emailChallenge.challengeId,
        code: emailCode,
      })
      setEmailCode('')
      setEmailChallenge(null)
      setNewEmail('')
      await load()
      setNotice('이메일을 변경하고 다른 Auth/BFF Session과 Refresh Token을 폐기했습니다.')
    })
  }

  const startReauthEmail = () => void run(async () => {
    const challenge = await csrfMutation<OtpRequest>('/api/v1/me/reauth/email/start', 'POST')
    setReauthChallenge(challenge)
    setReauthCode('')
    setNotice('현재 이메일로 탈퇴 재인증 코드를 보냈습니다.')
  })

  const verifyReauth = (event: FormEvent) => {
    event.preventDefault()
    void run(async () => {
      const result = await csrfMutation<ReauthResponse>('/api/v1/me/reauth/verify', 'POST', {
        method: reauthMethod,
        challengeId: reauthMethod === 'EMAIL_OTP' ? reauthChallenge?.challengeId : null,
        code: reauthCode,
      })
      setReauthCode('')
      setReauthenticatedUntil(result.expiresAt)
      setNotice('탈퇴를 위한 fresh re-authentication을 완료했습니다.')
    })
  }

  const deleteAccount = () => void run(async () => {
    await csrfMutation('/api/v1/me/account', 'DELETE', { confirmation })
    setConfirmation('')
    window.location.assign('/')
  })

  return <section aria-labelledby="profile-title">
    <h2 id="profile-title">내 정보</h2>
    <dl>
      <div><dt>User ID</dt><dd>{profile.userId}</dd></div>
      <div><dt>Email</dt><dd>{profile.email}</dd></div>
      <div><dt>Roles</dt><dd>{profile.roles.join(', ')}</dd></div>
      <div><dt>Groups</dt><dd>{profile.groups.length ? profile.groups.join(', ') : '할당된 그룹 없음'}</dd></div>
      <div><dt>TOTP</dt><dd>{profile.totpEnrolled ? '등록됨' : '미등록'}</dd></div>
    </dl>

    <form onSubmit={changeUsername}>
      <label>Username<input value={username} maxLength={100}
        onChange={event => setUsername(event.target.value)} required /></label>
      <button type="submit" disabled={busy || !username}>username 변경</button>
    </form>

    <form onSubmit={startEmailChange}>
      <label>새 Email<input type="email" value={newEmail} maxLength={320}
        onChange={event => setNewEmail(event.target.value)} required /></label>
      <button type="submit" disabled={busy || !newEmail}>새 이메일 인증 시작</button>
    </form>
    {emailChallenge && <form onSubmit={verifyEmailChange}>
      <label>새 Email OTP<input inputMode="numeric" autoComplete="one-time-code"
        value={emailCode} onChange={event => setEmailCode(event.target.value)} required /></label>
      <button type="submit" disabled={busy || !emailCode}>이메일 변경 확인</button>
    </form>}

    <section aria-labelledby="delete-title">
      <h3 id="delete-title">회원탈퇴</h3>
      <p>회원탈퇴는 되돌릴 수 없습니다. Backend fresh re-authentication과 CSRF 검증이 반드시 필요합니다.</p>
      <div role="group" aria-label="탈퇴 재인증 방법">
        <button type="button" onClick={() => setReauthMethod('EMAIL_OTP')}>Email OTP</button>
        {profile.totpEnrolled && <button type="button" onClick={() => setReauthMethod('TOTP')}>TOTP</button>}
      </div>
      {reauthMethod === 'EMAIL_OTP' && <button type="button" disabled={busy}
        onClick={startReauthEmail}>탈퇴 재인증 OTP 보내기</button>}
      <form onSubmit={verifyReauth}>
        <label>{reauthMethod} 코드<input inputMode="numeric" autoComplete="one-time-code"
          value={reauthCode} onChange={event => setReauthCode(event.target.value)} required /></label>
        <button type="submit" disabled={busy || !reauthCode
          || (reauthMethod === 'EMAIL_OTP' && !reauthChallenge)}>fresh re-authentication</button>
      </form>
      {reauthenticatedUntil && <p>재인증 유효 시각: {new Date(reauthenticatedUntil).toLocaleString()}</p>}
      <label>탈퇴하려면 DELETE 입력<input value={confirmation}
        onChange={event => setConfirmation(event.target.value)} /></label>
      <button type="button" disabled={busy || confirmation !== 'DELETE' || !reauthenticatedUntil}
        onClick={deleteAccount}>계정 영구 삭제</button>
    </section>
    {notice && <p className="notice" role="status">{notice}</p>}
  </section>
}
