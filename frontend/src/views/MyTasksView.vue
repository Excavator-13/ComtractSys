<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { RefreshCcw, CheckCircle, FileEdit, PenLine, UserCheck } from 'lucide-vue-next'
import { api } from '../api'
import PipelineStepper from '../components/PipelineStepper.vue'
import { taskLabel, taskOrder } from '../constants/contract'

const router = useRouter()
const tasks = ref([])
const error = ref('')

const taskStatusContractMap = {
  ASSIGN: 'DRAFT',
  COUNTERSIGN: 'ASSIGNED',
  FINALIZE: 'COUNTERSIGNED',
  APPROVAL: 'FINALIZED',
  SIGN: 'APPROVED'
}
function notifyTasksUpdated() {
  window.dispatchEvent(new CustomEvent('tasks-updated'))
}

const groupedTasks = computed(() => {
  const groups = new Map()
  const latestRoundByContract = new Map()
  tasks.value.forEach(task => {
    const round = Number(task.round || 1)
    latestRoundByContract.set(task.contractId, Math.max(latestRoundByContract.get(task.contractId) || 1, round))
  })
  tasks.value.filter(task => Number(task.round || 1) === latestRoundByContract.get(task.contractId)).forEach(task => {
    if (!groups.has(task.contractId)) {
      groups.set(task.contractId, { contractId: task.contractId, contractName: task.contractName, tasks: [] })
    }
    groups.get(task.contractId).tasks.push(task)
  })
  return Array.from(groups.values()).map(group => {
    group.tasks.sort((a, b) => (taskOrder[a.taskType] || 99) - (taskOrder[b.taskType] || 99))
    group.current = group.tasks[0]
    group.contract = {
      id: group.contractId,
      name: group.contractName,
      status: taskStatusContractMap[group.current?.taskType] || 'DRAFT',
      currentRound: group.current?.round || 1
    }
    return group
  })
})

async function loadTasks() {
  error.value = ''
  try {
    const res = await api.get('/tasks/my')
    tasks.value = res.data
    notifyTasksUpdated()
  } catch (err) {
    error.value = err.message
  }
}

function handleAssign(task) {
  router.push({ path: `/contracts/${task.contractId}`, query: { tab: 'assign' } })
}

function openAction(task) {
  const tabMap = {
    ASSIGN: 'assign',
    COUNTERSIGN: 'countersign',
    FINALIZE: 'finalize',
    APPROVAL: 'approve',
    SIGN: 'sign'
  }
  router.push({ path: `/contracts/${task.contractId}`, query: { tab: tabMap[task.taskType] || 'info' } })
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
          <PipelineStepper :contract="group.contract" :tasks="group.tasks" compact />
          <div v-if="group.tasks.length > 1" class="task-tags">
            <span v-for="t in group.tasks.slice(1)" :key="t.id" class="status">{{ taskLabel(t.taskType) }}</span>
          </div>
        </div>
        <div class="row-actions">
          <button v-if="group.current.taskType === 'ASSIGN'" @click="handleAssign(group.current)">
            <UserCheck :size="14" /> 分配
          </button>
          <button v-if="group.current.taskType === 'COUNTERSIGN'" @click="openAction(group.current)">
            <FileEdit :size="14" /> 会签
          </button>
          <button v-if="group.current.taskType === 'FINALIZE'" @click="openAction(group.current)">
            <FileEdit :size="14" /> 定稿
          </button>
          <button v-if="group.current.taskType === 'APPROVAL'" @click="openAction(group.current)">
            <CheckCircle :size="14" /> 审批
          </button>
          <button v-if="group.current.taskType === 'SIGN'" @click="openAction(group.current)">
            <PenLine :size="14" /> 签订
          </button>
        </div>
      </section>
    </div>
  </div>
</template>
