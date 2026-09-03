import { useEffect, useRef, useState } from 'react'

declare global {
  interface Window {
    ssoLabRuntimeConfig?: {
      turnstileEnabled?: boolean
      turnstileSiteKey?: string
    }
    turnstile?: {
      render: (container: HTMLElement, options: {
        sitekey: string
        callback: (token: string) => void
        'expired-callback': () => void
        'error-callback': () => void
      }) => string
      remove: (widgetId: string) => void
    }
  }
}

export const turnstileEnabled = window.ssoLabRuntimeConfig?.turnstileEnabled
  ?? import.meta.env.VITE_TURNSTILE_ENABLED === 'true'

const turnstileSiteKey = window.ssoLabRuntimeConfig?.turnstileSiteKey
  ?? import.meta.env.VITE_TURNSTILE_SITE_KEY
  ?? ''

let turnstileScriptPromise: Promise<void> | undefined

function loadTurnstileScript(): Promise<void> {
  if (window.turnstile) return Promise.resolve()
  if (turnstileScriptPromise) return turnstileScriptPromise

  turnstileScriptPromise = new Promise((resolve, reject) => {
    const existing = document.querySelector<HTMLScriptElement>('script[data-sso-lab-turnstile]')
    const script = existing ?? document.createElement('script')
    const loaded = () => window.turnstile ? resolve() : reject(new Error('Turnstile unavailable'))
    const failed = () => reject(new Error('Turnstile unavailable'))

    script.addEventListener('load', loaded, { once: true })
    script.addEventListener('error', failed, { once: true })
    if (!existing) {
      script.src = 'https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit'
      script.async = true
      script.defer = true
      script.dataset.ssoLabTurnstile = 'true'
      document.head.appendChild(script)
    }
  })
  return turnstileScriptPromise
}

type TurnstileProps = {
  onToken: (token: string) => void
  resetKey: number
}

export default function Turnstile({ onToken, resetKey }: TurnstileProps) {
  const container = useRef<HTMLDivElement>(null)
  const [unavailable, setUnavailable] = useState(false)

  useEffect(() => {
    if (!turnstileEnabled || !turnstileSiteKey) return
    let cancelled = false
    let widgetId: string | undefined
    setUnavailable(false)
    onToken('')

    void loadTurnstileScript()
      .then(() => {
        if (cancelled || !container.current || !window.turnstile) return
        widgetId = window.turnstile.render(container.current, {
          sitekey: turnstileSiteKey,
          callback: onToken,
          'expired-callback': () => onToken(''),
          'error-callback': () => {
            onToken('')
            setUnavailable(true)
          },
        })
      })
      .catch(() => {
        if (!cancelled) setUnavailable(true)
      })

    return () => {
      cancelled = true
      if (widgetId && window.turnstile) window.turnstile.remove(widgetId)
    }
  }, [onToken, resetKey])

  if (!turnstileEnabled) return null
  if (!turnstileSiteKey) return <p role="alert">Turnstile Site Key 설정이 필요합니다.</p>
  return <>
    <div ref={container} />
    {unavailable && <p role="alert">보안 확인을 불러오지 못했습니다. 잠시 후 다시 시도해주세요.</p>}
  </>
}
