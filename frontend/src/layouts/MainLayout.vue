<script setup>
import { ref, reactive, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import {
  ClipboardList, UsersRound, Handshake, LogOut,
  LayoutDashboard, Settings, ShieldCheck, UserCog, ScrollText, ChevronDown, Search, KeyRound, FolderDown
} from 'lucide-vue-next'
import { useAuthStore } from '../stores/auth'
import { api } from '../api'
import { contractStatuses } from '../constants/contract'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const menuItems = [
  { path: '/dashboard', label: '工作台', icon: LayoutDashboard },
  { path: '/contracts/query', label: '合同查询', icon: Search, permission: 'contract:query' },
  { path: '/templates', label: '模板库', icon: FolderDown, anyPermission: ['contract:create', 'contract:assign'] },
  { path: '/tasks', label: '我的待办', icon: Handshake, anyPermission: ['contract:assign', 'contract:countersign', 'contract:approve', 'contract:sign', 'contract:update'], badge: true },
  { path: '/customers', label: '客户管理', icon: UsersRound, permission: 'customer:manage' },
]

const contractStatusItems = contractStatuses.map(item => ({
  status: item.value,
  label: item.value ? item.label : '全部合同',
  permission: item.value === 'DRAFT' ? 'contract:assign' : undefined
}))

const sysItems = [
  { path: '/system/users', label: '用户管理', icon: UserCog, permission: 'user:manage' },
  { path: '/system/roles', label: '角色管理', icon: ShieldCheck, permission: 'role:manage' },
  { path: '/system/permissions', label: '权限管理', icon: KeyRound, permission: 'permission:manage' },
  { path: '/system/logs', label: '操作日志', icon: ScrollText, permission: 'log:view' },
]

function canAccess(item) {
  if (item.permission) return auth.permissions.includes(item.permission)
  if (item.anyPermission) return item.anyPermission.some(p => auth.permissions.includes(p))
  return true
}

const hasSystemAccess = computed(() =>
  auth.permissions.some(p => ['user:manage', 'role:manage', 'permission:manage', 'log:view'].includes(p))
)

const sysMenuOpen = ref(false)
const contractMenuOpen = ref(false)
const pendingTaskCount = ref(0)
const showProfileForm = ref(false)
const profileError = ref('')
const profileForm = reactive({ displayName: '', phone: '', email: '', password: '' })
const canLoadTasks = computed(() =>
  auth.permissions.some(p => ['contract:assign', 'contract:countersign', 'contract:approve', 'contract:sign', 'contract:update'].includes(p))
)
const BADGE_POLL_INTERVAL = 10000
let badgeTimer = null

watch(() => route.path, (path) => {
  if (path.startsWith('/system')) sysMenuOpen.value = true
  if (path === '/contracts' || path === '/contracts/create' || /^\/contracts\/\d+/.test(path)) contractMenuOpen.value = true
})

function isActive(path) {
  if (path === '/contracts') return route.path === '/contracts' || route.path === '/contracts/create' || /^\/contracts\/\d+/.test(route.path)
  if (path === '/contracts/query') return route.path === '/contracts/query'
  return route.path === path
}

function openContractStatus(status) {
  router.push(status ? { path: '/contracts', query: { status } } : '/contracts')
}

function isContractStatusActive(status) {
  return route.path === '/contracts' && (route.query.status || '') === status
}

function logout() {
  auth.logout()
  router.push('/login')
}

function openProfileForm() {
  profileError.value = ''
  profileForm.displayName = auth.user?.displayName || ''
  profileForm.phone = auth.user?.phone || ''
  profileForm.email = auth.user?.email || ''
  profileForm.password = ''
  showProfileForm.value = true
}

async function saveProfile() {
  profileError.value = ''
  if (!profileForm.displayName.trim()) {
    profileError.value = '显示名称不能为空'
    return
  }
  if (profileForm.password && profileForm.password.length < 6) {
    profileError.value = '密码长度不能少于6位'
    return
  }
  try {
    await auth.updateProfile({
      displayName: profileForm.displayName,
      phone: profileForm.phone,
      email: profileForm.email,
      password: profileForm.password
    })
    showProfileForm.value = false
  } catch (err) {
    profileError.value = err.message
  }
}

async function loadPendingTaskCount() {
  if (!canLoadTasks.value) return
  try {
    const res = await api.get('/statistics/tasks/my')
    pendingTaskCount.value = res.data.pendingTasks || 0
  } catch {}
}

function startBadgePolling() {
  stopBadgePolling()
  loadPendingTaskCount()
  badgeTimer = window.setInterval(loadPendingTaskCount, BADGE_POLL_INTERVAL)
}

function stopBadgePolling() {
  if (!badgeTimer) return
  window.clearInterval(badgeTimer)
  badgeTimer = null
}

watch(() => route.fullPath, loadPendingTaskCount)
onMounted(() => {
  startBadgePolling()
  window.addEventListener('tasks-updated', loadPendingTaskCount)
})
onBeforeUnmount(() => {
  stopBadgePolling()
  window.removeEventListener('tasks-updated', loadPendingTaskCount)
})
</script>

<template>
  <div class="shell">
    <aside class="sidebar">
      <div class="brand" @click="router.push('/dashboard')" style="cursor:pointer">
        <ClipboardList :size="24" />
        <strong>ContractSys</strong>
      </div>
      <nav>
        <button
          v-for="item in menuItems" :key="item.path"
          v-show="canAccess(item)"
          :class="{ selected: isActive(item.path) }"
          @click="router.push(item.path)"
        >
          <component :is="item.icon" :size="18" />
          <span>{{ item.label }}</span>
          <span v-if="item.badge && pendingTaskCount" class="nav-badge">{{ pendingTaskCount }}</span>
        </button>

        <div v-if="auth.permissions.includes('contract:view')" class="sys-group">
          <button :class="{ selected: isActive('/contracts') }" class="sys-toggle" @click="contractMenuOpen = !contractMenuOpen">
            <ClipboardList :size="18" />
            合同管理
            <ChevronDown :size="14" :class="{ rotated: contractMenuOpen }" style="margin-left:auto" />
          </button>
          <div v-show="contractMenuOpen" class="sys-sub">
            <button
              v-for="item in contractStatusItems" :key="item.status || 'all'"
              v-show="canAccess(item)"
              :class="{ selected: isContractStatusActive(item.status) }"
              @click="openContractStatus(item.status)"
            >
              {{ item.label }}
            </button>
          </div>
        </div>

        <div v-if="hasSystemAccess" class="sys-group">
          <button class="sys-toggle" @click="sysMenuOpen = !sysMenuOpen">
            <Settings :size="18" />
            系统管理
            <ChevronDown :size="14" :class="{ rotated: sysMenuOpen }" style="margin-left:auto" />
          </button>
          <div v-show="sysMenuOpen" class="sys-sub">
            <button
              v-for="item in sysItems" :key="item.path"
              v-show="canAccess(item)"
              :class="{ selected: route.path === item.path }"
              @click="router.push(item.path)"
            >
              <component :is="item.icon" :size="16" />
              {{ item.label }}
            </button>
          </div>
        </div>
      </nav>
    </aside>

    <main class="workspace">
      <header class="topbar">
        <h1>{{ route.meta.title || '合同管理系统' }}</h1>
        <div class="actions">
          <span class="muted">{{ auth.user?.displayName }} · {{ auth.user?.roles?.join(', ') }}</span>
          <button class="icon" title="个人设置" @click="openProfileForm"><UserCog :size="18" /></button>
          <button class="icon" title="退出登录" @click="logout"><LogOut :size="18" /></button>
        </div>
      </header>
      <div v-if="showProfileForm" class="panel narrow" style="margin-bottom:18px">
        <h2>个人设置</h2>
        <p v-if="profileError" class="error">{{ profileError }}</p>
        <div class="form-grid" style="margin-top:14px">
          <label>显示名称 *<input v-model="profileForm.displayName" /></label>
          <label>电话<input v-model="profileForm.phone" /></label>
          <label>邮箱<input v-model="profileForm.email" type="email" /></label>
          <label>新密码<input v-model="profileForm.password" type="password" minlength="6" placeholder="留空不修改" /></label>
        </div>
        <div class="row-actions">
          <button class="primary" @click="saveProfile">保存</button>
          <button class="secondary" @click="showProfileForm = false">取消</button>
        </div>
      </div>
      <RouterView />
    </main>
  </div>
</template>
