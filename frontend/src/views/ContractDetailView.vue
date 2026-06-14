<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, UserCheck, Paperclip, Download, Trash2, Upload, RotateCcw, XCircle, Eye } from 'lucide-vue-next'
import { api } from '../api'
import { useAuthStore } from '../stores/auth'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import PipelineStepper from '../components/PipelineStepper.vue'
import StatusBadge from '../components/StatusBadge.vue'
import { contractStatusLabel, taskLabel, taskStatusLabel } from '../constants/contract'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const contract = ref(null)
const tasks = ref([])
const attachments = ref([])
const timeline = ref([])
const versions = ref([])
const customers = ref([])
const countersignUsers = ref([])
const approvalUsers = ref([])
const signUsers = ref([])
const error = ref('')
const success = ref('')
const activeTab = ref('info')
const requestedTab = ref('')
const uploading = ref(false)
const savingEdit = ref(false)
const hasPermission = (permission) => auth.permissions.includes(permission)

const editForm = reactive({
  name: '',
  customerId: '',
  beginDate: '',
  endDate: '',
  content: ''
})

const actionForm = reactive({
  type: '',
  opinion: '',
  content: '',
  signInfo: '',
  signedDate: new Date().toISOString().slice(0, 10),
  returnTarget: 'FINALIZE'
})
const confirmState = reactive({ show: false, title: '', message: '', action: null })

const assignForm = reactive({
  countersignUserIds: [],
  approvalUserIds: [],
  signUserId: ''
})

function returnTargetLabel(target) {
  const map = { DRAFT: '重新起草', FINALIZE: '重新定稿' }
  return map[target] || target || '-'
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

function fileSizeLabel(bytes) {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

function notifyTasksUpdated() {
  window.dispatchEvent(new CustomEvent('tasks-updated'))
}

function fillEditForm() {
  if (!contract.value) return
  editForm.name = contract.value.name || ''
  editForm.customerId = contract.value.customerId || ''
  editForm.beginDate = contract.value.beginDate || ''
  editForm.endDate = contract.value.endDate || ''
  editForm.content = contract.value.content || ''
}

function openAction(type) {
  actionForm.type = type
  actionForm.opinion = ''
  actionForm.content = contract.value?.content || ''
  actionForm.signInfo = ''
  actionForm.signedDate = new Date().toISOString().slice(0, 10)
  actionForm.returnTarget = type === 'COUNTERSIGN' ? 'DRAFT' : 'FINALIZE'
  activeTab.value = 'action'
}

async function loadDetail() {
  error.value = ''
  try {
    const res = await api.get(`/contracts/${route.params.id}`)
    contract.value = res.data.contract
    tasks.value = res.data.tasks || []
    fillEditForm()
    if (activeTab.value === 'action' && actionForm.type === 'FINALIZE') {
      actionForm.content = contract.value?.content || ''
    }
    normalizeRequestedTab()
  } catch (err) {
    error.value = err.message
  }
}

async function loadCustomers() {
  try {
    const res = await api.get('/customers', { params: { page: 1, size: 500 } })
    customers.value = res.data.records || []
  } catch {}
}

async function loadAttachments() {
  try {
    const res = await api.get(`/contracts/${route.params.id}/attachments`)
    attachments.value = res.data
  } catch {}
}

async function loadUsers() {
  if (!hasPermission('contract:assign')) return
  try {
    const [countersignRes, approvalRes, signRes] = await Promise.all([
      api.get('/users/assignable?permission=contract:countersign'),
      api.get('/users/assignable?permission=contract:approve'),
      api.get('/users/assignable?permission=contract:sign')
    ])
    countersignUsers.value = countersignRes.data
    approvalUsers.value = approvalRes.data
    signUsers.value = signRes.data
  } catch {}
}

async function assign() {
  try {
    if (!assignForm.countersignUserIds.length || !assignForm.approvalUserIds.length || !assignForm.signUserId) {
      error.value = '请选择会签人员、审批人员和签订人员'
      return
    }
    await api.post(`/contracts/${route.params.id}/assign`, {
      countersignUserIds: assignForm.countersignUserIds,
      approvalUserIds: assignForm.approvalUserIds,
      signUserId: Number(assignForm.signUserId)
    })
    success.value = '分配成功'
    await reloadWorkflow()
    notifyTasksUpdated()
    activeTab.value = 'info'
  } catch (err) {
    error.value = err.message
  }
}

async function saveEdit() {
  if (!editForm.name || !editForm.customerId || !editForm.content) {
    error.value = '请填写合同名称、客户和合同概述'
    return
  }
  savingEdit.value = true
  error.value = ''
  try {
    await api.put(`/contracts/${route.params.id}`, {
      name: editForm.name,
      customerId: Number(editForm.customerId),
      beginDate: editForm.beginDate,
      endDate: editForm.endDate,
      content: editForm.content
    })
    success.value = '合同已更新'
    await reloadWorkflow()
    await loadVersions()
    activeTab.value = 'info'
  } catch (err) {
    error.value = err.message
  } finally {
    savingEdit.value = false
  }
}

async function doCountersign() {
  if (!actionForm.opinion) {
    error.value = '请填写会签意见'
    return
  }
  try {
    await api.post(`/contracts/${route.params.id}/countersign`, { opinion: actionForm.opinion })
    success.value = '会签成功'
    await reloadWorkflow()
    notifyTasksUpdated()
    activeTab.value = 'rollback'
  } catch (err) {
    error.value = err.message
  }
}

async function doFinalize() {
  if (!actionForm.content) {
    error.value = '请填写定稿后的合同概述'
    return
  }
  try {
    await api.post(`/contracts/${route.params.id}/finalize`, { content: actionForm.content })
    success.value = '定稿成功'
    await reloadWorkflow()
    await loadVersions()
    notifyTasksUpdated()
    activeTab.value = 'rollback'
  } catch (err) {
    error.value = err.message
  }
}

async function doApprove(result) {
  if (!actionForm.opinion) {
    error.value = result === 'APPROVED' ? '请填写审批意见' : '请填写拒绝原因'
    return
  }
  try {
    await api.post(`/contracts/${route.params.id}/approve`, { result, opinion: actionForm.opinion })
    success.value = result === 'APPROVED' ? '审批通过' : '已拒绝'
    await reloadWorkflow()
    notifyTasksUpdated()
    activeTab.value = 'rollback'
  } catch (err) {
    error.value = err.message
  }
}

async function doSign() {
  if (!actionForm.signInfo) {
    error.value = '请填写签订信息'
    return
  }
  try {
    await api.post(`/contracts/${route.params.id}/sign`, { signInfo: actionForm.signInfo, signedDate: actionForm.signedDate })
    success.value = '签订成功'
    await reloadWorkflow()
    notifyTasksUpdated()
    activeTab.value = 'rollback'
  } catch (err) {
    error.value = err.message
  }
}

async function doReturn(targetStage = actionForm.returnTarget) {
  if (!actionForm.opinion) {
    error.value = '请填写打回原因'
    return
  }
  try {
    await api.post(`/contracts/${route.params.id}/return`, { targetStage, opinion: actionForm.opinion })
    success.value = '合同已打回'
    await reloadWorkflow()
    notifyTasksUpdated()
    activeTab.value = 'rollback'
  } catch (err) {
    error.value = err.message
  }
}

async function doResume() {
  askConfirm('恢复流程', `确认按「${returnTargetLabel(contract.value?.returnTargetStage)}」恢复流程？`, async () => {
  try {
    await api.post(`/contracts/${route.params.id}/resume`)
    success.value = '流程已恢复'
    await reloadWorkflow()
    notifyTasksUpdated()
    activeTab.value = 'rollback'
  } catch (err) {
    error.value = err.message
  }
  })
}

async function doRecall() {
  askConfirm('撤回合同', '确认撤回合同并回到待分配？', async () => {
  try {
    await api.post(`/contracts/${route.params.id}/recall`)
    success.value = '合同已撤回'
    await reloadWorkflow()
    notifyTasksUpdated()
    activeTab.value = 'rollback'
  } catch (err) {
    error.value = err.message
  }
  })
}

async function doWithdrawTask() {
  askConfirm('撤回任务', '确认撤回最近一次已处理任务？', async () => {
  try {
    await api.post(`/contracts/${route.params.id}/tasks/withdraw`)
    success.value = '任务已撤回'
    await reloadWorkflow()
    notifyTasksUpdated()
    activeTab.value = 'rollback'
  } catch (err) {
    error.value = err.message
  }
  })
}

async function loadTimeline() {
  try {
    const res = await api.get(`/contracts/${route.params.id}/timeline`)
    timeline.value = res.data
  } catch {}
}

async function loadVersions() {
  try {
    const res = await api.get(`/contracts/${route.params.id}/versions`)
    versions.value = res.data
  } catch {}
}

async function reloadWorkflow() {
  await loadDetail()
  await loadTimeline()
}

async function doCancel() {
  askConfirm('取消合同', '确认取消该合同？取消后会关闭所有待办任务。', async () => {
  try {
    await api.post(`/contracts/${route.params.id}/cancel`)
    success.value = '合同已取消'
    await reloadWorkflow()
    notifyTasksUpdated()
  } catch (err) {
    error.value = err.message
  }
  })
}

async function handleUpload(e) {
  const file = e.target.files[0]
  if (!file) return
  uploading.value = true
  error.value = ''
  try {
    const formData = new FormData()
    formData.append('file', file)
    await api.post(`/contracts/${route.params.id}/attachments`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    success.value = '文件上传成功'
    await loadAttachments()
  } catch (err) {
    error.value = err.message
  } finally {
    uploading.value = false
  }
}

async function downloadAttachment(a) {
  try {
    const res = await api.get(`/attachments/${a.id}/download`, { responseType: 'blob' })
    const url = URL.createObjectURL(res.data)
    const aEl = document.createElement('a')
    aEl.href = url
    aEl.download = a.originalName
    aEl.click()
    URL.revokeObjectURL(url)
  } catch (err) {
    error.value = err.message
  }
}

async function deleteAttachment(a) {
  askConfirm('删除附件', `删除附件「${a.originalName}」？`, async () => {
  try {
    await api.delete(`/attachments/${a.id}`)
    await loadAttachments()
  } catch (err) {
    error.value = err.message
  }
  })
}

const excludeDrafter = (list) => list.filter(u => Number(u.id) !== Number(contract.value?.drafterId))
const assignableCountersignUsers = computed(() => excludeDrafter(countersignUsers.value))
const assignableApprovalUsers = computed(() => excludeDrafter(approvalUsers.value))
const assignableSignUsers = computed(() => excludeDrafter(signUsers.value))
const pendingTask = (type) => tasks.value.some(t => t.taskType === type && t.taskStatus === 'PENDING' && Number(t.assigneeId) === Number(auth.user?.id))
const canAssignCurrent = computed(() => hasPermission('contract:assign') && contract.value?.status === 'DRAFT' && pendingTask('ASSIGN'))
const canCountersignCurrent = computed(() => hasPermission('contract:countersign') && contract.value?.status === 'ASSIGNED' && pendingTask('COUNTERSIGN'))
const canFinalizeCurrent = computed(() => hasPermission('contract:update') && contract.value?.status === 'COUNTERSIGNED' && pendingTask('FINALIZE'))
const canApproveCurrent = computed(() => hasPermission('contract:approve') && contract.value?.status === 'FINALIZED' && pendingTask('APPROVAL'))
const canSignCurrent = computed(() => hasPermission('contract:sign') && contract.value?.status === 'APPROVED' && pendingTask('SIGN'))
const canCancelCurrent = computed(() => hasPermission('contract:delete') && !['SIGNED', 'CANCELLED'].includes(contract.value?.status))
const canModifyAttachments = computed(() =>
  hasPermission('contract:update') &&
  Number(contract.value?.drafterId) === Number(auth.user?.id) &&
  contract.value?.status === 'DRAFT'
)
const canEditCurrent = computed(() => hasPermission('contract:update') && Number(contract.value?.drafterId) === Number(auth.user?.id) && ['DRAFT', 'COUNTERSIGNED', 'RETURNED'].includes(contract.value?.status))
const canReturnCurrent = computed(() =>
  (canCountersignCurrent.value || canApproveCurrent.value || canSignCurrent.value) && contract.value?.status !== 'RETURNED'
)
const canResumeCurrent = computed(() =>
  hasPermission('contract:update') &&
  Number(contract.value?.drafterId) === Number(auth.user?.id) &&
  (contract.value?.status === 'RETURNED' || (contract.value?.status === 'DRAFT' && contract.value?.returnTargetStage === 'DRAFT'))
)
const canRecallCurrent = computed(() => hasPermission('contract:update') && contract.value?.status === 'ASSIGNED' && Number(contract.value?.drafterId) === Number(auth.user?.id))
const currentRoundTasks = computed(() => tasks.value.filter(t => Number(t.round || 1) === Number(contract.value?.currentRound || 1)))
const canWithdrawCurrent = computed(() => currentRoundTasks.value.some(isWithdrawableTask))
const taskRounds = computed(() => {
  const groups = new Map()
  visibleTasks.value.forEach(task => {
    const round = task.round || 1
    if (!groups.has(round)) groups.set(round, [])
    groups.get(round).push(task)
  })
  return Array.from(groups.entries())
    .sort((a, b) => b[0] - a[0])
    .map(([round, roundTasks]) => ({
      round,
      current: round === (contract.value?.currentRound || 1),
      tasks: roundTasks.sort((a, b) => {
      const order = { ASSIGN: 1, COUNTERSIGN: 2, REVISE: 3, FINALIZE: 4, APPROVAL: 5, SIGN: 6 }
        return (order[a.taskType] || 99) - (order[b.taskType] || 99)
      })
    }))
})
const visibleTasks = computed(() => tasks.value.filter(t => t.taskStatus !== 'SUPERSEDED'))
const countersignOpinions = computed(() => visibleTasks.value.filter(t => t.taskType === 'COUNTERSIGN' && t.opinion))
const approvalOpinions = computed(() => visibleTasks.value.filter(t => t.taskType === 'APPROVAL' && t.opinion))
const actionTitle = computed(() => {
  const map = { COUNTERSIGN: '会签处理', FINALIZE: '定稿处理', APPROVAL: '审批处理', SIGN: '签订处理' }
  return map[actionForm.type] || '流程处理'
})

function hasTaskPermission(taskType) {
  const map = {
    COUNTERSIGN: 'contract:countersign',
    FINALIZE: 'contract:update',
    APPROVAL: 'contract:approve'
  }
  return map[taskType] ? hasPermission(map[taskType]) : false
}

function hasCompletedCurrentTask(type) {
  return currentRoundTasks.value.some(t => t.taskType === type && ['DONE', 'REJECTED'].includes(t.taskStatus))
}

function isWithdrawableTask(task) {
  if (!contract.value || !hasTaskPermission(task.taskType)) return false
  if (Number(task.assigneeId) !== Number(auth.user?.id)) return false
  if (!['DONE', 'REJECTED'].includes(task.taskStatus)) return false
  if (task.taskType === 'SIGN' || contract.value.status === 'SIGNED') return false
  if (task.taskType === 'COUNTERSIGN' && hasCompletedCurrentTask('FINALIZE')) return false
  if (task.taskType === 'FINALIZE' && hasCompletedCurrentTask('APPROVAL')) return false
  if (task.taskType === 'APPROVAL' && hasCompletedCurrentTask('SIGN')) return false
  return true
}

function canOpenRequestedAction() {
  if (actionForm.type === 'COUNTERSIGN') return canCountersignCurrent.value
  if (actionForm.type === 'FINALIZE') return canFinalizeCurrent.value
  if (actionForm.type === 'APPROVAL') return canApproveCurrent.value
  if (actionForm.type === 'SIGN') return canSignCurrent.value
  return false
}

function normalizeRequestedTab() {
  if (!requestedTab.value || requestedTab.value === 'rollback') return
  if (activeTab.value === 'edit' && !canEditCurrent.value) {
    activeTab.value = 'info'
    success.value = '该任务已被处理或当前无权编辑'
  } else if (activeTab.value === 'assign' && !canAssignCurrent.value) {
    activeTab.value = 'info'
    success.value = '该分配任务已被处理或当前不可分配'
  } else if (activeTab.value === 'action' && !canOpenRequestedAction()) {
    activeTab.value = 'info'
    success.value = '该任务已被处理或当前不可操作'
  }
}

function userName(user) {
  return user.displayName || user.username
}

async function previewAttachment(a) {
  try {
    const res = await api.get(`/attachments/${a.id}/preview`, { responseType: 'blob' })
    const url = URL.createObjectURL(res.data)
    window.open(url, '_blank', 'noopener')
    setTimeout(() => URL.revokeObjectURL(url), 60000)
  } catch (err) {
    error.value = err.message
  }
}

function selectedUsers(field) {
  const source = field === 'countersignUserIds' ? assignableCountersignUsers.value : assignableApprovalUsers.value
  return source.filter(u => assignForm[field].includes(u.id))
}

function selectedSignUser() {
  return assignableSignUsers.value.find(u => u.id === assignForm.signUserId)
}

function toggleUser(field, userId) {
  const list = assignForm[field]
  const idx = list.indexOf(userId)
  if (idx >= 0) list.splice(idx, 1)
  else list.push(userId)
}

function removeUser(field, userId) {
  const list = assignForm[field]
  const idx = list.indexOf(userId)
  if (idx >= 0) list.splice(idx, 1)
}

function canPreview(a) {
  return /\.(pdf|jpg|jpeg|png|gif|bmp)$/i.test(a.originalName || '')
}

onMounted(() => {
  requestedTab.value = route.query.edit === '1' ? 'edit' : (route.query.tab || '')
  if (requestedTab.value === 'edit') activeTab.value = 'edit'
  else if (requestedTab.value === 'assign') activeTab.value = 'assign'
  else if (requestedTab.value === 'countersign') { actionForm.type = 'COUNTERSIGN'; actionForm.returnTarget = 'DRAFT'; activeTab.value = 'action' }
  else if (requestedTab.value === 'finalize') { actionForm.type = 'FINALIZE'; activeTab.value = 'action' }
  else if (requestedTab.value === 'approve') { actionForm.type = 'APPROVAL'; actionForm.returnTarget = 'FINALIZE'; activeTab.value = 'action' }
  else if (requestedTab.value === 'sign') { actionForm.type = 'SIGN'; actionForm.returnTarget = 'FINALIZE'; activeTab.value = 'action' }
  else if (requestedTab.value === 'rollback') activeTab.value = 'rollback'
  else if (requestedTab.value) activeTab.value = 'info'
  loadDetail()
  loadCustomers()
  loadUsers()
  loadAttachments()
  loadTimeline()
  loadVersions()
})
</script>

<template>
  <div class="narrow">
    <button class="secondary" style="margin-bottom:16px" @click="router.back()">
      <ArrowLeft :size="16" /> 返回合同列表
    </button>

    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="success" class="success-msg">{{ success }}</p>

    <div v-if="contract" class="panel">
      <div class="section-title">
        <h2>{{ contract.name }}</h2>
        <StatusBadge :value="contract.status" />
      </div>

      <PipelineStepper :contract="contract" :tasks="tasks" />

      <div class="tabs">
        <button :class="{ selected: activeTab === 'info' }" @click="activeTab = 'info'">基础信息</button>
        <button :class="{ selected: activeTab === 'tasks' }" @click="activeTab = 'tasks'">流程任务</button>
        <button :class="{ selected: activeTab === 'timeline' }" @click="activeTab = 'timeline'">流程时间线</button>
        <button :class="{ selected: activeTab === 'versions' }" @click="activeTab = 'versions'">版本历史</button>
        <button :class="{ selected: activeTab === 'rollback' }" @click="activeTab = 'rollback'">回退模型</button>
        <button v-if="canCountersignCurrent || canFinalizeCurrent || canApproveCurrent || canSignCurrent" class="transient-tab" :class="{ selected: activeTab === 'action' }" @click="openAction(canCountersignCurrent ? 'COUNTERSIGN' : canFinalizeCurrent ? 'FINALIZE' : canApproveCurrent ? 'APPROVAL' : 'SIGN')">当前处理</button>
        <button :class="{ selected: activeTab === 'attachments' }" @click="activeTab = 'attachments'">
          附件 ({{ attachments.length }})
        </button>
        <button v-if="canAssignCurrent" class="transient-tab" :class="{ selected: activeTab === 'assign' }" @click="activeTab = 'assign'">分配人员</button>
        <button v-if="canEditCurrent" class="transient-tab" :class="{ selected: activeTab === 'edit' }" @click="activeTab = 'edit'">编辑合同</button>
      </div>

      <div v-if="activeTab === 'info'" class="tab-content">
        <dl class="detail-grid">
          <div><dt>合同编号</dt><dd>{{ contract.contractNo }}</dd></div>
          <div><dt>合同名称</dt><dd>{{ contract.name }}</dd></div>
          <div><dt>客户</dt><dd>{{ contract.customerName }}</dd></div>
          <div><dt>状态</dt><dd><StatusBadge :value="contract.status" /></dd></div>
          <div><dt>起草人</dt><dd>{{ contract.drafterName }}</dd></div>
          <div><dt>开始日期</dt><dd>{{ contract.beginDate }}</dd></div>
          <div><dt>结束日期</dt><dd>{{ contract.endDate }}</dd></div>
          <div v-if="contract.signedDate"><dt>签订日期</dt><dd>{{ contract.signedDate }}</dd></div>
          <div v-if="contract.signInfo"><dt>签订信息</dt><dd>{{ contract.signInfo }}</dd></div>
        </dl>
        <div style="margin-top:16px">
          <h4>合同概述</h4>
          <pre class="content-box">{{ contract.content }}</pre>
        </div>

        <div class="row-actions" style="margin-top:16px">
          <button v-if="canAssignCurrent" @click="activeTab = 'assign'">
            <UserCheck :size="14" /> 分配人员
          </button>
          <button v-if="canEditCurrent" @click="activeTab = 'edit'">编辑合同</button>
          <button v-if="canFinalizeCurrent" @click="openAction('FINALIZE')">定稿</button>
          <button v-if="canApproveCurrent" @click="openAction('APPROVAL')">审批</button>
          <button v-if="canCountersignCurrent" @click="openAction('COUNTERSIGN')">会签</button>
          <button v-if="canSignCurrent" @click="openAction('SIGN')">签订</button>
          <button v-if="canResumeCurrent" @click="doResume">
            <RotateCcw :size="14" /> 恢复流程
          </button>
          <button v-if="canRecallCurrent" @click="doRecall">撤回合同</button>
          <button v-if="canWithdrawCurrent" @click="doWithdrawTask">撤回任务</button>
          <button v-if="canCancelCurrent" @click="doCancel" style="color:#b42318">
            <XCircle :size="14" /> 取消合同
          </button>
        </div>
      </div>

      <div v-if="activeTab === 'edit' && canEditCurrent" class="tab-content">
        <div class="form-grid">
          <label>合同名称<input v-model="editForm.name" required /></label>
          <label>客户
            <select v-model="editForm.customerId" required>
              <option value="" disabled>选择客户</option>
              <option v-for="c in customers" :key="c.id" :value="c.id">{{ c.name }}</option>
            </select>
          </label>
          <label>开始日期<input v-model="editForm.beginDate" type="date" required /></label>
          <label>结束日期<input v-model="editForm.endDate" type="date" required /></label>
          <label class="full">合同概述<textarea v-model="editForm.content" rows="8" required /></label>
        </div>
        <div class="row-actions" style="margin-top:16px">
          <button class="primary" :disabled="savingEdit" @click="saveEdit">{{ savingEdit ? '保存中...' : '保存修改' }}</button>
          <button class="secondary" type="button" @click="fillEditForm(); activeTab = 'info'">取消</button>
        </div>
      </div>

      <div v-if="activeTab === 'action'" class="tab-content">
        <div class="assign-section-head">
          <h3>{{ actionTitle }}</h3>
          <span class="muted">{{ contract.contractNo }} · 第 {{ contract.currentRound || 1 }} 轮</span>
        </div>

        <div class="detail-grid" style="margin:16px 0">
          <div><dt>合同名称</dt><dd>{{ contract.name }}</dd></div>
          <div><dt>客户</dt><dd>{{ contract.customerName }}</dd></div>
          <div><dt>状态</dt><dd><StatusBadge :value="contract.status" /></dd></div>
          <div><dt>起草人</dt><dd>{{ contract.drafterName }}</dd></div>
        </div>

        <section v-if="countersignOpinions.length" class="version-item" style="margin-bottom:14px">
          <div class="assign-section-head"><h3>会签意见</h3></div>
          <p v-for="t in countersignOpinions" :key="'cs-op-' + t.id" class="muted">
            {{ t.assigneeName }} · 第 {{ t.round || 1 }} 轮：{{ t.opinion }}
          </p>
        </section>

        <section v-if="approvalOpinions.length && (actionForm.type === 'SIGN' || actionForm.type === 'APPROVAL')" class="version-item" style="margin-bottom:14px">
          <div class="assign-section-head"><h3>审批意见</h3></div>
          <p v-for="t in approvalOpinions" :key="'ap-op-' + t.id" class="muted">
            {{ t.assigneeName }} · 第 {{ t.round || 1 }} 轮：{{ t.opinion }}
          </p>
        </section>

        <div style="margin-bottom:14px">
          <h4>合同概述</h4>
          <textarea v-if="actionForm.type === 'FINALIZE'" v-model="actionForm.content" rows="8" />
          <pre v-else class="content-box">{{ contract.content }}</pre>
        </div>

        <div v-if="attachments.length" style="margin-bottom:14px">
          <h4>附件</h4>
          <div class="attachment-list">
            <div v-for="a in attachments" :key="'action-att-' + a.id" class="attachment-item">
              <Paperclip :size="16" />
              <span>{{ a.originalName }}</span>
              <small>{{ fileSizeLabel(a.fileSize) }}</small>
              <button class="icon mini" type="button" @click="downloadAttachment(a)" title="下载附件">
                <Download :size="14" />
              </button>
            </div>
          </div>
        </div>

        <div v-if="actionForm.type === 'COUNTERSIGN' || actionForm.type === 'APPROVAL'" class="form-grid single">
          <label class="full">{{ actionForm.type === 'COUNTERSIGN' ? '会签意见' : '审批意见' }}
            <textarea v-model="actionForm.opinion" rows="5" required />
          </label>
        </div>

        <div v-if="actionForm.type === 'SIGN'" class="form-grid">
          <label>签订日期<input v-model="actionForm.signedDate" type="date" required /></label>
          <label class="full">签订信息<textarea v-model="actionForm.signInfo" rows="5" required /></label>
        </div>

        <div v-if="canReturnCurrent" class="form-grid single" style="margin-top:10px">
          <label class="full">打回目标
            <select v-model="actionForm.returnTarget">
              <option v-if="actionForm.type !== 'COUNTERSIGN'" value="FINALIZE">重新定稿</option>
              <option value="DRAFT">重新起草</option>
            </select>
          </label>
          <label v-if="actionForm.type === 'SIGN'" class="full">打回原因
            <textarea v-model="actionForm.opinion" rows="4" />
          </label>
        </div>

        <div class="row-actions" style="margin-top:16px">
          <button v-if="actionForm.type === 'COUNTERSIGN'" class="primary" @click="doCountersign">提交会签</button>
          <button v-if="actionForm.type === 'FINALIZE'" class="primary" @click="doFinalize">提交定稿</button>
          <button v-if="actionForm.type === 'APPROVAL'" class="primary" @click="doApprove('APPROVED')">审批通过</button>
          <button v-if="actionForm.type === 'APPROVAL'" @click="doApprove('REJECTED')">审批拒绝</button>
          <button v-if="actionForm.type === 'SIGN'" class="primary" @click="doSign">提交签订</button>
          <button v-if="canReturnCurrent" style="color:#b42318" @click="doReturn()">打回</button>
          <button class="secondary" type="button" @click="activeTab = 'info'">取消</button>
        </div>
      </div>

      <div v-if="activeTab === 'tasks'" class="tab-content">
        <div v-if="visibleTasks.length === 0" class="muted" style="text-align:center;padding:24px">暂无流程任务</div>
        <table v-else>
          <thead><tr><th>类型</th><th>处理人</th><th>状态</th><th>意见</th><th>时间</th></tr></thead>
          <tbody>
            <tr v-for="t in visibleTasks" :key="t.id">
              <td>{{ taskLabel(t.taskType) }}</td>
              <td>{{ t.assigneeName }}</td>
              <td>
                <span :class="t.taskStatus === 'DONE' ? 'status status-SIGNED' : t.taskStatus === 'REJECTED' ? 'status status-REJECTED' : 'status'">
                  {{ taskStatusLabel(t.taskStatus) }}
                </span>
              </td>
              <td>{{ t.opinion || '-' }}</td>
              <td>{{ t.operatedAt?.slice(0, 16) || '-' }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-if="activeTab === 'timeline'" class="tab-content">
        <div v-if="timeline.length === 0" class="muted" style="text-align:center;padding:24px">暂无流程记录</div>
        <ol v-else class="timeline-list">
          <li v-for="item in timeline" :key="item.id">
            <div class="timeline-dot"></div>
            <div>
              <strong>{{ contractStatusLabel(item.fromStatus) }} -> {{ contractStatusLabel(item.toStatus) }}</strong>
              <p>{{ item.remark || '-' }}</p>
              <span>{{ item.operatorName }} · {{ item.createdAt?.slice(0, 16) }}</span>
            </div>
          </li>
        </ol>
      </div>

      <div v-if="activeTab === 'versions'" class="tab-content">
        <div v-if="versions.length === 0" class="muted" style="text-align:center;padding:24px">暂无版本记录</div>
        <div v-else class="version-list">
          <article v-for="v in versions" :key="v.id" class="version-item">
            <div class="assign-section-head">
              <h3>V{{ v.versionNo }} · {{ v.name }}</h3>
              <span class="muted">{{ v.operatorName }} · {{ v.createdAt?.slice(0, 16) }}</span>
            </div>
            <p class="muted">{{ v.remark }}</p>
            <pre class="content-box">{{ v.content }}</pre>
          </article>
        </div>
      </div>

      <div v-if="activeTab === 'rollback'" class="tab-content">
        <div class="detail-grid" style="margin-bottom:16px">
          <div><dt>当前轮次</dt><dd>第 {{ contract.currentRound || 1 }} 轮</dd></div>
          <div><dt>回退目标</dt><dd>{{ returnTargetLabel(contract.returnTargetStage) }}</dd></div>
          <div><dt>当前状态</dt><dd><StatusBadge :value="contract.status" /></dd></div>
        </div>

        <div v-if="taskRounds.length === 0" class="muted" style="text-align:center;padding:24px">暂无轮次任务</div>
        <div v-else class="version-list">
          <article v-for="round in taskRounds" :key="round.round" class="version-item">
            <div class="assign-section-head">
              <h3>第 {{ round.round }} 轮</h3>
              <span class="status" :class="round.current ? 'status-assigned' : ''">{{ round.current ? '当前轮' : '历史轮' }}</span>
            </div>
            <table>
              <thead><tr><th>环节</th><th>处理人</th><th>状态</th><th>意见/原因</th><th>时间</th></tr></thead>
              <tbody>
                <tr v-for="t in round.tasks" :key="'round-' + round.round + '-' + t.id">
                  <td>{{ taskLabel(t.taskType) }}</td>
                  <td>{{ t.assigneeName }}</td>
                  <td>{{ taskStatusLabel(t.taskStatus) }}</td>
                  <td>{{ t.opinion || '-' }}</td>
                  <td>{{ t.operatedAt?.slice(0, 16) || '-' }}</td>
                </tr>
              </tbody>
            </table>
          </article>
        </div>
      </div>

      <div v-if="activeTab === 'attachments'" class="tab-content">
        <div style="margin-bottom:14px">
          <label v-if="canModifyAttachments" class="secondary" style="display:inline-flex;cursor:pointer;min-height:38px;align-items:center;gap:8px;padding:0 14px;border-radius:6px;font-weight:700">
            <Upload :size="16" />
            {{ uploading ? '上传中...' : '选择文件' }}
            <input type="file" hidden accept=".doc,.docx,.jpg,.jpeg,.png,.bmp,.gif,.pdf" @change="handleUpload" :disabled="uploading" />
          </label>
          <span class="muted" style="margin-left:10px;font-size:13px">支持 doc/docx/jpg/png/pdf，最大 10MB</span>
        </div>

        <div v-if="attachments.length === 0" class="muted" style="text-align:center;padding:24px">暂无附件</div>
        <table v-else>
          <thead><tr><th>文件名</th><th>大小</th><th>上传人</th><th>时间</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="a in attachments" :key="a.id">
              <td><Paperclip :size="14" style="margin-right:6px" />{{ a.originalName }}</td>
              <td>{{ fileSizeLabel(a.fileSize) }}</td>
              <td>{{ a.uploaderName }}</td>
              <td>{{ a.uploadedAt?.slice(0, 16) }}</td>
              <td class="row-actions">
                <button v-if="canPreview(a)" @click="previewAttachment(a)"><Eye :size="14" /> 预览</button>
                <button @click="downloadAttachment(a)"><Download :size="14" /> 下载</button>
                <button v-if="canModifyAttachments" @click="deleteAttachment(a)"><Trash2 :size="14" /> 删除</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-if="activeTab === 'assign' && canAssignCurrent" class="tab-content">
        <div class="assignment-panel">
          <section class="assign-section">
            <div class="assign-section-head">
              <h3>会签人员</h3>
              <span class="muted">可多选，全部完成后进入定稿</span>
            </div>
            <div class="tag-picker">
              <div class="selected-tags">
                <span v-if="selectedUsers('countersignUserIds').length === 0" class="tag-placeholder">请选择会签人员</span>
                <button
                  v-for="u in selectedUsers('countersignUserIds')"
                  :key="'counter-tag-' + u.id"
                  type="button"
                  class="selected-tag"
                  @click="removeUser('countersignUserIds', u.id)"
                >
                  {{ userName(u) }} <span aria-hidden="true">x</span>
                </button>
              </div>
              <div class="checkbox-list">
                <label v-for="u in assignableCountersignUsers" :key="'counter-' + u.id" class="check-option">
                  <input
                    type="checkbox"
                    :checked="assignForm.countersignUserIds.includes(u.id)"
                    @change="toggleUser('countersignUserIds', u.id)"
                  />
                  <span>{{ userName(u) }}</span>
                  <small>{{ u.username }}</small>
                </label>
              </div>
            </div>
          </section>

          <section class="assign-section">
            <div class="assign-section-head">
              <h3>审批人员</h3>
              <span class="muted">可多选，全部通过后进入签订</span>
            </div>
            <div class="tag-picker">
              <div class="selected-tags">
                <span v-if="selectedUsers('approvalUserIds').length === 0" class="tag-placeholder">请选择审批人员</span>
                <button
                  v-for="u in selectedUsers('approvalUserIds')"
                  :key="'approval-tag-' + u.id"
                  type="button"
                  class="selected-tag"
                  @click="removeUser('approvalUserIds', u.id)"
                >
                  {{ userName(u) }} <span aria-hidden="true">x</span>
                </button>
              </div>
              <div class="checkbox-list">
                <label v-for="u in assignableApprovalUsers" :key="'approval-' + u.id" class="check-option">
                  <input
                    type="checkbox"
                    :checked="assignForm.approvalUserIds.includes(u.id)"
                    @change="toggleUser('approvalUserIds', u.id)"
                  />
                  <span>{{ userName(u) }}</span>
                  <small>{{ u.username }}</small>
                </label>
              </div>
            </div>
          </section>

          <section class="assign-section">
            <div class="assign-section-head">
              <h3>签订人员</h3>
              <span class="muted">单选</span>
            </div>
            <div class="tag-picker">
              <div class="selected-tags">
                <span v-if="!selectedSignUser()" class="tag-placeholder">请选择签订人员</span>
                <button v-else type="button" class="selected-tag" @click="assignForm.signUserId = ''">
                  {{ userName(selectedSignUser()) }} <span aria-hidden="true">x</span>
                </button>
              </div>
              <div class="checkbox-list">
                <label v-for="u in assignableSignUsers" :key="'sign-' + u.id" class="check-option">
                  <input
                    type="radio"
                    name="signUser"
                    :checked="assignForm.signUserId === u.id"
                    @change="assignForm.signUserId = u.id"
                  />
                  <span>{{ userName(u) }}</span>
                  <small>{{ u.username }}</small>
                </label>
              </div>
            </div>
          </section>
        </div>
        <button class="primary" @click="assign">确认分配</button>
      </div>
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

