import {
  Avatar, Box, Button, Chip, Divider, Drawer, IconButton, List, ListItemButton,
  ListItemIcon, ListItemText, Menu, MenuItem, Tooltip, Typography, useMediaQuery,
} from '@mui/material'
import { ChevronLeft, ChevronRight, Eye, EyeOff, LogOut, Menu as MenuIcon } from 'lucide-react'
import { useState } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router-dom'
import type { Csrf, Session } from '../../types'
import { navigation } from '../../app/navigation'
import { MarsLogo } from './MarsLogo'
import { useSecurity } from '../../contexts/SecurityContext'

const compactWidth = 232
const expandedWidth = 292

export function AdminLayout({ session, csrf }: { session: Session; csrf: Csrf }) {
  const [expanded, setExpanded] = useState(false)
  const [mobileOpen, setMobileOpen] = useState(false)
  const [anchor, setAnchor] = useState<HTMLElement | null>(null)
  const mobile = useMediaQuery('(max-width:900px)')
  const location = useLocation()
  const security = useSecurity()
  const current = navigation.find(item => item.path === location.pathname)?.label ?? '관리자 콘솔'
  const drawerWidth = expanded ? expandedWidth : compactWidth
  const name = session.name || session.username || '관리자'

  const sidebar = <Box className="sidebar-content">
    <Box className="brand"><Box className="brand-logo"><MarsLogo /></Box><Box className="brand-copy"><strong>SSO Lab</strong><span>Admin Console</span></Box></Box>
    <Divider />
    <List component="nav" aria-label="관리자 메뉴" className="nav-list">
      {navigation.map(({ path, label, icon: Icon }) => <ListItemButton
        key={path} component={NavLink} to={path} end={path === '/'}
        onClick={() => setMobileOpen(false)}
        className="nav-item"
      >
        <ListItemIcon><Icon size={19} aria-hidden="true" /></ListItemIcon>
        <ListItemText primary={label} slotProps={{ primary: { noWrap: true } }} />
      </ListItemButton>)}
    </List>
    {!mobile && <Tooltip title={expanded ? '사이드바 너비 줄이기' : '긴 메뉴를 위한 사이드바 확장'}>
      <IconButton className="sidebar-resize" onClick={() => setExpanded(value => !value)} aria-label={expanded ? '사이드바 줄이기' : '사이드바 확장'}>
        {expanded ? <ChevronLeft size={18} /> : <ChevronRight size={18} />}
      </IconButton>
    </Tooltip>}
    <Box className="sidebar-footer"><span>OIDC · Passwordless MFA</span><small>보안 세션 기반 관리</small></Box>
  </Box>

  return <Box className="admin-root">
    <Drawer
      variant={mobile ? 'temporary' : 'permanent'} open={mobile ? mobileOpen : true}
      onClose={() => setMobileOpen(false)}
      ModalProps={{ keepMounted: true }}
      sx={{ '& .MuiDrawer-paper': { width: drawerWidth } }}
    >{sidebar}</Drawer>
    <Box className="admin-main" sx={{ ml: mobile ? 0 : `${drawerWidth}px` }}>
      <header className="topbar">
        <Box className="topbar-title">
          {mobile && <IconButton onClick={() => setMobileOpen(true)} aria-label="메뉴 열기"><MenuIcon /></IconButton>}
          <Box><Typography variant="overline">SSO 관리 콘솔</Typography><Typography variant="h6">{current}</Typography></Box>
        </Box>
        <Box className="topbar-actions">
          <Chip
            icon={security.elevated ? <Eye size={16} /> : <EyeOff size={16} />}
            label={security.elevated ? `마스킹 해제됨 · ${formatRemaining(security.remainingSeconds)}` : '마스킹 해제 필요'}
            color={security.elevated ? 'success' : 'default'} variant="outlined"
            clickable={!security.elevated}
            onClick={security.elevated ? undefined : () => void security.requestElevated()}
            aria-label={security.elevated ? '민감정보 마스킹 해제 상태' : '민감정보 마스킹 해제 인증'}
          />
          <Button className="identity-button" onClick={event => setAnchor(event.currentTarget)} aria-haspopup="menu">
            <Avatar>{name.slice(0, 1).toUpperCase()}</Avatar>
            <Box><strong>{name}</strong><span>관리자</span></Box>
          </Button>
          <Menu anchorEl={anchor} open={Boolean(anchor)} onClose={() => setAnchor(null)}>
            <MenuItem><form method="post" action="/api/v1/logout" className="logout-form">
              <input type="hidden" name={csrf.parameterName} value={csrf.token} />
              <button type="submit" className="menu-logout"><LogOut size={17} />로그아웃</button>
            </form></MenuItem>
          </Menu>
        </Box>
      </header>
      <Box component="main" className="page-container"><Outlet /></Box>
    </Box>
  </Box>
}

function formatRemaining(seconds: number) {
  const minutes = Math.floor(seconds / 60).toString().padStart(2, '0')
  return `${minutes}:${(seconds % 60).toString().padStart(2, '0')}`
}
