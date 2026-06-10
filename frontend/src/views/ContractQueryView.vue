<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { Search, Eye, RefreshCcw, Download } from 'lucide-vue-next'
import { api } from '../api'

const router = useRouter()
const contracts = ref([])
const customers = ref([])
const loading = ref(false)
const error = ref('')
const keyword = ref('')
const statusFilter = ref('')
const customerId = ref('')
const beginFrom = ref('')
const beginTo = ref('')
const endFrom = ref('')
const endTo = ref('')
const page = ref(1)
const total = ref(0)
const pageSize = 10

const statusOptions = [
  { value: '', label: '全部状态' },
  { value: 'DRAFT', label: '待分配' },
  { value: 'ASSIGNED', label: '待会签' },
  { value: 'COUNTERSIGNED', label: '待定稿' },
  { value: 'FINALIZED', label: '待审批' },
  { value: 'APPROVED', label: '待签订' },
  { value: 'SIGNED', label: '已签订' },
  { value: 'REJECTED', label: '已拒绝' },
]

function statusLabel(status) {
  const map = { DRAFT:'待分配', ASSIGNED:'待会签', COUNTERSIGNED:'待定稿', FINALIZED:'待审批', APPROVED:'待签订', SIGNED:'已签订', REJECTED:'已拒绝', CANCELLED:'已取消' }
  return map[status] || status
}

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))

async function loadContracts() {
  loading.value = true
  error.value = ''
  try {
    const params = { page: page.value, size: pageSize }
    if (keyword.value) params.keyword = keyword.value
    if (statusFilter.value) params.status = statusFilter.value
    if (customerId.value) params.customerId = customerId.value
    if (beginFrom.value) params.beginFrom = beginFrom.value
    if (beginTo.value) params.beginTo = beginTo.value
    if (endFrom.value) params.endFrom = endFrom.value
    if (endTo.value) params.endTo = endTo.value
    const res = await api.get('/contracts/query', { params })
    contracts.value = res.data.records
    total.value = res.data.total
  } catch (err) {
    error.value = err.message
  } finally {
    loading.value = false
  }
}

async function loadCustomers() {
  try {
    const res = await api.get('/customers', { params: { page: 1, size: 200 } })
    customers.value = res.data.records
  } catch {}
}

function search() {
  page.value = 1
  loadContracts()
}

function goPage(p) {
  page.value = p
  loadContracts()
}

async function exportContracts() {
  error.value = ''
  try {
    const params = {}
    if (keyword.value) params.keyword = keyword.value
    if (statusFilter.value) params.status = statusFilter.value
    if (customerId.value) params.customerId = customerId.value
    if (beginFrom.value) params.beginFrom = beginFrom.value
    if (beginTo.value) params.beginTo = beginTo.value
    if (endFrom.value) params.endFrom = endFrom.value
    if (endTo.value) params.endTo = endTo.value
    const res = await api.get('/contracts/export', { params, responseType: 'blob' })
    const url = URL.createObjectURL(res.data)
    const link = document.createElement('a')
    link.href = url
    link.download = `contracts_${new Date().toISOString().slice(0, 10)}.csv`
    link.click()
    URL.revokeObjectURL(url)
  } catch (err) {
    error.value = err.message
  }
}

onMounted(() => {
  loadCustomers()
  loadContracts()
})
</script>

<template>
  <div>
    <div class="section-title">
      <h2>合同查询</h2>
    </div>

    <div class="search-bar">
      <div class="input" style="max-width:260px">
        <Search :size="16" />
        <input v-model="keyword" placeholder="合同编号/名称" @keyup.enter="search" />
      </div>
      <select v-model="statusFilter" @change="search" style="max-width:140px">
        <option v-for="opt in statusOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
      </select>
      <select v-model="customerId" @change="search" style="max-width:180px">
        <option value="">全部客户</option>
        <option v-for="c in customers" :key="c.id" :value="c.id">{{ c.name }}</option>
      </select>
      <div class="date-range-filter" aria-label="合同开始日期范围">
        <span>开始日期</span>
        <input v-model="beginFrom" type="date" title="开始日期从" @change="search" />
        <em>至</em>
        <input v-model="beginTo" type="date" title="开始日期至" @change="search" />
      </div>
      <div class="date-range-filter" aria-label="合同结束日期范围">
        <span>结束日期</span>
        <input v-model="endFrom" type="date" title="结束日期从" @change="search" />
        <em>至</em>
        <input v-model="endTo" type="date" title="结束日期至" @change="search" />
      </div>
      <button class="secondary" @click="search">查询</button>
      <button class="secondary" @click="exportContracts"><Download :size="16" /> 导出</button>
      <button class="icon" @click="loadContracts"><RefreshCcw :size="16" /></button>
    </div>

    <p v-if="error" class="error">{{ error }}</p>

    <div class="panel" style="margin-top:14px">
      <table>
        <thead>
          <tr><th>编号</th><th>名称</th><th>客户</th><th>状态</th><th>起草人</th><th>操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="c in contracts" :key="c.id">
            <td>{{ c.contractNo }}</td>
            <td>{{ c.name }}</td>
            <td>{{ c.customerName }}</td>
            <td><span class="status" :class="'status-' + c.status?.toLowerCase()">{{ statusLabel(c.status) }}</span></td>
            <td>{{ c.drafterName }}</td>
            <td class="row-actions">
              <button @click="router.push(`/contracts/${c.id}`)"><Eye :size="14" /> 详情</button>
            </td>
          </tr>
          <tr v-if="!loading && contracts.length === 0"><td colspan="6" class="muted" style="text-align:center">暂无数据</td></tr>
        </tbody>
      </table>
    </div>

    <div v-if="totalPages > 1" class="pagination">
      <button :disabled="page === 1" @click="goPage(page - 1)">上一页</button>
      <span>{{ page }} / {{ totalPages }}</span>
      <button :disabled="page >= totalPages" @click="goPage(page + 1)">下一页</button>
    </div>
  </div>
</template>
