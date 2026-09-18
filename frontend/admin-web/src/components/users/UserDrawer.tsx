import {
  Box, Button, Checkbox, Dialog, DialogActions, DialogContent, DialogTitle,
  Drawer, FormControlLabel, FormGroup, IconButton, MenuItem, Select, Typography,
} from '@mui/material'
import { X } from 'lucide-react'
import { useEffect, useState } from 'react'
import { adminApi } from '../../api/admin'
import { userFacingError } from '../../api/client'
import { useToast } from '../../contexts/ToastContext'
import type { Csrf, Group, User } from '../../types'
import { RoleChip, StatusChip } from '../common/Ui'
import { SensitiveEmail } from '../security/SensitiveEmail'

export function UserDrawer({ csrf, user, groups, open, onClose, onChanged }: {
  csrf: Csrf; user: User | null; groups: Group[]; open: boolean
  onClose: () => void; onChanged: () => Promise<void>
}) {
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [selectedGroups, setSelectedGroups] = useState<string[]>([])
  const [admin, setAdmin] = useState(false)
  const { showToast } = useToast()
  useEffect(() => {
    setSelectedGroups(user?.groups.map(group => group.id) ?? [])
    setAdmin(user?.roles.includes('ADMIN') ?? false)
  }, [user])
  if (!user) return null

  const run = async (message: string, action: () => Promise<unknown>) => {
    try { await action(); showToast(message, 'success'); await onChanged() }
    catch (error) { showToast(userFacingError(error, '작업에 실패했습니다.'), 'error') }
  }
  return <>
    <Drawer anchor="right" open={open} onClose={onClose} slotProps={{ paper: { className: 'detail-drawer' } }}>
      <Box className="drawer-header"><Box><Typography variant="overline" color="primary">사용자 상세</Typography><Typography variant="h5">{user.username}</Typography><Typography color="text.secondary">{user.userId}</Typography></Box><IconButton onClick={onClose} aria-label="사용자 상세 닫기"><X /></IconButton></Box>
      <Box className="drawer-section"><Typography variant="subtitle2">계정 정보</Typography><dl className="detail-list"><div><dt>로그인 ID</dt><dd>{user.userId}</dd></div><div><dt>사용자명</dt><dd>{user.username}</dd></div><div><dt>이메일</dt><dd><SensitiveEmail csrf={csrf} userId={user.id} masked={user.maskedEmail} /></dd></div><div><dt>상태</dt><dd><StatusChip status={user.status} /></dd></div></dl></Box>
      <Box className="drawer-section"><Typography variant="subtitle2">역할</Typography><Box className="chip-row">{user.roles.map(role => <RoleChip key={role} role={role} />)}</Box><FormControlLabel control={<Checkbox checked={admin} onChange={event => setAdmin(event.target.checked)} />} label="관리자 역할" /><Button variant="outlined" onClick={() => void run('사용자 역할을 변경했습니다.', () => adminApi.roles(csrf, user.id, admin ? ['USER', 'ADMIN'] : ['USER']))}>역할 적용</Button></Box>
      <Box className="drawer-section"><Typography variant="subtitle2">그룹</Typography><Select multiple fullWidth value={selectedGroups} onChange={event => setSelectedGroups(typeof event.target.value === 'string' ? event.target.value.split(',') : event.target.value)} renderValue={values => values.map(id => groups.find(group => group.id === id)?.name).filter(Boolean).join(', ')}>{groups.map(group => <MenuItem key={group.id} value={group.id}><Checkbox checked={selectedGroups.includes(group.id)} />{group.fullPath}</MenuItem>)}</Select><Button sx={{ mt: 1 }} variant="outlined" onClick={() => void run('그룹 멤버십을 변경했습니다.', () => adminApi.groupsForUser(csrf, user.id, selectedGroups))}>그룹 적용</Button></Box>
      <Box className="drawer-section drawer-actions"><Typography variant="subtitle2">계정 관리</Typography><Button color={user.status === 'ACTIVE' ? 'error' : 'primary'} variant={user.status === 'ACTIVE' ? 'outlined' : 'contained'} onClick={() => setConfirmOpen(true)}>{user.status === 'ACTIVE' ? '계정 비활성화' : '계정 활성화'}</Button></Box>
    </Drawer>
    <Dialog open={confirmOpen} onClose={() => setConfirmOpen(false)}>
      <DialogTitle>{user.status === 'ACTIVE' ? '계정을 비활성화할까요?' : '계정을 활성화할까요?'}</DialogTitle>
      <DialogContent><Typography color="text.secondary">{user.username} 사용자의 서비스 접근 상태가 즉시 변경됩니다.</Typography></DialogContent>
      <DialogActions><Button onClick={() => setConfirmOpen(false)}>취소</Button><Button color={user.status === 'ACTIVE' ? 'error' : 'primary'} variant="contained" onClick={() => { setConfirmOpen(false); void run(user.status === 'ACTIVE' ? '계정을 비활성화했습니다.' : '계정을 활성화했습니다.', () => user.status === 'ACTIVE' ? adminApi.suspend(csrf, user.id) : adminApi.resume(csrf, user.id)) }}>확인</Button></DialogActions>
    </Dialog>
  </>
}
