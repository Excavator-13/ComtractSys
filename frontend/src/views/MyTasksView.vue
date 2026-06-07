<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { RefreshCcw, CheckCircle, XCircle, FileEdit, PenLine, UserCheck } from 'lucide-vue-next'
import { api } from '../api'

const router = useRouter()
const tasks = ref([])
const error = ref('')

function taskLabel(t) {
  const map = { ASSIGN:'分配', COUNTERSIGN:'会签', APPROVAL:'审批', FINALIZE:'定稿', SIGN:'签订' }
  return map[t] || t
}

const taskOrder = { ASSIGN: 1, COUNTERSIGN: 2, FINALIZE: 3, APPROVAL: 4, SIGN: 5 }
const groupedTasks = computed(() => {
  const groups = new Map()
  tasks.value.forEach(task => {
    if (!groups.has(task.contractId)) {
      groups.set(task.contractId, { contractId: task.contractId, contractName: task.contractName, tasks: [] })
    }
    groups.get(task.contractId).tasks.push(task)
  })
  return Array.from(groups.values()).map(group => {
    group.tasks.sort((a, b) => (taskOrder[a.taskType] || 99) - (taskOrder[b.taskType] || 99))
    group.current = group.tasks[0]
    return group
  })
})

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

function handleAssign(task) {
  router.push({ path: `/contracts/${task.contractId}`, query: { tab: 'assign' } })
}

async function handleFinalize(task) {
  const content = prompt('定稿内容:')
  if (!content) return
  try {
    await api.post(`/contracts/${task.contractId}/finalize`, { content })
    loadTasks()
  } catch (err) {
    error.value = err.message
  }
}

function openContract(task) {
  router.push(`/contracts/${task.contractId}`)
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

    <div v-else class="task-groups">
      <section v-for="group in groupedTasks" :key="group.contractId" class="panel task-card">
        <div>
          <button class="link-button" @click="openContract(group.current)">{{ group.contractName }}</button>
          <div class="muted" style="margin-top:6px">当前步骤：<strong>{{ taskLabel(group.current.taskType) }}</strong></div>
          <div v-if="group.tasks.length > 1" class="task-tags">
            <span v-for="t in group.tasks.slice(1)" :key="t.id" class="status">{{ taskLabel(t.taskType) }}</span>
          </div>
        </div>
        <div class="row-actions">
          <button v-if="group.current.taskType === 'ASSIGN'" @click="handleAssign(group.current)">
            <UserCheck :size="14" /> 分配
          </button>
          <button v-if="group.current.taskType === 'COUNTERSIGN'" @click="handleCountersign(group.current)">
            <FileEdit :size="14" /> 会签
          </button>
          <button v-if="group.current.taskType === 'FINALIZE'" @click="handleFinalize(group.current)">
            <FileEdit :size="14" /> 定稿
          </button>
          <button v-if="group.current.taskType === 'APPROVAL'" @click="handleApprove(group.current, 'APPROVED')">
            <CheckCircle :size="14" /> 通过
          </button>
          <button v-if="group.current.taskType === 'APPROVAL'" @click="handleApprove(group.current, 'REJECTED')">
            <XCircle :size="14" /> 拒绝
          </button>
          <button v-if="group.current.taskType === 'SIGN'" @click="handleSign(group.current)">
            <PenLine :size="14" /> 签订
          </button>
        </div>
      </section>
    </div>
  </div>
</template>
