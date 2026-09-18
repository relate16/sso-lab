import {
  Autocomplete, Box, Button, Checkbox, Dialog, DialogActions, DialogContent, DialogTitle,
  FormControl, InputLabel, MenuItem, Pagination, Paper, Select, Stack, Table,
  TableBody, TableCell, TableContainer, TableHead, TableRow, TextField, Typography,
} from '@mui/material'
import { ArrowDownAZ, RefreshCw, Search, Shield, UserRoundX } from 'lucide-react'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { adminApi } from '../api/admin'
import { userFacingError } from '../api/client'
import { PageHeader, EmptyState, RoleChip, StatusChip, TableSkeleton } from '../components/common/Ui'
import { SensitiveEmail } from '../components/security/SensitiveEmail'
import { UserDrawer } from '../components/users/UserDrawer'
import { useToast } from '../contexts/ToastContext'
import type { Csrf, Group, Page, User } from '../types'
import { labels } from '../i18n-or-labels/labels'

const pageSize = 15

export function UsersPage({ csrf }: { csrf: Csrf }) {
  const [page, setPage] = useState<Page<User>>({ content: [], page: 0, size: pageSize, totalElements: 0 })
  const [groups, setGroups] = useState<Group[]>([])
  const [loading, setLoading] = useState(true)
  const [search, setSearch] = useState('')
  const [debouncedSearch, setDebouncedSearch] = useState('')
  const [status, setStatus] = useState('')
  const [role, setRole] = useState('')
  const [groupId, setGroupId] = useState('')
  const [sort, setSort] = useState('normalizedUserId')
  const [direction, setDirection] = useState('asc')
  const [currentPage, setCurrentPage] = useState(0)
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set())
  const [drawerUser, setDrawerUser] = useState<User | null>(null)
  const [bulkDialog, setBulkDialog] = useState<'suspend' | 'roles' | null>(null)
  const [bulkAdmin, setBulkAdmin] = useState(false)
  const { showToast } = useToast()

  useEffect(() => {
    const timer = window.setTimeout(() => { setDebouncedSearch(search); setCurrentPage(0) }, 300)
    return () => window.clearTimeout(timer)
  }, [search])

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const [users, groupList] = await Promise.all([
        adminApi.users({ q: debouncedSearch, status, role, groupId, page: currentPage, size: pageSize, sort, direction }),
        adminApi.groups(),
      ])
      setPage(users); setGroups(groupList)
      setSelectedIds(previous => new Set([...previous].filter(id => users.content.some(user => user.id === id))))
      if (drawerUser) {
        const updated = users.content.find(user => user.id === drawerUser.id)
        if (updated) setDrawerUser(updated)
      }
    } catch (error) { setPage(previous => ({ ...previous, content: [], totalElements: 0 })); showToast(userFacingError(error, labels.genericError), 'error') }
    finally { setLoading(false) }
  }, [debouncedSearch, status, role, groupId, currentPage, sort, direction, drawerUser?.id, showToast])
  useEffect(() => { void load() }, [load])

  const allSelected = page.content.length > 0 && page.content.every(user => selectedIds.has(user.id))
  const toggleAll = () => setSelectedIds(allSelected ? new Set() : new Set(page.content.map(user => user.id)))
  const totalPages = Math.max(1, Math.ceil(page.totalElements / pageSize))
  const selection = useMemo(() => [...selectedIds], [selectedIds])

  const applyBulk = async () => {
    try {
      const result = bulkDialog === 'suspend'
        ? await adminApi.bulkStatus(csrf, selection, 'SUSPENDED')
        : await adminApi.bulkRoles(csrf, selection, bulkAdmin ? ['USER', 'ADMIN'] : ['USER'])
      showToast(`${result.succeededCount}명의 사용자 정보가 변경되었습니다.`, 'success')
      setSelectedIds(new Set()); setBulkDialog(null); await load()
    } catch (error) { showToast(userFacingError(error, labels.genericError), 'error') }
  }

  const changeSort = (field: string) => {
    if (sort === field) setDirection(value => value === 'asc' ? 'desc' : 'asc')
    else { setSort(field); setDirection('asc') }
    setCurrentPage(0)
  }

  return <>
    <PageHeader eyebrow="Identity directory" title="사용자" description="서비스를 이용하는 계정과 역할, 그룹, 접근 상태를 관리합니다." action={<Button variant="outlined" startIcon={<RefreshCw size={17} />} onClick={() => void load()}>새로고침</Button>} />
    <Paper variant="outlined" className="filter-panel">
      <TextField size="small" label="사용자 검색" placeholder="로그인 ID 또는 사용자명" value={search} onChange={event => setSearch(event.target.value)} slotProps={{ input: { startAdornment: <Search size={17} /> } }} />
      <FormControl size="small"><InputLabel>상태</InputLabel><Select label="상태" value={status} onChange={event => { setStatus(event.target.value); setCurrentPage(0) }}><MenuItem value="">전체</MenuItem><MenuItem value="ACTIVE">활성</MenuItem><MenuItem value="SUSPENDED">비활성</MenuItem></Select></FormControl>
      <FormControl size="small"><InputLabel>역할</InputLabel><Select label="역할" value={role} onChange={event => { setRole(event.target.value); setCurrentPage(0) }}><MenuItem value="">전체</MenuItem><MenuItem value="USER">USER</MenuItem><MenuItem value="ADMIN">ADMIN</MenuItem></Select></FormControl>
      <Autocomplete
        size="small" className="group-filter" options={groups}
        value={groups.find(group => group.id === groupId) ?? null}
        getOptionLabel={group => group.fullPath}
        onChange={(_, group) => { setGroupId(group?.id ?? ''); setCurrentPage(0) }}
        renderInput={params => <TextField {...params} label="그룹 검색" />}
      />
    </Paper>
    {selectedIds.size > 0 && <Paper variant="outlined" className="bulk-bar"><Typography><strong>{selectedIds.size}명</strong> 선택됨</Typography><Stack direction="row" spacing={1}><Button color="error" variant="outlined" startIcon={<UserRoundX size={17} />} onClick={() => setBulkDialog('suspend')}>계정 비활성화</Button><Button variant="outlined" startIcon={<Shield size={17} />} onClick={() => setBulkDialog('roles')}>역할 변경</Button></Stack></Paper>}
    <TableContainer component={Paper} variant="outlined" className="data-card">
      {loading ? <TableSkeleton columns={6} /> : <Table stickyHeader size="small" aria-label="사용자 목록">
        <TableHead><TableRow><TableCell padding="checkbox"><Checkbox checked={allSelected} indeterminate={selectedIds.size > 0 && !allSelected} onChange={toggleAll} slotProps={{ input: { 'aria-label': '현재 페이지 사용자 전체 선택' } }} /></TableCell><TableCell><SortButton label="로그인 ID" active={sort === 'normalizedUserId'} onClick={() => changeSort('normalizedUserId')} /></TableCell><TableCell><SortButton label="사용자명" active={sort === 'username'} onClick={() => changeSort('username')} /></TableCell><TableCell>이메일</TableCell><TableCell><SortButton label="상태" active={sort === 'status'} onClick={() => changeSort('status')} /></TableCell><TableCell>역할 · 그룹</TableCell></TableRow></TableHead>
        <TableBody>{page.content.map(user => <TableRow key={user.id} hover selected={selectedIds.has(user.id)} className="clickable-row"><TableCell padding="checkbox" onClick={event => event.stopPropagation()}><Checkbox checked={selectedIds.has(user.id)} onChange={() => setSelectedIds(previous => { const next = new Set(previous); if (next.has(user.id)) next.delete(user.id); else next.add(user.id); return next })} slotProps={{ input: { 'aria-label': `${user.userId} 선택` } }} /></TableCell><TableCell onClick={() => setDrawerUser(user)}><strong>{user.userId}</strong></TableCell><TableCell onClick={() => setDrawerUser(user)}>{user.username}</TableCell><TableCell onClick={event => event.stopPropagation()}><SensitiveEmail csrf={csrf} userId={user.id} masked={user.maskedEmail} /></TableCell><TableCell onClick={() => setDrawerUser(user)}><StatusChip status={user.status} /></TableCell><TableCell onClick={() => setDrawerUser(user)}><Box className="table-chip-row">{user.roles.map(item => <RoleChip role={item} key={item} />)}{user.groups.slice(0, 1).map(group => <span className="group-pill" key={group.id}>{group.name}</span>)}{user.groups.length > 1 && <span className="more-pill">+{user.groups.length - 1}</span>}</Box></TableCell></TableRow>)}</TableBody>
      </Table>}
      {!loading && page.content.length === 0 && <EmptyState message={labels.emptyUsers} />}
    </TableContainer>
    <Box className="pagination-row"><Typography color="text.secondary">총 {page.totalElements.toLocaleString()}명</Typography><Pagination page={currentPage + 1} count={totalPages} onChange={(_, value) => setCurrentPage(value - 1)} /></Box>
    <UserDrawer csrf={csrf} user={drawerUser} groups={groups} open={Boolean(drawerUser)} onClose={() => setDrawerUser(null)} onChanged={load} />
    <Dialog open={Boolean(bulkDialog)} onClose={() => setBulkDialog(null)}>
      <DialogTitle>{bulkDialog === 'suspend' ? '선택한 계정을 비활성화할까요?' : '선택한 사용자의 역할 변경'}</DialogTitle>
      <DialogContent>{bulkDialog === 'suspend' ? <Typography color="text.secondary">선택한 {selectedIds.size}명의 활성 세션과 인증 정보가 함께 철회될 수 있습니다.</Typography> : <CheckboxLabel checked={bulkAdmin} onChange={setBulkAdmin} />}</DialogContent>
      <DialogActions><Button onClick={() => setBulkDialog(null)}>취소</Button><Button color={bulkDialog === 'suspend' ? 'error' : 'primary'} variant="contained" onClick={() => void applyBulk()}>적용</Button></DialogActions>
    </Dialog>
  </>
}

function SortButton({ label, active, onClick }: { label: string; active: boolean; onClick: () => void }) {
  return <button className={active ? 'sort-button active' : 'sort-button'} onClick={onClick}>{label}<ArrowDownAZ size={15} /></button>
}

function CheckboxLabel({ checked, onChange }: { checked: boolean; onChange: (value: boolean) => void }) {
  return <label className="checkbox-label"><Checkbox checked={checked} onChange={event => onChange(event.target.checked)} />ADMIN 역할을 함께 부여합니다. USER 역할은 항상 유지됩니다.</label>
}
