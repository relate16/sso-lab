import {
  Box, Chip, Drawer, FormControl, IconButton, InputLabel, MenuItem, Pagination,
  Paper, Select, Table, TableBody, TableCell, TableContainer, TableHead, TableRow,
  TextField, Typography,
} from '@mui/material'
import { AlertTriangle, CheckCircle2, FileClock, X } from 'lucide-react'
import { useCallback, useEffect, useState } from 'react'
import { adminApi } from '../api/admin'
import { userFacingError } from '../api/client'
import { EmptyState, PageHeader, TableSkeleton } from '../components/common/Ui'
import { useToast } from '../contexts/ToastContext'
import { auditEventLabels, labels } from '../i18n-or-labels/labels'
import type { Audit, Page } from '../types'

const pageSize = 20

export function AuditLogsPage() {
  const [page, setPage] = useState<Page<Audit>>({ content: [], page: 0, size: pageSize, totalElements: 0 })
  const [loading, setLoading] = useState(true)
  const [event, setEvent] = useState('')
  const [result, setResult] = useState('')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [currentPage, setCurrentPage] = useState(0)
  const [selected, setSelected] = useState<Audit | null>(null)
  const { showToast } = useToast()

  const load = useCallback(async () => {
    setLoading(true)
    try {
      setPage(await adminApi.audits({
        event,
        success: result === '' ? undefined : result === 'success',
        from: from ? new Date(from).toISOString() : undefined,
        to: to ? new Date(to).toISOString() : undefined,
        page: currentPage, size: pageSize, sort: 'occurredAt', direction: 'desc',
      }))
    } catch (error) { setPage(previous => ({ ...previous, content: [], totalElements: 0 })); showToast(userFacingError(error, labels.genericError), 'error') }
    finally { setLoading(false) }
  }, [event, result, from, to, currentPage, showToast])
  useEffect(() => { void load() }, [load])

  return <>
    <PageHeader eyebrow="Security audit" title="감사 로그" description="관리자 작업과 계정 보안 이벤트를 시간순으로 확인합니다." />
    <Paper variant="outlined" className="filter-panel audit-filters">
      <FormControl size="small"><InputLabel>이벤트</InputLabel><Select value={event} label="이벤트" onChange={event => { setEvent(event.target.value); setCurrentPage(0) }}><MenuItem value="">전체</MenuItem>{Object.entries(auditEventLabels).map(([value, label]) => <MenuItem value={value} key={value}>{label}</MenuItem>)}</Select></FormControl>
      <FormControl size="small"><InputLabel>결과</InputLabel><Select value={result} label="결과" onChange={event => { setResult(event.target.value); setCurrentPage(0) }}><MenuItem value="">전체</MenuItem><MenuItem value="success">성공</MenuItem><MenuItem value="failure">실패</MenuItem></Select></FormControl>
      <TextField size="small" label="시작 일시" type="datetime-local" value={from} onChange={event => { setFrom(event.target.value); setCurrentPage(0) }} slotProps={{ inputLabel: { shrink: true } }} />
      <TextField size="small" label="종료 일시" type="datetime-local" value={to} onChange={event => { setTo(event.target.value); setCurrentPage(0) }} slotProps={{ inputLabel: { shrink: true } }} />
    </Paper>
    <TableContainer component={Paper} variant="outlined" className="data-card audit-table">
      {loading ? <TableSkeleton columns={5} /> : <Table stickyHeader size="small" aria-label="감사 로그"><TableHead><TableRow><TableCell>이벤트</TableCell><TableCell>수행자</TableCell><TableCell>대상</TableCell><TableCell>결과</TableCell><TableCell>시간</TableCell></TableRow></TableHead><TableBody>{page.content.map(item => <TableRow key={item.id} hover className={item.success ? 'clickable-row' : 'clickable-row audit-failed-row'} onClick={() => setSelected(item)}><TableCell><Box className="event-name">{item.success ? <CheckCircle2 size={17} /> : <AlertTriangle size={17} />}<strong>{auditEventLabels[item.event] ?? item.event}</strong></Box></TableCell><TableCell>{item.actorLabel}</TableCell><TableCell>{item.targetLabel}</TableCell><TableCell><Chip size="small" color={item.success ? 'success' : 'error'} variant="outlined" label={item.success ? '성공' : '실패'} /></TableCell><TableCell>{new Date(item.occurredAt).toLocaleString('ko-KR')}</TableCell></TableRow>)}</TableBody></Table>}
      {!loading && page.content.length === 0 && <EmptyState message={labels.emptyAudits} />}
    </TableContainer>
    <Box className="pagination-row"><Typography color="text.secondary">총 {page.totalElements.toLocaleString()}건</Typography><Pagination page={currentPage + 1} count={Math.max(1, Math.ceil(page.totalElements / pageSize))} onChange={(_, value) => setCurrentPage(value - 1)} /></Box>
    <Drawer anchor="right" open={Boolean(selected)} onClose={() => setSelected(null)} slotProps={{ paper: { className: 'detail-drawer' } }}>{selected && <><Box className="drawer-header"><Box><Typography variant="overline" color="primary">Event detail</Typography><Typography variant="h5">{auditEventLabels[selected.event] ?? selected.event}</Typography></Box><IconButton aria-label="감사 상세 닫기" onClick={() => setSelected(null)}><X /></IconButton></Box><Box className="drawer-section"><Box className="audit-detail-icon">{selected.success ? <CheckCircle2 /> : <AlertTriangle />}</Box><dl className="detail-list"><div><dt>결과</dt><dd>{selected.success ? '성공' : '실패'}</dd></div><div><dt>수행자</dt><dd>{selected.actorLabel}</dd></div><div><dt>대상</dt><dd>{selected.targetLabel}</dd></div><div><dt>발생 시각</dt><dd>{new Date(selected.occurredAt).toLocaleString('ko-KR')}</dd></div><div><dt>발생 서비스</dt><dd>{sourceLabel(selected.source)}</dd></div></dl><Typography variant="body2" color="text.secondary" sx={{ mt: 2 }}>감사 모델에 신뢰 가능한 IP 주소가 저장되지 않으므로 IP 정보는 표시하지 않습니다.</Typography></Box></>}</Drawer>
  </>
}

function sourceLabel(source: string) {
  if (source === 'ADMIN_WEB') return '관리자 콘솔'
  if (source === 'AUTH_WEB') return '인증 서비스'
  if (source === 'INTERNAL_API') return '내부 서비스 연동'
  if (source === 'BOOTSTRAP') return '초기 관리자 설정'
  return '시스템'
}
