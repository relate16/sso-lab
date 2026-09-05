import { csrfMutation, type Fetcher } from './api.ts'

export type TotpEnrollment = {
  otpauthUri: string
}

export type RecoveryCodeBatch = {
  recoveryCodes: string[]
}

export type ReauthenticationResult = {
  reauthenticated: boolean
  expiresAt: string
}

export type OtpRequest = {
  challengeId: string
  expiresAt: string
  resendAvailableAt: string
}

export function startTotpEnrollment(fetcher: Fetcher = fetch): Promise<TotpEnrollment> {
  return csrfMutation('/api/v1/me/totp/enroll/start', 'POST', undefined, fetcher)
}

export function confirmTotpEnrollment(
  code: string,
  fetcher: Fetcher = fetch,
): Promise<RecoveryCodeBatch> {
  return csrfMutation('/api/v1/me/totp/enroll/confirm', 'POST', { code }, fetcher)
}

export function regenerateRecoveryCodes(fetcher: Fetcher = fetch): Promise<RecoveryCodeBatch> {
  return csrfMutation('/api/v1/me/recovery-codes/regenerate', 'POST', undefined, fetcher)
}

export function disableTotp(fetcher: Fetcher = fetch): Promise<void> {
  return csrfMutation('/api/v1/me/totp', 'DELETE', undefined, fetcher)
}

export function startTotpManagementEmailReauthentication(
  fetcher: Fetcher = fetch,
): Promise<OtpRequest> {
  return csrfMutation('/api/v1/me/reauth/email/start', 'POST', undefined, fetcher)
}

export function verifyTotpManagementReauthentication(
  method: 'EMAIL_OTP' | 'TOTP',
  code: string,
  challengeId: string | null,
  fetcher: Fetcher = fetch,
): Promise<ReauthenticationResult> {
  return csrfMutation('/api/v1/me/reauth/verify', 'POST', {
    method,
    challengeId: method === 'EMAIL_OTP' ? challengeId : null,
    code,
  }, fetcher)
}

export function recoveryCodesForClipboard(codes: string[]): string {
  return codes.join('\n')
}
