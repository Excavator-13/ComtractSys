import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from './stores/auth'
import LoginView from './views/LoginView.vue'
import RegisterView from './views/RegisterView.vue'
import MainLayout from './layouts/MainLayout.vue'
import DashboardView from './views/DashboardView.vue'
import ContractListView from './views/ContractListView.vue'
import ContractCreateView from './views/ContractCreateView.vue'
import ContractDetailView from './views/ContractDetailView.vue'
import CustomerListView from './views/CustomerListView.vue'
import MyTasksView from './views/MyTasksView.vue'
import UserManagementView from './views/system/UserManagementView.vue'
import RoleManagementView from './views/system/RoleManagementView.vue'
import LogView from './views/system/LogView.vue'

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
        { path: 'contracts', component: ContractListView, meta: { title: '合同管理' } },
        { path: 'contracts/create', component: ContractCreateView, meta: { title: '起草合同' } },
        { path: 'contracts/:id', component: ContractDetailView, meta: { title: '合同详情' } },
        { path: 'customers', component: CustomerListView, meta: { title: '客户管理' } },
        { path: 'tasks', component: MyTasksView, meta: { title: '我的待办' } },
        { path: 'system/users', component: UserManagementView, meta: { title: '用户管理' } },
        { path: 'system/roles', component: RoleManagementView, meta: { title: '角色管理' } },
        { path: 'system/logs', component: LogView, meta: { title: '操作日志' } },
      ]
    }
  ]
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.path !== '/login' && to.path !== '/register' && !auth.isLoggedIn) {
    return '/login'
  }
})

export default router
