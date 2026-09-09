import { FormEvent, useCallback, useEffect, useState } from 'react'
import { adminJson, adminMutation, type Csrf } from './api'

type Session = { authenticated: boolean; name?: string; username?: string; roles?: string[]; acr?: string }
type Group = { id: string; name: string; parentId: string | null; fullPath: string; memberCount: number }
type User = { id: string; userId: string; username: string; maskedEmail: string; status: string; roles: string[]; groups: Array<{ id: string; name: string; fullPath: string }> }
type Audit = { id: string; event: string; actorId: string; targetId?: string; success: boolean; occurredAt: string }
type Page<T> = { content: T[]; totalElements: number }
type ReauthStart = { challengeId: string; totpAvailable: boolean }
type ElevatedStatus = { elevated: boolean; expiresAt?: string }

function App() {
  const [session, setSession] = useState<Session | null>(null)
  const [users, setUsers] = useState<User[]>([])
  const [groups, setGroups] = useState<Group[]>([])
  const [audits, setAudits] = useState<Audit[]>([])
  const [selected, setSelected] = useState<User | null>(null)
  const [csrf, setCsrf] = useState<Csrf | null>(null)
  const [reauth, setReauth] = useState<ReauthStart | null>(null)
  const [elevated, setElevated] = useState<ElevatedStatus>({ elevated: false })
  const [revealedEmail, setRevealedEmail] = useState<string | null>(null)
  const [notice, setNotice] = useState('')
  const [loading, setLoading] = useState(true)

  const refresh = useCallback(async () => {
    const [userPage, groupList, auditPage] = await Promise.all([
      adminJson<Page<User>>('/api/v1/admin/users'), adminJson<Group[]>('/api/v1/admin/groups'),
      adminJson<Page<Audit>>('/api/v1/admin/audit-logs')
    ])
    setUsers(userPage.content); setGroups(groupList); setAudits(auditPage.content)
    setSelected(current => current && userPage.content.find(user => user.id === current.id) || null)
  }, [])

  useEffect(() => {
    Promise.all([adminJson<Session>('/api/v1/session'), adminJson<Csrf>('/api/v1/csrf'), adminJson<ElevatedStatus>('/api/v1/admin/reauth/status')])
      .then(async ([activeSession, csrfToken, status]) => {
        if (!activeSession.authenticated) { setSession(null); return }
        setSession(activeSession); setCsrf(csrfToken); setElevated(status); await refresh()
      })
      .catch(() => setSession(null)).finally(() => setLoading(false))
  }, [refresh])

  const mutate = async <T,>(url: string, method: string, body?: unknown): Promise<T> => {
    return adminMutation<T>(csrf, url, method, body)
  }
  const act = async (label: string, action: () => Promise<unknown>) => {
    try { await action(); setNotice(`${label} 완료`); await refresh() }
    catch (error) { setNotice(`${label} 실패: ${error instanceof Error ? error.message : 'unknown error'}`) }
  }
  const submitReauth = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault(); const formElement = event.currentTarget; const form = new FormData(formElement)
    const method = String(form.get('method')); const code = String(form.get('code'))
    await act('재인증', async () => {
      const status = await mutate<ElevatedStatus>('/api/v1/admin/reauth/verify', 'POST', { method, challengeId: method === 'EMAIL_OTP' ? reauth?.challengeId : null, code })
      setElevated(status); setReauth(null); formElement.reset()
    })
  }

  if (loading) return <main className="center"><p>관리자 세션 확인 중…</p></main>
  if (!session?.authenticated) return <main className="center"><section className="login-card"><span className="login-mark" aria-hidden="true">AD</span><p className="eyebrow">관리자 전용</p><h1>관리자 페이지</h1><p>사용자 계정과 조직, 권한을 관리하는 공간입니다. 관리자 권한이 있는 계정으로 안전하게 로그인해주세요.</p><a className="primary" href="/oauth2/authorization/admin-client">Passwordless SSO 로그인</a></section></main>

  return <main className="app-shell">
    <header className="app-header"><div><p className="eyebrow">SSO LAB · ADMIN</p><h1>Identity control plane</h1><p className="header-description">사용자, 역할, 조직과 보안 이벤트를 관리합니다.</p></div><div className="identity"><span className="status"><span className="status-dot" />관리자 로그인됨</span><strong>{session.name || session.username}</strong><span>ADMIN · {session.acr}</span>{csrf && <form method="post" action="/api/v1/logout"><input type="hidden" name={csrf.parameterName} value={csrf.token} /><button type="submit" className="secondary">로그아웃</button></form>}</div></header>
    {notice && <div className="notice" role="status">{notice}</div>}
    <section className="metrics"><article><span>사용자</span><strong>{users.length}</strong></article><article><span>그룹</span><strong>{groups.length}</strong></article><article><span>재인증</span><strong>{elevated.elevated ? '활성' : '필요'}</strong></article></section>
    <div className="workspace">
      <section className="panel users"><div className="panel-title"><div><p className="section-kicker">IDENTITY</p><h2>사용자</h2></div><button className="secondary" onClick={() => refresh()}>새로고침</button></div><div className="user-list">{users.map(user => <button className={selected?.id === user.id ? 'user-row selected' : 'user-row'} key={user.id} onClick={() => { setSelected(user); setRevealedEmail(null) }}><span><strong>{user.username}</strong><small>{user.maskedEmail}</small></span><em className={user.status.toLowerCase()}>{user.status}</em></button>)}</div></section>
      <section className="panel detail"><p className="section-kicker">USER DETAIL</p><h2>사용자 상세</h2>{selected ? <><dl><div><dt>User ID</dt><dd>{selected.userId}</dd></div><div><dt>Email</dt><dd>{revealedEmail || selected.maskedEmail}</dd></div><div><dt>Roles</dt><dd>{selected.roles.join(', ')}</dd></div><div><dt>Groups</dt><dd>{selected.groups.map(group => group.fullPath).join(', ') || '—'}</dd></div></dl><div className="actions"><button onClick={() => act(selected.status === 'ACTIVE' ? '사용자 정지' : '사용자 활성화', () => mutate(`/api/v1/admin/users/${selected.id}/${selected.status === 'ACTIVE' ? 'suspend' : 'resume'}`, 'POST'))}>{selected.status === 'ACTIVE' ? 'Suspend' : 'Resume'}</button><button onClick={() => act('ADMIN 역할 변경', () => mutate(`/api/v1/admin/users/${selected.id}/roles`, 'PUT', { roles: selected.roles.includes('ADMIN') ? ['USER'] : ['USER', 'ADMIN'] }))}>{selected.roles.includes('ADMIN') ? 'ADMIN 제거' : 'ADMIN 부여'}</button><button disabled={!elevated.elevated} onClick={() => act('이메일 조회', async () => { const result = await mutate<{ email: string }>(`/api/v1/admin/users/${selected.id}/email/reveal`, 'POST'); setRevealedEmail(result.email) })}>Email reveal</button></div><UserGroupForm key={`${selected.id}-${selected.groups.map(group => group.id).join('-')}`} user={selected} groups={groups} submit={groupIds => act('사용자 그룹 변경', () => mutate(`/api/v1/admin/users/${selected.id}/groups`, 'PUT', { groupIds }))} /></> : <p className="empty">왼쪽 목록에서 사용자를 선택하세요.</p>}</section>
      <section className="panel groups"><p className="section-kicker">ORGANIZATION</p><h2>그룹 구조</h2><ul>{groups.map(group => <li key={group.id}><span>{group.fullPath}<small>{group.memberCount} members</small></span><span className="mini-actions"><button aria-label={`${group.name} 이름 변경`} onClick={() => { const name = window.prompt('새 그룹 이름', group.name); if (name) void act('그룹 이름 변경', () => mutate(`/api/v1/admin/groups/${group.id}`, 'PATCH', { name })) }}>Rename</button><button aria-label={`${group.name} 이동`} onClick={() => { const parentId = window.prompt('상위 그룹 UUID (Root는 비움)', group.parentId || ''); if (parentId !== null) void act('그룹 이동', () => mutate(`/api/v1/admin/groups/${group.id}/move`, 'POST', { parentId: parentId || null })) }}>Move</button><button aria-label={`${group.name} 삭제`} onClick={() => { if (window.confirm(`${group.fullPath} 그룹을 삭제할까요?`)) void act('그룹 삭제', () => mutate(`/api/v1/admin/groups/${group.id}`, 'DELETE')) }}>Delete</button></span></li>)}</ul><GroupForm groups={groups} submit={(name, parentId) => act('그룹 생성', () => mutate('/api/v1/admin/groups', 'POST', { name, parentId: parentId || null }))} /></section>
      <section className="panel reauth"><p className="section-kicker">ELEVATED ACCESS</p><h2>관리자 재인증</h2><p>복호화된 이메일 조회는 서버 측 5분 elevated session이 필요합니다.</p>{!reauth && <button onClick={() => act('Email OTP 발급', async () => setReauth(await mutate('/api/v1/admin/reauth/email/start', 'POST')))}>Email OTP 발급</button>}{reauth && <form onSubmit={submitReauth}><input type="hidden" name="method" value="EMAIL_OTP" /><label>OTP<input name="code" inputMode="numeric" minLength={6} maxLength={10} required autoComplete="one-time-code" /></label><button type="submit">검증</button></form>}<form onSubmit={submitReauth}><input type="hidden" name="method" value="TOTP" /><label>TOTP<input name="code" inputMode="numeric" minLength={6} maxLength={6} required autoComplete="one-time-code" /></label><button type="submit">TOTP 검증</button></form></section>
      <section className="panel audit"><p className="section-kicker">SECURITY LOG</p><h2>관리자 감사 기록</h2><div className="audit-list">{audits.map(item => <article key={item.id}><span className={item.success ? 'ok' : 'failed'}>{item.event}</span><small>{new Date(item.occurredAt).toLocaleString()} · actor {item.actorId.slice(0, 8)}{item.targetId ? ` → ${item.targetId.slice(0, 8)}` : ''}</small></article>)}</div></section>
    </div>
  </main>
}

function GroupForm({ groups, submit }: { groups: Group[]; submit: (name: string, parentId: string) => Promise<void> }) {
  const onSubmit = async (event: FormEvent<HTMLFormElement>) => { event.preventDefault(); const formElement = event.currentTarget; const form = new FormData(formElement); await submit(String(form.get('name')), String(form.get('parentId'))); formElement.reset() }
  return <form className="group-form" onSubmit={onSubmit}><input name="name" maxLength={100} placeholder="새 그룹 이름" required /><select name="parentId"><option value="">Root</option>{groups.map(group => <option key={group.id} value={group.id}>{group.fullPath}</option>)}</select><button type="submit">추가</button></form>
}

function UserGroupForm({ user, groups, submit }: { user: User; groups: Group[]; submit: (groupIds: string[]) => Promise<void> }) {
  const onSubmit = async (event: FormEvent<HTMLFormElement>) => { event.preventDefault(); const control = event.currentTarget.elements.namedItem('groupIds') as HTMLSelectElement; const selected = Array.from(control.selectedOptions, option => option.value); await submit(selected) }
  return <form className="membership-form" onSubmit={onSubmit}><label>그룹 멤버십<select name="groupIds" multiple defaultValue={user.groups.map(group => group.id)}>{groups.map(group => <option key={group.id} value={group.id}>{group.fullPath}</option>)}</select></label><button type="submit">그룹 적용</button></form>
}

export default App
