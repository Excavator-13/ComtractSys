<script setup>
import { ref, computed, onMounted } from 'vue'
import { Search, RefreshCcw, Download } from 'lucide-vue-next'
import { api } from '../../api'
import StatusBadge from '../../components/StatusBadge.vue'
import { contractStatuses } from '../../constants/contract'

const logs = ref([])
const keyword = ref('')
const module = ref('')
const startDate = ref('')
const endDate = ref('')
const error = ref('')
const page = ref(1)
const total = ref(0)
const pageSize = 10
const modules = ['', 'CONTRACT', 'CUSTOMER', 'USER', 'ROLE', 'SYSTEM']
const statusValues = new Set(contractStatuses.map(item => item.value).filter(Boolean))

async function loadLogs() {
  error.value = ''
  try {
    const params = { page: page.value, size: pageSize }
    if (keyword.value) params.keyword = keyword.value
    if (module.value) params.module = module.value
    if (startDate.value) params.startDate = startDate.value
    if (endDate.value) params.endDate = endDate.value
    const res = await api.get('/logs', { params })
    logs.value = res.data.records
    total.value = res.data.total
  } catch (err) {
    error.value = err.message
  }
}

function search() {
  page.value = 1
  loadLogs()
}

async function exportLogs() {
  try {
    const params = {}
    if (keyword.value) params.keyword = keyword.value
    if (module.value) params.module = module.value
    if (startDate.value) params.startDate = startDate.value
    if (endDate.value) params.endDate = endDate.value
    const res = await api.get('/logs/export', { params, responseType: 'blob' })
    const url = URL.createObjectURL(res.data)
    const a = document.createElement('a')
    a.href = url
    a.download = `logs_${new Date().toISOString().slice(0,10)}.csv`
    a.click()
    URL.revokeObjectURL(url)
  } catch (err) {
    error.value = err.message
  }
}

function goPage(p) {
  page.value = p
  loadLogs()
}

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))

function parseStatusChange(content) {
  const text = content || ''
  const match = text.match(/\b([A-Z_]+|-)\s*->\s*([A-Z_]+)\b/)
  if (!match || !statusValues.has(match[2]) || (match[1] !== '-' && !statusValues.has(match[1]))) return null
  return {
    before: text.slice(0, match.index).trim(),
    from: match[1] === '-' ? '' : match[1],
    to: match[2],
    after: text.slice((match.index || 0) + match[0].length).trim()
  }
}

onMounted(loadLogs)
</script>

<template>
  <div>
    <div class="section-title">
      <h2>操作日志</h2>
      <div class="actions">
        <div class="input" style="max-width:220px">
          <Search :size="16" />
          <input v-model="keyword" placeholder="搜索模块/动作/操作人" @keyup.enter="search" />
        </div>
        <select v-model="module" @change="search">
          <option v-for="m in modules" :key="m" :value="m">{{ m || '全部模块' }}</option>
        </select>
        <div class="date-range-filter">
          <span>时间</span>
          <input v-model="startDate" type="date" @change="search" />
          <em>至</em>
          <input v-model="endDate" type="date" @change="search" />
        </div>
        <button class="secondary" @click="search"><Search :size="16" /> 筛选</button>
        <button class="secondary" @click="loadLogs"><RefreshCcw :size="16" /></button>
        <button class="primary" @click="exportLogs"><Download :size="16" /> 导出CSV</button>
      </div>
    </div>

    <p v-if="error" class="error">{{ error }}</p>

    <div class="panel">
      <table>
        <thead>
          <tr>
            <th>时间</th>
            <th>操作人</th>
            <th>模块</th>
            <th>动作</th>
            <th>对象</th>
            <th>内容</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="log in logs" :key="log.id">
            <td>{{ log.createdAt?.slice(0, 16) }}</td>
            <td>{{ log.operatorName || '-' }}</td>
            <td><span class="status">{{ log.module }}</span></td>
            <td>{{ log.action }}</td>
            <td>{{ log.targetType || '-' }}<span v-if="log.targetId"> #{{ log.targetId }}</span></td>
            <td>
              <template v-if="parseStatusChange(log.content)">
                <span v-if="parseStatusChange(log.content).before">{{ parseStatusChange(log.content).before }}</span>
                <StatusBadge v-if="parseStatusChange(log.content).from" :value="parseStatusChange(log.content).from" />
                <span class="muted"> -> </span>
                <StatusBadge :value="parseStatusChange(log.content).to" />
                <span v-if="parseStatusChange(log.content).after">{{ parseStatusChange(log.content).after }}</span>
              </template>
              <template v-else>{{ log.content || '-' }}</template>
            </td>
          </tr>
          <tr v-if="logs.length === 0">
            <td colspan="6" class="muted" style="text-align:center;padding:24px">暂无操作记录</td>
          </tr>
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
