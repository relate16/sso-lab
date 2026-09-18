export type Session = {
  authenticated: boolean
  name?: string
  username?: string
  roles?: string[]
}

export type Csrf = { headerName: string; parameterName: string; token: string }
export type GroupMembership = { id: string; name: string; fullPath: string }
export type User = {
  id: string
  userId: string
  username: string
  maskedEmail: string
  status: 'ACTIVE' | 'SUSPENDED'
  roles: string[]
  groups: GroupMembership[]
  createdAt: string
  updatedAt: string
}
export type Group = {
  id: string
  name: string
  parentId: string | null
  fullPath: string
  memberCount: number
  createdAt: string
  updatedAt: string
}
export type GroupMember = { id: string; userId: string; username: string; status: string }
export type GroupDetail = { group: Group; members: GroupMember[] }
export type Audit = {
  id: string
  event: string
  actorLabel: string
  targetLabel: string
  success: boolean
  source: string
  occurredAt: string
}
export type Page<T> = { content: T[]; page: number; size: number; totalElements: number }
export type Dashboard = {
  totalUsers: number
  activeUsers: number
  suspendedUsers: number
  adminUsers: number
  totalGroups: number
  recentSecurityEvents: Audit[]
}
export type ElevatedStatus = { elevated: boolean; expiresAt?: string }
export type ReauthStart = { challengeId: string; totpAvailable: boolean }
export type BulkResult = { requestedCount: number; succeededCount: number; failedCount: number }
export type ToastKind = 'success' | 'error' | 'warning' | 'info'
