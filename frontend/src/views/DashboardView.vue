<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { FilePlus2, Handshake, RefreshCcw } from 'lucide-vue-next'
import { api } from '../api'
import { useAuthStore } from '../stores/auth'
import StatusBadge from '../components/StatusBadge.vue'
import { contractStatuses } from '../constants/contract'

const router = useRouter()
const auth = useAuthStore()
const contracts = ref([])
const tasks = ref([])
const stats = reactive({ total: 0, draft: 0, assigned: 0, signed: 0, rejected: 0, pendingTasks: 0 })
const monthlyStats = ref([])
const error = ref('')
const canViewContracts = computed(() => auth.permissions.includes('contract:view'))
const canCreateContract = computed(() => auth.permissions.includes('contract:create'))
const canViewTasks = computed(() =>
  auth.permissions.some(p => ['contract:assign', 'contract:countersign', 'contract:approve', 'contract:sign', 'contract:update'].includes(p))
)

async function loadData() {
  error.value = ''
  if (canViewContracts.value) {
    try {
      const [statisticsRes, _monthlyRes, contractsRes] = await Promise.all([
        api.get('/statistics'),
        api.get('/statistics/contracts/monthly').then(res => monthlyStats.value = res.data || []).catch(() => []),
        api.get('/contracts', { params: { page: 1, size: 10 } })
      ])
      Object.assign(stats, statisticsRes.data)
      contracts.value = contractsRes.data.records
    } catch (err) {
      error.value = err.message
    }
  }
  if (canViewTasks.value) {
    try {
      const res = await api.get('/tasks/my')
      tasks.value = res.data
    } catch {
      tasks.value = []
    }
  }
}

const inProgressCount = computed(() => {
  const active = ['ASSIGNED', 'COUNTERSIGNED', 'FINALIZED', 'APPROVED']
  return contracts.value.filter(c => active.includes(c.status)).length
})

const statusDistribution = computed(() => {
  const distribution = stats.statusDistribution || {}
  return contractStatuses
    .filter(item => item.value)
    .map(item => ({ ...item, count: distribution[item.value] || 0 }))
})

const monthlyMax = computed(() => Math.max(1, ...monthlyStats.value.map(item => item.count || 0)))

function openStatus(status) {
  router.push({ path: '/contracts', query: { status } })
}

onMounted(loadData)
</script>

<template>
  <div>
    <p v-if="error" class="error mb">{{ error }}</p>

    <section v-if="canViewContracts" class="metrics">
      <div><span>全部合同</span><strong>{{ stats.total }}</strong></div>
      <div><span>待分配</span><strong>{{ stats.draft }}</strong></div>
      <div><span>流转中</span><strong>{{ stats.assigned }}</strong></div>
      <div><span>已签订</span><strong>{{ stats.signed }}</strong></div>
    </section>

    <section v-if="canViewContracts || canViewTasks || canCreateContract" class="metrics">
      <div><span>待办任务</span><strong>{{ tasks.length }}</strong></div>
      <div v-if="canViewContracts"><span>已拒绝</span><strong>{{ stats.rejected }}</strong></div>
      <div class="quick-actions">
        <button v-if="canCreateContract" class="primary" @click="router.push('/contracts/create')"><FilePlus2 :size="16" /> 起草合同</button>
        <button v-if="tasks.length" class="secondary" @click="router.push('/tasks')"><Handshake :size="16" /> 处理待办 ({{ tasks.length }})</button>
      </div>
    </section>

    <section v-if="!canViewContracts && !canViewTasks && !canCreateContract" class="panel">
      <p class="muted" style="text-align:center;padding:32px">当前账号暂无业务权限，请联系管理员分配角色。</p>
    </section>

    <section v-if="canViewContracts" class="panel">
      <div class="section-title">
        <h2>状态分布</h2>
      </div>
      <div class="status-grid">
        <button v-for="item in statusDistribution" :key="item.value" class="status-card" @click="openStatus(item.value)">
          <StatusBadge :value="item.value" />
          <strong>{{ item.count }}</strong>
        </button>
      </div>
      <div class="section-title" style="margin-top:18px">
        <h2>月度合同量</h2>
      </div>
      <div v-if="monthlyStats.length" class="chart-bars">
        <div v-for="item in monthlyStats" :key="item.month" class="chart-bar">
          <span :style="{ height: `${Math.max(8, ((item.count || 0) / monthlyMax) * 120)}px` }"></span>
          <small>{{ item.month }}</small>
          <strong>{{ item.count }}</strong>
        </div>
      </div>
      <p v-else class="empty-hint">暂无月度统计</p>
    </section>

    <section v-if="canViewContracts" class="panel">
      <div class="section-title">
        <h2>最近合同</h2>
        <button class="secondary" @click="loadData"><RefreshCcw :size="16" /> 刷新</button>
      </div>
      <table>
        <thead>
          <tr><th>编号</th><th>名称</th><th>客户</th><th>状态</th><th>起草人</th></tr>
        </thead>
        <tbody>
          <tr v-for="c in contracts" :key="c.id" @click="router.push(`/contracts/${c.id}`)" style="cursor:pointer">
            <td>{{ c.contractNo }}</td>
            <td>{{ c.name }}</td>
            <td>{{ c.customerName }}</td>
            <td><StatusBadge :value="c.status" /></td>
            <td>{{ c.drafterName }}</td>
          </tr>
          <tr v-if="contracts.length === 0"><td colspan="5" class="muted" style="text-align:center">暂无合同</td></tr>
        </tbody>
      </table>
    </section>
  </div>
</template>
