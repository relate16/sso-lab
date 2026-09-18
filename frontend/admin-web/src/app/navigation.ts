import { FileClock, LayoutDashboard, UsersRound, Workflow } from 'lucide-react'

export const navigation = [
  { path: '/', label: '대시보드', icon: LayoutDashboard },
  { path: '/users', label: '사용자', icon: UsersRound },
  { path: '/groups', label: '그룹', icon: Workflow },
  { path: '/audit-logs', label: '감사 로그', icon: FileClock },
] as const
