export function MarsLogo({ size = 34 }: { size?: number }) {
  return <svg
    width={size} height={size} viewBox="0 0 48 48" fill="none"
    role="img" aria-label="SSO Lab Admin"
  >
    <circle cx="24" cy="24" r="12" fill="currentColor" opacity=".95" />
    <path d="M10 29.5c4.8 6.1 19.2 7.6 28.2 1.4 5.5-3.8 5.2-8.8-1.1-11.2-7.3-2.8-20.3-.2-27 5.1-4.5 3.5-3.5 7.2 2.2 8.4" stroke="currentColor" strokeWidth="2" strokeLinecap="round" opacity=".55" />
    <circle cx="20" cy="20" r="2.1" fill="white" opacity=".55" />
    <path d="m31.5 12.5 4-4m0 0h-3.2m3.2 0v3.2" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
  </svg>
}
