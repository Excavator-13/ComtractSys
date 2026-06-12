<script setup>
import { ref, reactive, onMounted, computed, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Search, FilePlus2, Eye, Pencil, Trash2, RefreshCcw } from 'lucide-vue-next'
import { api } from '../api'
import { useAuthStore } from '../stores/auth'
import StatusBadge from '../components/StatusBadge.vue'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import { contractStatuses } from '../constants/contract'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const contracts = ref([])
const loading = ref(false)
const error = ref('')
const keyword = ref('')
const statusFilter = ref('')
const page = ref(1)
const total = ref(0)
const pageSize = 10
const confirmState = reactive({ show: false, title: '', message: '', action: null })

function dateRange(c) {
  return `${c.beginDate || '-'} ~ ${c.endDate || '-'}`
}

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))
const hasPermission = (permission) => auth.permissions.includes(permission)

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

function askConfirm(title, message, action) {
  confirmState.title = title
  confirmState.message = message
  confirmState.action = action
  confirmState.show = true
}

async function confirmDialogAction() {
  const action = confirmState.action
  confirmState.show = false
  confirmState.action = null
  if (action) await action()
}

function deleteContract(c) {
  askConfirm('删除合同', `确认删除合同「${c.name}」？`, async () => {
    try {
      await api.delete(`/contracts/${c.id}`)
      loadContracts()
    } catch (err) {
      error.value = err.message
    }
  })
}

watch(() => route.query.status, (status) => {
  statusFilter.value = typeof status === 'string' ? status : ''
  search()
})

onMounted(() => {
  statusFilter.value = typeof route.query.status === 'string' ? route.query.status : ''
  loadContracts()
})
</script>

<template>
  <div>
    <div class="section-title">
      <div>
        <h2>合同管理</h2>
        <p class="muted">我相关的合同：起草、参与处理或可分配管理的合同。</p>
      </div>
      <button v-if="hasPermission('contract:create')" class="primary" @click="router.push('/contracts/create')"><FilePlus2 :size="16" /> 起草合同</button>
    </div>

    <div class="search-bar">
      <div class="input" style="max-width:260px">
        <Search :size="16" />
        <input v-model="keyword" placeholder="合同编号/名称" @keyup.enter="search" />
      </div>
      <select v-model="statusFilter" @change="search" style="max-width:140px">
        <option v-for="opt in contractStatuses" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
      </select>
      <button class="secondary" @click="search">查询</button>
      <button class="icon" @click="loadContracts"><RefreshCcw :size="16" /></button>
    </div>

    <p v-if="error" class="error">{{ error }}</p>

    <div class="panel" style="margin-top:14px">
      <table>
        <thead>
          <tr><th>编号</th><th>名称</th><th>客户</th><th>状态</th><th>合同期限</th><th>起草人</th><th>操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="c in contracts" :key="c.id">
            <td>{{ c.contractNo }}</td>
            <td>{{ c.name }}</td>
            <td>{{ c.customerName }}</td>
            <td><StatusBadge :value="c.status" /></td>
            <td>{{ dateRange(c) }}</td>
            <td>{{ c.drafterName }}</td>
            <td class="row-actions">
              <button @click="router.push(`/contracts/${c.id}`)"><Eye :size="14" /> 详情</button>
              <button v-if="hasPermission('contract:update') && Number(c.drafterId) === Number(auth.user?.id) && (c.status === 'DRAFT' || c.status === 'COUNTERSIGNED' || c.status === 'REJECTED' || c.status === 'RETURNED')" @click="editContract(c)"><Pencil :size="14" /> 编辑</button>
              <button v-if="hasPermission('contract:delete') && (c.status === 'DRAFT' || c.status === 'CANCELLED')" @click="deleteContract(c)"><Trash2 :size="14" /> 删除</button>
            </td>
          </tr>
          <tr v-if="!loading && contracts.length === 0"><td colspan="7" class="muted" style="text-align:center">暂无数据</td></tr>
          <tr v-if="loading"><td colspan="7" class="muted" style="text-align:center">加载中...</td></tr>
        </tbody>
      </table>
    </div>

    <div v-if="totalPages > 1" class="pagination">
      <button :disabled="page === 1" @click="goPage(page - 1)">上一页</button>
      <span>{{ page }} / {{ totalPages }}</span>
      <button :disabled="page >= totalPages" @click="goPage(page + 1)">下一页</button>
    </div>

    <ConfirmDialog
      :show="confirmState.show"
      :title="confirmState.title"
      :message="confirmState.message"
      @confirm="confirmDialogAction"
      @cancel="confirmState.show = false"
    />
  </div>
</template>
