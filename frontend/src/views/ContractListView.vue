<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { Search, FilePlus2, Eye, Pencil, Trash2, RefreshCcw } from 'lucide-vue-next'
import { api } from '../api'

const router = useRouter()
const contracts = ref([])
const loading = ref(false)
const error = ref('')
const keyword = ref('')
const statusFilter = ref('')
const page = ref(1)
const total = ref(0)
const pageSize = 10

const statusOptions = [
  { value: '', label: '全部状态' },
  { value: 'DRAFT', label: '起草' },
  { value: 'ASSIGNED', label: '已分配' },
  { value: 'COUNTERSIGNED', label: '会签完成' },
  { value: 'FINALIZED', label: '已定稿' },
  { value: 'APPROVED', label: '已审批' },
  { value: 'SIGNED', label: '已签订' },
  { value: 'REJECTED', label: '已拒绝' },
]

function statusLabel(status) {
  const map = { DRAFT:'起草', ASSIGNED:'已分配', COUNTERSIGNED:'会签完成', FINALIZED:'已定稿', APPROVED:'已审批', SIGNED:'已签订', REJECTED:'已拒绝', CANCELLED:'已取消' }
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
    const res = await api.get('/contracts', { params })
    contracts.value = res.data.records
    total.value = res.data.total
  } catch (err) {
    error.value = err.message
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  loadContracts()
}

function goPage(p) {
  page.value = p
  loadContracts()
}

function editContract(c) {
  router.push({ path: `/contracts/${c.id}`, query: { edit: '1' } })
}

async function deleteContract(c) {
  if (!confirm(`确认删除合同「${c.name}」？`)) return
  try {
    await api.delete(`/contracts/${c.id}`)
    loadContracts()
  } catch (err) {
    error.value = err.message
  }
}

onMounted(loadContracts)
</script>

<template>
  <div>
    <div class="section-title">
      <h2>合同管理</h2>
      <button class="primary" @click="router.push('/contracts/create')"><FilePlus2 :size="16" /> 起草合同</button>
    </div>

    <div class="search-bar">
      <div class="input" style="max-width:260px">
        <Search :size="16" />
        <input v-model="keyword" placeholder="合同编号/名称" @keyup.enter="search" />
      </div>
      <select v-model="statusFilter" @change="search" style="max-width:140px">
        <option v-for="opt in statusOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
      </select>
      <button class="secondary" @click="search">查询</button>
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
              <button v-if="c.status === 'DRAFT' || c.status === 'REJECTED'" @click="editContract(c)"><Pencil :size="14" /> 编辑</button>
              <button v-if="c.status === 'DRAFT' || c.status === 'CANCELLED'" @click="deleteContract(c)"><Trash2 :size="14" /> 删除</button>
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
