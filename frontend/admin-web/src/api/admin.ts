import { adminJson, adminMutation, queryString } from './client.ts'
import type {
  Audit, BulkResult, Csrf, Dashboard, ElevatedStatus, Group, GroupDetail,
  Page, ReauthStart, Session, User,
} from '../types.ts'

export type UserQuery = {
  q?: string; status?: string; role?: string; groupId?: string
  page?: number; size?: number; sort?: string; direction?: string
}
export type AuditQuery = {
  event?: string; success?: boolean; from?: string; to?: string
  page?: number; size?: number; sort?: string; direction?: string
}

export const adminApi = {
  session: () => adminJson<Session>('/api/v1/session'),
  csrf: () => adminJson<Csrf>('/api/v1/csrf'),
  dashboard: () => adminJson<Dashboard>('/api/v1/admin/dashboard'),
  users: (query: UserQuery = {}) =>
    adminJson<Page<User>>(`/api/v1/admin/users${queryString(query)}`),
  user: (id: string) => adminJson<User>(`/api/v1/admin/users/${id}`),
  groups: () => adminJson<Group[]>('/api/v1/admin/groups'),
  group: (id: string) => adminJson<GroupDetail>(`/api/v1/admin/groups/${id}`),
  audits: (query: AuditQuery = {}) =>
    adminJson<Page<Audit>>(`/api/v1/admin/audit-logs${queryString(query)}`),
  elevatedStatus: () => adminJson<ElevatedStatus>('/api/v1/admin/reauth/status'),
  suspend: (csrf: Csrf | null, id: string) =>
    adminMutation<void>(csrf, `/api/v1/admin/users/${id}/suspend`, 'POST'),
  resume: (csrf: Csrf | null, id: string) =>
    adminMutation<void>(csrf, `/api/v1/admin/users/${id}/resume`, 'POST'),
  roles: (csrf: Csrf | null, id: string, roles: string[]) =>
    adminMutation<void>(csrf, `/api/v1/admin/users/${id}/roles`, 'PUT', { roles }),
  groupsForUser: (csrf: Csrf | null, id: string, groupIds: string[]) =>
    adminMutation<void>(csrf, `/api/v1/admin/users/${id}/groups`, 'PUT', { groupIds }),
  bulkStatus: (csrf: Csrf | null, userIds: string[], status: string) =>
    adminMutation<BulkResult>(csrf, '/api/v1/admin/users/bulk/status', 'POST', { userIds, status }),
  bulkRoles: (csrf: Csrf | null, userIds: string[], roles: string[]) =>
    adminMutation<BulkResult>(csrf, '/api/v1/admin/users/bulk/roles', 'PUT', { userIds, roles }),
  createGroup: (csrf: Csrf | null, name: string, parentId: string | null) =>
    adminMutation<{ id: string }>(csrf, '/api/v1/admin/groups', 'POST', { name, parentId }),
  renameGroup: (csrf: Csrf | null, id: string, name: string) =>
    adminMutation<void>(csrf, `/api/v1/admin/groups/${id}`, 'PATCH', { name }),
  moveGroup: (csrf: Csrf | null, id: string, parentId: string | null) =>
    adminMutation<void>(csrf, `/api/v1/admin/groups/${id}/move`, 'POST', { parentId }),
  deleteGroup: (csrf: Csrf | null, id: string) =>
    adminMutation<void>(csrf, `/api/v1/admin/groups/${id}`, 'DELETE'),
  addGroupMember: (csrf: Csrf | null, groupId: string, userId: string) =>
    adminMutation<void>(csrf, `/api/v1/admin/groups/${groupId}/members/${userId}`, 'POST'),
  removeGroupMember: (csrf: Csrf | null, groupId: string, userId: string) =>
    adminMutation<void>(csrf, `/api/v1/admin/groups/${groupId}/members/${userId}`, 'DELETE'),
  startReauth: (csrf: Csrf | null) =>
    adminMutation<ReauthStart>(csrf, '/api/v1/admin/reauth/email/start', 'POST'),
  verifyReauth: (
    csrf: Csrf | null, method: string, code: string, challengeId?: string,
  ) => adminMutation<ElevatedStatus>(csrf, '/api/v1/admin/reauth/verify', 'POST', {
    method, code, challengeId: method === 'EMAIL_OTP' ? challengeId : null,
  }),
  revealEmail: (csrf: Csrf | null, id: string) =>
    adminMutation<{ email: string }>(csrf, `/api/v1/admin/users/${id}/email/reveal`, 'POST'),
}
