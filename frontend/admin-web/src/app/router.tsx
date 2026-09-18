import { Navigate, Route, Routes } from 'react-router-dom'
import { lazy, Suspense } from 'react'
import { Box, CircularProgress, Typography } from '@mui/material'
import type { Csrf, Session } from '../types'
import { AdminLayout } from '../components/layout/AdminLayout'

const DashboardPage = lazy(() => import('../pages/DashboardPage').then(module => ({ default: module.DashboardPage })))
const UsersPage = lazy(() => import('../pages/UsersPage').then(module => ({ default: module.UsersPage })))
const GroupsPage = lazy(() => import('../pages/GroupsPage').then(module => ({ default: module.GroupsPage })))
const AuditLogsPage = lazy(() => import('../pages/AuditLogsPage').then(module => ({ default: module.AuditLogsPage })))

export function AdminRouter({ session, csrf }: { session: Session; csrf: Csrf }) {
  return <Suspense fallback={<Box className="route-loading"><CircularProgress size={24} /><Typography color="text.secondary">화면을 불러오고 있습니다.</Typography></Box>}>
    <Routes>
      <Route element={<AdminLayout session={session} csrf={csrf} />}>
        <Route index element={<DashboardPage />} />
        <Route path="users" element={<UsersPage csrf={csrf} />} />
        <Route path="groups" element={<GroupsPage csrf={csrf} />} />
        <Route path="audit-logs" element={<AuditLogsPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  </Suspense>
}
