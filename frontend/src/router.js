import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from './stores/auth'
import LoginView from './views/LoginView.vue'
import RegisterView from './views/RegisterView.vue'
import MainLayout from './layouts/MainLayout.vue'
import DashboardView from './views/DashboardView.vue'
import ContractListView from './views/ContractListView.vue'
import ContractQueryView from './views/ContractQueryView.vue'
import ContractCreateView from './views/ContractCreateView.vue'
import ContractDetailView from './views/ContractDetailView.vue'
import CustomerListView from './views/CustomerListView.vue'
import MyTasksView from './views/MyTasksView.vue'
import UserManagementView from './views/system/UserManagementView.vue'
import RoleManagementView from './views/system/RoleManagementView.vue'
import LogView from './views/system/LogView.vue'

function hasAccess(required, permissions) {
  if (!required) return true
  if (Array.isArray(required)) return required.some(p => permissions.includes(p))
  return permissions.includes(required)
}

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/dashboard' },
    { path: '/login', component: LoginView },
    { path: '/register', component: RegisterView },
    {
      path: '/',
      component: MainLayout,
      children: [
        { path: 'dashboard', component: DashboardView, meta: { title: '工作台' } },
        { path: 'contracts', component: ContractListView, meta: { title: '合同管理', permission: 'contract:view' } },
        { path: 'contracts/query', component: ContractQueryView, meta: { title: '合同查询', permission: 'contract:query' } },
        { path: 'contracts/create', component: ContractCreateView, meta: { title: '起草合同', permission: 'contract:create' } },
        { path: 'contracts/:id', component: ContractDetailView, meta: { title: '合同详情', permission: ['contract:view', 'contract:query'] } },
        { path: 'customers', component: CustomerListView, meta: { title: '客户管理', permission: 'customer:manage' } },
        { path: 'tasks', component: MyTasksView, meta: { title: '我的待办', permission: ['contract:assign', 'contract:countersign', 'contract:approve', 'contract:sign', 'contract:update'] } },
        { path: 'system/users', component: UserManagementView, meta: { title: '用户管理', permission: 'user:manage' } },
        { path: 'system/roles', component: RoleManagementView, meta: { title: '角色管理', permission: 'role:manage' } },
        { path: 'system/logs', component: LogView, meta: { title: '操作日志', permission: 'log:view' } },
      ]
    }
  ]
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.path !== '/login' && to.path !== '/register' && !auth.isLoggedIn) {
    return '/login'
  }
  if (!hasAccess(to.meta.permission, auth.permissions)) {
    return '/dashboard'
  }
})

export default router
