import { csrfMutation, type Fetcher } from './api.ts'

export type SignupStartInput = {
  userId: string
  username: string
  email: string
  turnstileToken: string
}

export type SignupChallenge = {
  challengeId: string
  expiresAt: string
  resendAvailableAt: string
  message: string
}

export function requestSignupOtp(input: SignupStartInput, fetcher: Fetcher = fetch) {
  return csrfMutation<SignupChallenge>('/api/v1/signup/start', 'POST', input, fetcher)
}

export function resendSignupOtp(
  challengeId: string,
  turnstileToken: string,
  fetcher: Fetcher = fetch,
) {
  return csrfMutation<SignupChallenge>('/api/v1/signup/resend', 'POST', {
    challengeId,
    turnstileToken,
  }, fetcher)
}

export function verifySignupOtp(challengeId: string, code: string, fetcher: Fetcher = fetch) {
  return csrfMutation<{ registered: boolean }>('/api/v1/signup/verify', 'POST', {
    challengeId,
    code,
  }, fetcher)
}
