<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { FilePlus2, Handshake, FileCheck, AlertCircle, RefreshCcw } from 'lucide-vue-next'
import { api } from '../api'

const router = useRouter()
const contracts = ref([])
const tasks = ref([])
const stats = reactive({ total: 0, draft: 0, assigned: 0, signed: 0, rejected: 0, pendingTasks: 0 })
const error = ref('')

async function loadData() {
  error.value = ''
  try {
    const [statRes, contractRes, taskRes] = await Promise.all([
      api.get('/statistics'),
      api.get('/contracts', { params: { page: 1, size: 10 } }),
      api.get('/tasks/my')
    ])
    Object.assign(stats, statRes.data)
    contracts.value = contractRes.data.records
    tasks.value = taskRes.data
  } catch (err) {
    error.value = err.message
  }
}

function statusLabel(status) {
  const map = { DRAFT:'起草', ASSIGNED:'已分配', COUNTERSIGNED:'会签完成', FINALIZED:'已定稿', APPROVED:'已审批', SIGNED:'已签订', REJECTED:'已拒绝', CANCELLED:'已取消' }
  return map[status] || status
}

const inProgressCount = computed(() => {
  const active = ['ASSIGNED', 'COUNTERSIGNED', 'FINALIZED', 'APPROVED']
  return contracts.value.filter(c => active.includes(c.status)).length
})

onMounted(loadData)
</script>

<template>
  <div>
    <p v-if="error" class="error mb">{{ error }}</p>

    <section class="metrics">
      <div><span>全部合同</span><strong>{{ stats.total }}</strong></div>
      <div><span>待分配</span><strong>{{ stats.draft }}</strong></div>
      <div><span>流转中</span><strong>{{ stats.assigned }}</strong></div>
      <div><span>已签订</span><strong>{{ stats.signed }}</strong></div>
    </section>

    <section class="metrics">
      <div><span>待办任务</span><strong>{{ tasks.length }}</strong></div>
      <div><span>已拒绝</span><strong>{{ stats.rejected }}</strong></div>
      <div class="quick-actions">
        <button class="primary" @click="router.push('/contracts/create')"><FilePlus2 :size="16" /> 起草合同</button>
        <button v-if="tasks.length" class="secondary" @click="router.push('/tasks')"><Handshake :size="16" /> 处理待办 ({{ tasks.length }})</button>
      </div>
    </section>

    <section class="panel">
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
            <td><span class="status" :class="'status-' + c.status?.toLowerCase()">{{ statusLabel(c.status) }}</span></td>
            <td>{{ c.drafterName }}</td>
          </tr>
          <tr v-if="contracts.length === 0"><td colspan="5" class="muted" style="text-align:center">暂无合同</td></tr>
        </tbody>
      </table>
    </section>
  </div>
</template>
