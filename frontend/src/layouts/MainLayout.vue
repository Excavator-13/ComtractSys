<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import {
  ClipboardList, UsersRound, Handshake, LogOut,
  LayoutDashboard, Settings, ShieldCheck, UserCog, ScrollText, ChevronDown, Search
} from 'lucide-vue-next'
import { useAuthStore } from '../stores/auth'
import { api } from '../api'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const menuItems = [
  { path: '/dashboard', label: '工作台', icon: LayoutDashboard },
  { path: '/contracts', label: '合同管理', icon: ClipboardList, permission: 'contract:view' },
  { path: '/contracts/query', label: '合同查询', icon: Search, permission: 'contract:query' },
  { path: '/tasks', label: '我的待办', icon: Handshake, anyPermission: ['contract:assign', 'contract:countersign', 'contract:approve', 'contract:sign', 'contract:update'], badge: true },
  { path: '/customers', label: '客户管理', icon: UsersRound, permission: 'customer:manage' },
]

const sysItems = [
  { path: '/system/users', label: '用户管理', icon: UserCog, permission: 'user:manage' },
  { path: '/system/roles', label: '角色管理', icon: ShieldCheck, permission: 'role:manage' },
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
const pendingTaskCount = ref(0)
const canLoadTasks = computed(() =>
  auth.permissions.some(p => ['contract:assign', 'contract:countersign', 'contract:approve', 'contract:sign', 'contract:update'].includes(p))
)

watch(() => route.path, (path) => {
  if (path.startsWith('/system')) sysMenuOpen.value = true
})

function isActive(path) {
  if (path === '/contracts') return route.path === '/contracts' || route.path === '/contracts/create' || /^\/contracts\/\d+/.test(route.path)
  if (path === '/contracts/query') return route.path === '/contracts/query'
  return route.path === path
}

function logout() {
  auth.logout()
  router.push('/login')
}

async function loadPendingTaskCount() {
  if (!canLoadTasks.value) return
  try {
    const res = await api.get('/statistics/tasks/my')
    pendingTaskCount.value = res.data.pendingTasks || 0
  } catch {}
}

watch(() => route.fullPath, loadPendingTaskCount)
onMounted(loadPendingTaskCount)
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
          <button class="icon" title="退出登录" @click="logout"><LogOut :size="18" /></button>
        </div>
      </header>
      <RouterView />
    </main>
  </div>
</template>
