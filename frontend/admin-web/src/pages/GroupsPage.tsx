import {
  Autocomplete, Box, Button, Dialog, DialogActions, DialogContent, DialogTitle,
  Drawer, IconButton, List, ListItem, ListItemText, MenuItem, Paper, TextField,
  Typography,
} from '@mui/material'
import { ChevronRight, Folder, FolderPlus, GripVertical, Pencil, Trash2, UserMinus, X } from 'lucide-react'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { adminApi } from '../api/admin'
import { userFacingError } from '../api/client'
import { buildGroupTree } from '../app/groupTree'
import type { GroupNode } from '../app/groupTree'
import { EmptyState, PageHeader, StatusChip } from '../components/common/Ui'
import { useToast } from '../contexts/ToastContext'
import { labels } from '../i18n-or-labels/labels'
import type { Csrf, Group, GroupDetail, User } from '../types'

type DialogMode = 'create' | 'rename' | 'move' | 'delete' | null

export function GroupsPage({ csrf }: { csrf: Csrf }) {
  const [groups, setGroups] = useState<Group[]>([])
  const [selected, setSelected] = useState<GroupDetail | null>(null)
  const [dialog, setDialog] = useState<DialogMode>(null)
  const [dialogGroup, setDialogGroup] = useState<Group | null>(null)
  const [candidateUsers, setCandidateUsers] = useState<User[]>([])
  const [candidateLoading, setCandidateLoading] = useState(false)
  const [candidateSearch, setCandidateSearch] = useState('')
  const [userToAdd, setUserToAdd] = useState<User | null>(null)
  const [draggedId, setDraggedId] = useState<string | null>(null)
  const { showToast } = useToast()

  const load = useCallback(async () => {
    try {
      const list = await adminApi.groups(); setGroups(list)
      if (selected) setSelected(await adminApi.group(selected.group.id))
    } catch (error) { setGroups([]); showToast(userFacingError(error, labels.genericError), 'error') }
  }, [selected?.group.id, showToast])
  useEffect(() => { void load() }, [load])
  useEffect(() => {
    if (!selected) return
    const timer = window.setTimeout(() => {
      setCandidateLoading(true)
      adminApi.users({ q: candidateSearch, size: 25, sort: 'normalizedUserId', direction: 'asc' })
        .then(users => setCandidateUsers(users.content))
        .catch(error => showToast(userFacingError(error, labels.genericError), 'error'))
        .finally(() => setCandidateLoading(false))
    }, 250)
    return () => window.clearTimeout(timer)
  }, [candidateSearch, selected?.group.id, showToast])
  const tree = useMemo(() => buildGroupTree(groups), [groups])

  const openDetail = async (group: Group) => {
    try {
      setSelected(await adminApi.group(group.id))
      setCandidateSearch('')
    } catch (error) { showToast(userFacingError(error, labels.genericError), 'error') }
  }
  const run = async (message: string, action: () => Promise<unknown>) => {
    try { await action(); showToast(message, 'success'); setDialog(null); await load() }
    catch (error) { showToast(userFacingError(error, labels.genericError), 'error') }
  }
  const moveByDrop = async (groupId: string, parentId: string | null) => {
    if (groupId === parentId) return
    await run('그룹을 이동했습니다.', () => adminApi.moveGroup(csrf, groupId, parentId))
  }

  return <>
    <PageHeader eyebrow="Organization" title="그룹" description="계층형 조직 그룹과 소속 사용자를 관리합니다." action={<Button variant="contained" startIcon={<FolderPlus size={17} />} onClick={() => { setDialogGroup(null); setDialog('create') }}>그룹 생성</Button>} />
    <Paper variant="outlined" className="content-card group-tree-card">
      <Box className="card-heading"><Box><Typography variant="overline" color="primary">Group hierarchy</Typography><Typography variant="h6">그룹 구조</Typography></Box><Typography variant="body2" color="text.secondary">드래그하여 그룹을 이동하거나, 메뉴에서 직접 이동할 수 있습니다.</Typography></Box>
      {tree.length ? <Box role="tree" aria-label="그룹 계층"><div className="root-drop" onDragOver={event => event.preventDefault()} onDrop={() => { if (draggedId) void moveByDrop(draggedId, null); setDraggedId(null) }}>최상위로 이동</div>{tree.map(node => <GroupTreeNode key={node.id} node={node} depth={0} onSelect={openDetail} onEdit={(mode, group) => { setDialogGroup(group); setDialog(mode) }} onDrag={setDraggedId} onDrop={moveByDrop} />)}</Box> : <EmptyState message={labels.emptyGroups} />}
    </Paper>
    <GroupDialog mode={dialog} group={dialogGroup} groups={groups} open={Boolean(dialog)} onClose={() => setDialog(null)} onSubmit={(name, parentId) => {
      if (dialog === 'create') return run('그룹을 생성했습니다.', () => adminApi.createGroup(csrf, name, parentId))
      if (dialog === 'rename' && dialogGroup) return run('그룹 이름을 변경했습니다.', () => adminApi.renameGroup(csrf, dialogGroup.id, name))
      if (dialog === 'move' && dialogGroup) return run('그룹을 이동했습니다.', () => adminApi.moveGroup(csrf, dialogGroup.id, parentId))
      if (dialog === 'delete' && dialogGroup) return run('그룹을 삭제했습니다.', () => adminApi.deleteGroup(csrf, dialogGroup.id))
      return Promise.resolve()
    }} />
    <Drawer anchor="right" open={Boolean(selected)} onClose={() => setSelected(null)} slotProps={{ paper: { className: 'detail-drawer' } }}>
      {selected && <><Box className="drawer-header"><Box><Typography variant="overline" color="primary">그룹 상세</Typography><Typography variant="h5">{selected.group.name}</Typography><Typography color="text.secondary">{selected.group.fullPath}</Typography></Box><IconButton onClick={() => setSelected(null)} aria-label="그룹 상세 닫기"><X /></IconButton></Box>
        <Box className="drawer-section"><dl className="detail-list"><div><dt>상위 그룹</dt><dd>{groups.find(group => group.id === selected.group.parentId)?.fullPath ?? '최상위'}</dd></div><div><dt>소속 사용자</dt><dd>{selected.members.length}명</dd></div></dl></Box>
        <Box className="drawer-section"><Typography variant="subtitle2">사용자 추가</Typography><Autocomplete loading={candidateLoading} options={candidateUsers.filter(user => !selected.members.some(member => member.id === user.id))} getOptionLabel={user => `${user.userId} · ${user.username}`} value={userToAdd} onChange={(_, value) => setUserToAdd(value)} onInputChange={(_, value) => setCandidateSearch(value)} filterOptions={options => options} renderInput={params => <TextField {...params} label="사용자 검색" size="small" />} /><Button sx={{ mt: 1 }} variant="outlined" disabled={!userToAdd} onClick={() => { if (userToAdd) void run('그룹에 사용자를 추가했습니다.', () => adminApi.addGroupMember(csrf, selected.group.id, userToAdd.id)).then(() => setUserToAdd(null)) }}>사용자 추가</Button></Box>
        <Box className="drawer-section"><Typography variant="subtitle2">소속 사용자</Typography>{selected.members.length ? <List disablePadding>{selected.members.map(member => <ListItem key={member.id} divider secondaryAction={<IconButton edge="end" aria-label={`${member.username} 그룹에서 제거`} onClick={() => void run('그룹에서 사용자를 제거했습니다.', () => adminApi.removeGroupMember(csrf, selected.group.id, member.id))}><UserMinus size={17} /></IconButton>}><ListItemText primary={`${member.userId} · ${member.username}`} secondary={<StatusChip status={member.status} />} /></ListItem>)}</List> : <EmptyState message="이 그룹에 소속된 사용자가 없습니다." />}</Box></>}
    </Drawer>
  </>
}

function GroupTreeNode({ node, depth, onSelect, onEdit, onDrag, onDrop }: {
  node: GroupNode; depth: number
  onSelect: (group: Group) => void
  onEdit: (mode: Exclude<DialogMode, 'create' | null>, group: Group) => void
  onDrag: (id: string | null) => void
  onDrop: (id: string, parentId: string | null) => Promise<void>
}) {
  return <div role="treeitem" aria-level={depth + 1}>
    <div className="group-tree-row" style={{ paddingLeft: `${depth * 24 + 10}px` }} draggable onDragStart={event => { onDrag(node.id); event.dataTransfer.setData('text/plain', node.id) }} onDragEnd={() => onDrag(null)} onDragOver={event => event.preventDefault()} onDrop={event => { event.stopPropagation(); const id = event.dataTransfer.getData('text/plain'); if (id) void onDrop(id, node.id) }}>
      <button className="drag-handle" aria-label={`${node.name} 이동 핸들`} onDragStart={event => event.dataTransfer.setData('text/plain', node.id)} draggable><GripVertical size={16} /></button><button className="group-name" onClick={() => void onSelect(node)}><Folder size={18} /><span><strong>{node.name}</strong><small>{node.memberCount}명</small></span><ChevronRight size={17} /></button><div className="tree-actions"><IconButton size="small" aria-label={`${node.name} 이름 변경`} onClick={() => onEdit('rename', node)}><Pencil size={16} /></IconButton><Button size="small" onClick={() => onEdit('move', node)}>이동</Button><IconButton size="small" color="error" aria-label={`${node.name} 삭제`} onClick={() => onEdit('delete', node)}><Trash2 size={16} /></IconButton></div>
    </div>
    {node.children.map(child => <GroupTreeNode key={child.id} node={child} depth={depth + 1} onSelect={onSelect} onEdit={onEdit} onDrag={onDrag} onDrop={onDrop} />)}
  </div>
}

function GroupDialog({ mode, group, groups, open, onClose, onSubmit }: {
  mode: DialogMode; group: Group | null; groups: Group[]; open: boolean
  onClose: () => void; onSubmit: (name: string, parentId: string | null) => Promise<void>
}) {
  const [name, setName] = useState('')
  const [parentId, setParentId] = useState<string>('')
  useEffect(() => { setName(group?.name ?? ''); setParentId(group?.parentId ?? '') }, [group, mode])
  const title = mode === 'create' ? '그룹 생성' : mode === 'rename' ? '그룹 이름 변경' : mode === 'move' ? '그룹 이동' : '그룹 삭제'
  const availableParents = groups.filter(item => item.id !== group?.id && (!group || !item.fullPath.startsWith(`${group.fullPath}/`)))
  return <Dialog open={open} onClose={onClose} fullWidth maxWidth="xs"><DialogTitle>{title}</DialogTitle><DialogContent>{mode === 'delete' ? <Typography color="text.secondary">{group?.fullPath} 그룹을 삭제합니다. 하위 그룹이나 소속 사용자가 있으면 삭제할 수 없습니다.</Typography> : <Box sx={{ display: 'grid', gap: 2, pt: 1 }}>{mode !== 'move' && <TextField autoFocus label="그룹명" value={name} onChange={event => setName(event.target.value)} slotProps={{ htmlInput: { maxLength: 100 } }} />}{mode !== 'rename' && <TextField select label="상위 그룹" value={parentId} onChange={event => setParentId(event.target.value)}><MenuItem value="">최상위</MenuItem>{availableParents.map(item => <MenuItem key={item.id} value={item.id}>{item.fullPath}</MenuItem>)}</TextField>}</Box>}</DialogContent><DialogActions><Button onClick={onClose}>취소</Button><Button color={mode === 'delete' ? 'error' : 'primary'} variant="contained" disabled={mode !== 'delete' && mode !== 'move' && !name.trim()} onClick={() => void onSubmit(name.trim(), parentId || null)}>{mode === 'delete' ? '삭제' : '저장'}</Button></DialogActions></Dialog>
}
