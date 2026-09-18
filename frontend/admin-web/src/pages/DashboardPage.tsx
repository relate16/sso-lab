import { Box, Paper, Skeleton, Typography } from '@mui/material'
import { FileClock, ShieldCheck, UserCheck, UserRoundX, UsersRound, Workflow } from 'lucide-react'
import { useEffect, useState } from 'react'
import { adminApi } from '../api/admin'
import { userFacingError } from '../api/client'
import { EmptyState, PageHeader } from '../components/common/Ui'
import { useToast } from '../contexts/ToastContext'
import { auditEventLabels, labels } from '../i18n-or-labels/labels'
import type { Dashboard } from '../types'

export function DashboardPage() {
  const [dashboard, setDashboard] = useState<Dashboard | null>(null)
  const [loading, setLoading] = useState(true)
  const { showToast } = useToast()
  useEffect(() => {
    adminApi.dashboard().then(setDashboard).catch(error => {
      showToast(userFacingError(error, labels.genericError), 'error')
    }).finally(() => setLoading(false))
  }, [showToast])
  const cards = dashboard ? [
    { label: '전체 사용자', value: dashboard.totalUsers, icon: UsersRound },
    { label: '활성 사용자', value: dashboard.activeUsers, icon: UserCheck },
    { label: '비활성 사용자', value: dashboard.suspendedUsers, icon: UserRoundX },
    { label: '활성 관리자', value: dashboard.adminUsers, icon: ShieldCheck },
    { label: '전체 그룹', value: dashboard.totalGroups, icon: Workflow },
  ] : []
  return <>
    <PageHeader eyebrow="Overview" title="대시보드" description="현재 계정과 조직, 최근 보안 활동을 실제 운영 데이터로 요약합니다." />
    <Box className="metric-grid">{loading ? Array.from({ length: 5 }).map((_, index) => <Skeleton key={index} variant="rounded" height={120} />) : cards.map(({ label, value, icon: Icon }) => <Paper key={label} variant="outlined" className="metric-card"><span className="metric-icon"><Icon size={20} /></span><Typography color="text.secondary">{label}</Typography><Typography variant="h4">{value.toLocaleString()}</Typography></Paper>)}</Box>
    <Paper variant="outlined" className="content-card">
      <Box className="card-heading"><Box><Typography variant="overline" color="primary">Security activity</Typography><Typography variant="h6">최근 보안 이벤트</Typography></Box><FileClock color="var(--petrol)" /></Box>
      {loading ? <Skeleton height={220} /> : dashboard?.recentSecurityEvents.length ? <Box className="activity-list">{dashboard.recentSecurityEvents.map(item => <Box key={item.id} className="activity-item"><span className={item.success ? 'event-dot success' : 'event-dot failed'} /><Box><strong>{auditEventLabels[item.event] ?? item.event}</strong><Typography variant="body2" color="text.secondary">{item.actorLabel} · {new Date(item.occurredAt).toLocaleString('ko-KR')}</Typography></Box><span className={item.success ? 'result-success' : 'result-failed'}>{item.success ? '성공' : '실패'}</span></Box>)}</Box> : <EmptyState message="최근 보안 이벤트가 없습니다." />}
    </Paper>
  </>
}
