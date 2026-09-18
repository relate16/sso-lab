import { Box, Chip, Skeleton, Typography } from '@mui/material'
import { Inbox } from 'lucide-react'
import type { ReactNode } from 'react'

export function PageHeader({ eyebrow, title, description, action }: {
  eyebrow: string; title: string; description: string; action?: ReactNode
}) {
  return <Box className="page-header"><Box><Typography variant="overline" color="primary">{eyebrow}</Typography><Typography variant="h4">{title}</Typography><Typography color="text.secondary">{description}</Typography></Box>{action}</Box>
}

export function EmptyState({ message }: { message: string }) {
  return <Box className="empty-state"><Inbox aria-hidden="true" /><Typography>{message}</Typography></Box>
}

export function StatusChip({ status }: { status: string }) {
  const active = status === 'ACTIVE'
  return <Chip size="small" color={active ? 'success' : 'default'} variant={active ? 'outlined' : 'filled'} label={active ? '활성' : '비활성'} />
}

export function RoleChip({ role }: { role: string }) {
  return <Chip size="small" className={role === 'ADMIN' ? 'role-admin' : 'role-user'} label={role} />
}

export function TableSkeleton({ columns = 5, rows = 6 }: { columns?: number; rows?: number }) {
  return <Box className="table-skeleton" aria-label="데이터 불러오는 중">{Array.from({ length: rows }).map((_, row) => <Box key={row} sx={{ display: 'grid', gridTemplateColumns: `repeat(${columns}, 1fr)`, gap: 2 }}>{Array.from({ length: columns }).map((__, cell) => <Skeleton key={cell} height={42} />)}</Box>)}</Box>
}
