<script setup>
import { ref, computed, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import {
  ClipboardList, FilePlus2, UsersRound, Handshake, LogOut,
  LayoutDashboard, Settings, ShieldCheck, UserCog, ScrollText, ChevronDown
} from 'lucide-vue-next'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const menuItems = [
  { path: '/dashboard', label: '工作台', icon: LayoutDashboard },
  { path: '/contracts', label: '合同管理', icon: ClipboardList },
  { path: '/contracts/create', label: '起草合同', icon: FilePlus2 },
  { path: '/tasks', label: '我的待办', icon: Handshake },
  { path: '/customers', label: '客户管理', icon: UsersRound },
]

const sysItems = [
  { path: '/system/users', label: '用户管理', icon: UserCog },
  { path: '/system/roles', label: '角色管理', icon: ShieldCheck },
  { path: '/system/logs', label: '操作日志', icon: ScrollText },
]

const hasSystemAccess = computed(() =>
  auth.permissions.some(p => ['user:manage', 'role:manage', 'permission:manage', 'log:view'].includes(p))
)

const sysMenuOpen = ref(false)

watch(() => route.path, (path) => {
  if (path.startsWith('/system')) sysMenuOpen.value = true
})

function isActive(path) {
  if (path === '/contracts' && route.path.startsWith('/contracts')) return true
  return route.path === path
}

function logout() {
  auth.logout()
  router.push('/login')
}
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
          :class="{ selected: isActive(item.path) }"
          @click="router.push(item.path)"
        >
          <component :is="item.icon" :size="18" />
          {{ item.label }}
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
