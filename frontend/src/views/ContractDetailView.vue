<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, UserCheck, Paperclip, Download, Trash2, Upload, RotateCcw, XCircle, Eye } from 'lucide-vue-next'
import { api } from '../api'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const contract = ref(null)
const tasks = ref([])
const attachments = ref([])
const timeline = ref([])
const versions = ref([])
const users = ref([])
const error = ref('')
const success = ref('')
const activeTab = ref('info')
const uploading = ref(false)
const hasPermission = (permission) => auth.permissions.includes(permission)

const assignForm = reactive({
  countersignUserIds: [],
  approvalUserIds: [],
  signUserId: ''
})

function statusLabel(s) {
  const map = { DRAFT:'待分配', ASSIGNED:'待会签', COUNTERSIGNED:'待定稿', FINALIZED:'待审批', APPROVED:'待签订', SIGNED:'已签订', REJECTED:'已拒绝', CANCELLED:'已取消' }
  return map[s] || s
}

function taskLabel(t) {
  const map = { ASSIGN:'分配', COUNTERSIGN:'会签', APPROVAL:'审批', FINALIZE:'定稿', SIGN:'签订' }
  return map[t] || t
}

function taskStatusLabel(s) {
  const map = { PENDING:'待处理', DONE:'已完成', REJECTED:'已拒绝' }
  return map[s] || s
}

function fileSizeLabel(bytes) {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

async function loadDetail() {
  error.value = ''
  try {
    const res = await api.get(`/contracts/${route.params.id}`)
    contract.value = res.data.contract
    tasks.value = res.data.tasks || []
  } catch (err) {
    error.value = err.message
  }
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
    const res = await api.get('/users/assignable')
    users.value = res.data
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
    await loadDetail()
    activeTab.value = 'info'
  } catch (err) {
    error.value = err.message
  }
}

async function doCountersign() {
  const opinion = prompt('会签意见:')
  if (!opinion) return
  try {
    await api.post(`/contracts/${route.params.id}/countersign`, { opinion })
    success.value = '会签成功'
    await loadDetail()
  } catch (err) {
    error.value = err.message
  }
}

async function doFinalize() {
  const content = prompt('定稿内容:', contract.value?.content || '')
  if (!content) return
  try {
    await api.post(`/contracts/${route.params.id}/finalize`, { content })
    success.value = '定稿成功'
    await loadDetail()
  } catch (err) {
    error.value = err.message
  }
}

async function doApprove(result) {
  const opinion = prompt(result === 'APPROVED' ? '审批通过意见:' : '拒绝原因:')
  if (!opinion) return
  try {
    await api.post(`/contracts/${route.params.id}/approve`, { result, opinion })
    success.value = result === 'APPROVED' ? '审批通过' : '已拒绝'
    await loadDetail()
  } catch (err) {
    error.value = err.message
  }
}

async function doSign() {
  const signInfo = prompt('签订信息:')
  if (!signInfo) return
  const signedDate = new Date().toISOString().slice(0, 10)
  try {
    await api.post(`/contracts/${route.params.id}/sign`, { signInfo, signedDate })
    success.value = '签订成功'
    await loadDetail()
  } catch (err) {
    error.value = err.message
  }
}

async function doResubmit() {
  if (!confirm('确认重新提交审批？所有审批人都需要重新审批，旧审批意见将被清空。')) return
  try {
    await api.post(`/contracts/${route.params.id}/resubmit`)
    success.value = '已重新提交审批'
    await loadDetail()
  } catch (err) {
    error.value = err.message
  }
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

async function doCancel() {
  if (!confirm('确认取消该合同？取消后会关闭所有待办任务。')) return
  try {
    await api.post(`/contracts/${route.params.id}/cancel`)
    success.value = '合同已取消'
    await loadDetail()
  } catch (err) {
    error.value = err.message
  }
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
  if (!confirm(`删除附件「${a.originalName}」？`)) return
  try {
    await api.delete(`/attachments/${a.id}`)
    await loadAttachments()
  } catch (err) {
    error.value = err.message
  }
}

const assignableUsers = computed(() => users.value.filter(u => u.id !== contract.value?.drafterId))
const pendingTask = (type) => tasks.value.some(t => t.taskType === type && t.taskStatus === 'PENDING' && Number(t.assigneeId) === Number(auth.user?.id))
const canAssignCurrent = computed(() => hasPermission('contract:assign') && contract.value?.status === 'DRAFT' && pendingTask('ASSIGN'))
const canCountersignCurrent = computed(() => hasPermission('contract:countersign') && contract.value?.status === 'ASSIGNED' && pendingTask('COUNTERSIGN'))
const canFinalizeCurrent = computed(() => hasPermission('contract:update') && contract.value?.status === 'COUNTERSIGNED' && pendingTask('FINALIZE'))
const canApproveCurrent = computed(() => hasPermission('contract:approve') && contract.value?.status === 'FINALIZED' && pendingTask('APPROVAL'))
const canSignCurrent = computed(() => hasPermission('contract:sign') && contract.value?.status === 'APPROVED' && pendingTask('SIGN'))
const canResubmitCurrent = computed(() => hasPermission('contract:update') && contract.value?.status === 'REJECTED' && Number(contract.value?.drafterId) === Number(auth.user?.id))
const canCancelCurrent = computed(() => hasPermission('contract:delete') && !['SIGNED', 'CANCELLED'].includes(contract.value?.status))
const canModifyAttachments = computed(() => hasPermission('contract:update') && contract.value?.status !== 'CANCELLED')

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
  return assignableUsers.value.filter(u => assignForm[field].includes(u.id))
}

function selectedSignUser() {
  return assignableUsers.value.find(u => u.id === assignForm.signUserId)
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
  if (route.query.tab) activeTab.value = route.query.tab
  loadDetail()
  loadUsers()
  loadAttachments()
  loadTimeline()
  loadVersions()
})
</script>

<template>
  <div class="narrow">
    <button class="secondary" style="margin-bottom:16px" @click="router.push('/contracts')">
      <ArrowLeft :size="16" /> 返回合同列表
    </button>

    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="success" class="success-msg">{{ success }}</p>

    <div v-if="contract" class="panel">
      <div class="section-title">
        <h2>{{ contract.name }}</h2>
        <span class="status" :class="'status-' + contract.status?.toLowerCase()">{{ statusLabel(contract.status) }}</span>
      </div>

      <div class="tabs">
        <button :class="{ selected: activeTab === 'info' }" @click="activeTab = 'info'">基础信息</button>
        <button :class="{ selected: activeTab === 'tasks' }" @click="activeTab = 'tasks'">流程任务</button>
        <button :class="{ selected: activeTab === 'timeline' }" @click="activeTab = 'timeline'">流程时间线</button>
        <button :class="{ selected: activeTab === 'versions' }" @click="activeTab = 'versions'">版本历史</button>
        <button :class="{ selected: activeTab === 'attachments' }" @click="activeTab = 'attachments'">
          附件 ({{ attachments.length }})
        </button>
        <button v-if="canAssignCurrent" :class="{ selected: activeTab === 'assign' }" @click="activeTab = 'assign'">分配人员</button>
      </div>

      <div v-if="activeTab === 'info'" class="tab-content">
        <dl class="detail-grid">
          <div><dt>合同编号</dt><dd>{{ contract.contractNo }}</dd></div>
          <div><dt>合同名称</dt><dd>{{ contract.name }}</dd></div>
          <div><dt>客户</dt><dd>{{ contract.customerName }}</dd></div>
          <div><dt>状态</dt><dd><span class="status" :class="'status-' + contract.status?.toLowerCase()">{{ statusLabel(contract.status) }}</span></dd></div>
          <div><dt>起草人</dt><dd>{{ contract.drafterName }}</dd></div>
          <div><dt>开始日期</dt><dd>{{ contract.beginDate }}</dd></div>
          <div><dt>结束日期</dt><dd>{{ contract.endDate }}</dd></div>
          <div v-if="contract.signedDate"><dt>签订日期</dt><dd>{{ contract.signedDate }}</dd></div>
          <div v-if="contract.signInfo"><dt>签订信息</dt><dd>{{ contract.signInfo }}</dd></div>
        </dl>
        <div style="margin-top:16px">
          <h4>合同内容</h4>
          <pre class="content-box">{{ contract.content }}</pre>
        </div>

        <div class="row-actions" style="margin-top:16px">
          <button v-if="canAssignCurrent" @click="activeTab = 'assign'">
            <UserCheck :size="14" /> 分配人员
          </button>
          <button v-if="canFinalizeCurrent" @click="doFinalize">定稿</button>
          <button v-if="canResubmitCurrent" @click="doResubmit">
            <RotateCcw :size="14" /> 重新提交审批
          </button>
          <button v-if="canApproveCurrent" @click="doApprove('APPROVED')">审批通过</button>
          <button v-if="canApproveCurrent" @click="doApprove('REJECTED')">审批拒绝</button>
          <button v-if="canCountersignCurrent" @click="doCountersign">会签</button>
          <button v-if="canSignCurrent" @click="doSign">签订</button>
          <button v-if="canCancelCurrent" @click="doCancel" style="color:#b42318">
            <XCircle :size="14" /> 取消合同
          </button>
        </div>
      </div>

      <div v-if="activeTab === 'tasks'" class="tab-content">
        <div v-if="tasks.length === 0" class="muted" style="text-align:center;padding:24px">暂无流程任务</div>
        <table v-else>
          <thead><tr><th>类型</th><th>处理人</th><th>状态</th><th>意见</th><th>时间</th></tr></thead>
          <tbody>
            <tr v-for="t in tasks" :key="t.id">
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
              <strong>{{ statusLabel(item.fromStatus) }} -> {{ statusLabel(item.toStatus) }}</strong>
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
                <label v-for="u in assignableUsers" :key="'counter-' + u.id" class="check-option">
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
                <label v-for="u in assignableUsers" :key="'approval-' + u.id" class="check-option">
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
                <label v-for="u in assignableUsers" :key="'sign-' + u.id" class="check-option">
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
  </div>
</template>
