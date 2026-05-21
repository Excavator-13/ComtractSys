<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { RefreshCcw, CheckCircle, XCircle, FileEdit, PenLine } from 'lucide-vue-next'
import { api } from '../api'

const router = useRouter()
const tasks = ref([])
const error = ref('')

function taskLabel(t) {
  const map = { COUNTERSIGN:'会签', APPROVAL:'审批', SIGN:'签订' }
  return map[t] || t
}

async function loadTasks() {
  error.value = ''
  try {
    const res = await api.get('/tasks/my')
    tasks.value = res.data
  } catch (err) {
    error.value = err.message
  }
}

async function handleCountersign(task) {
  const opinion = prompt('会签意见:')
  if (!opinion) return
  try {
    await api.post(`/contracts/${task.contractId}/countersign`, { opinion })
    loadTasks()
  } catch (err) {
    error.value = err.message
  }
}

async function handleApprove(task, result) {
  const opinion = prompt(result === 'APPROVED' ? '审批通过意见:' : '拒绝原因:')
  if (!opinion) return
  try {
    await api.post(`/contracts/${task.contractId}/approve`, { result, opinion })
    loadTasks()
  } catch (err) {
    error.value = err.message
  }
}

async function handleSign(task) {
  const signInfo = prompt('签订信息:')
  if (!signInfo) return
  try {
    await api.post(`/contracts/${task.contractId}/sign`, { signInfo, signedDate: new Date().toISOString().slice(0,10) })
    loadTasks()
  } catch (err) {
    error.value = err.message
  }
}

onMounted(loadTasks)
</script>

<template>
  <div class="narrow">
    <div class="section-title">
      <h2>我的待办</h2>
      <button class="secondary" @click="loadTasks"><RefreshCcw :size="16" /> 刷新</button>
    </div>

    <p v-if="error" class="error">{{ error }}</p>

    <div v-if="tasks.length === 0" class="panel">
      <p class="muted" style="text-align:center;padding:32px">暂无待办任务</p>
    </div>

    <div v-else class="panel">
      <table>
        <thead><tr><th>合同</th><th>任务类型</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="t in tasks" :key="t.id">
            <td>
              <a @click="router.push(`/contracts/${t.contractId}`)" style="cursor:pointer;color:#126f67;font-weight:700">
                {{ t.contractName }}
              </a>
            </td>
            <td>{{ taskLabel(t.taskType) }}</td>
            <td class="row-actions">
              <button v-if="t.taskType === 'COUNTERSIGN'" @click="handleCountersign(t)">
                <FileEdit :size="14" /> 会签
              </button>
              <button v-if="t.taskType === 'APPROVAL'" @click="handleApprove(t, 'APPROVED')">
                <CheckCircle :size="14" /> 通过
              </button>
              <button v-if="t.taskType === 'APPROVAL'" @click="handleApprove(t, 'REJECTED')">
                <XCircle :size="14" /> 拒绝
              </button>
              <button v-if="t.taskType === 'SIGN'" @click="handleSign(t)">
                <PenLine :size="14" /> 签订
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
