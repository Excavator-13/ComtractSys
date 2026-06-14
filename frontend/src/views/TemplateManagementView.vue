<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { Download, RefreshCcw, Upload, ToggleLeft, ToggleRight, Trash2 } from 'lucide-vue-next'
import { api } from '../api'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const templates = ref([])
const roleOptions = ref([
  { roleCode: 'ROLE_OPERATOR', roleName: '操作员' }
])
const error = ref('')
const loading = ref(false)
const fileInput = ref(null)
const form = reactive({ name: '', description: '', content: '', visibleRoles: [], file: null })
const canManage = computed(() => auth.permissions.includes('contract:assign'))

function fileSizeLabel(bytes) {
  if (!bytes) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

async function loadTemplates() {
  loading.value = true
  error.value = ''
  try {
    const url = canManage.value ? '/contract-templates/manage' : '/contract-templates'
    const res = await api.get(url)
    templates.value = res.data || []
  } catch (err) {
    error.value = err.message
  } finally {
    loading.value = false
  }
}

async function loadRoles() {
  try {
    const res = await api.get('/roles/options')
    roleOptions.value = (res.data || []).map(role => ({ roleCode: role.roleCode, roleName: role.roleName }))
  } catch {}
}

function onFileChange(e) {
  form.file = e.target.files?.[0] || null
  if (form.file && !form.name) {
    form.name = form.file.name.replace(/\.[^.]+$/, '')
  }
}

async function uploadTemplate() {
  if (!form.name.trim()) {
    error.value = '模板名称不能为空'
    return
  }
  error.value = ''
  const data = new FormData()
  data.append('name', form.name)
  data.append('description', form.description)
  data.append('content', form.content)
  data.append('visibleRoles', form.visibleRoles.join(','))
  if (form.file) data.append('file', form.file)
  try {
    await api.post('/contract-templates', data, { headers: { 'Content-Type': 'multipart/form-data' } })
    form.name = ''
    form.description = ''
    form.content = ''
    form.visibleRoles = []
    form.file = null
    if (fileInput.value) fileInput.value.value = ''
    await loadTemplates()
  } catch (err) {
    error.value = err.message
  }
}

async function toggleEnabled(template) {
  try {
    await api.patch(`/contract-templates/${template.id}/enabled`, null, { params: { enabled: !template.enabled } })
    await loadTemplates()
  } catch (err) {
    error.value = err.message
  }
}

async function deleteTemplate(template) {
  if (!confirm(`确认删除模板「${template.name}」？`)) return
  try {
    await api.delete(`/contract-templates/${template.id}`)
    await loadTemplates()
  } catch (err) {
    error.value = err.message
  }
}

async function downloadTemplate(template) {
  try {
    const res = await api.get(`/contract-templates/${template.id}/download`, { responseType: 'blob' })
    const url = URL.createObjectURL(res.data)
    const link = document.createElement('a')
    link.href = url
    link.download = template.originalName || `${template.name}.txt`
    link.click()
    URL.revokeObjectURL(url)
  } catch (err) {
    error.value = err.message
  }
}

onMounted(loadTemplates)
onMounted(loadRoles)
</script>

<template>
  <div>
    <div class="section-title">
      <div>
        <h2>模板库</h2>
        <p class="muted">下载参考模板后线下拟稿，再作为合同附件上传。</p>
      </div>
      <button class="secondary" @click="loadTemplates"><RefreshCcw :size="16" /> 刷新</button>
    </div>

    <p v-if="error" class="error">{{ error }}</p>

    <div v-if="canManage" class="panel" style="margin-bottom:18px">
      <h2>上传模板</h2>
      <div class="form-grid" style="margin-top:14px">
        <label>模板名称 *<input v-model="form.name" /></label>
        <label>说明<input v-model="form.description" /></label>
        <div class="full">
          <span class="field-label">可见角色</span>
          <div class="choice-grid">
            <label v-for="role in roleOptions" :key="role.roleCode" class="check-row">
              <input v-model="form.visibleRoles" type="checkbox" :value="role.roleCode" />
              <span>{{ role.roleName || role.roleCode }}</span>
              <small>{{ role.roleCode }}</small>
            </label>
          </div>
          <p class="muted" style="margin:6px 0 0">不选择时全部角色可见。</p>
        </div>
        <label class="full">摘要<textarea v-model="form.content" rows="3" placeholder="模板适用范围、关键条款或使用说明" /></label>
        <label class="full">模板文件<input ref="fileInput" type="file" accept=".doc,.docx,.jpg,.jpeg,.png,.bmp,.gif,.pdf" @change="onFileChange" /></label>
      </div>
      <button class="primary" @click="uploadTemplate"><Upload :size="16" /> 上传模板</button>
    </div>

    <div class="panel">
      <table>
        <thead>
          <tr><th>模板名称</th><th>说明</th><th>文件</th><th>可见角色</th><th>状态</th><th>操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="t in templates" :key="t.id">
            <td>{{ t.name }}</td>
            <td class="muted">{{ t.description || t.content || '-' }}</td>
            <td>{{ t.originalName || '文本模板' }} <span class="muted">{{ fileSizeLabel(t.fileSize) }}</span></td>
            <td class="muted">{{ t.visibleRoles || '全部' }}</td>
            <td><span class="status" :class="t.enabled ? 'status-signed' : 'status-cancelled'">{{ t.enabled ? '启用' : '停用' }}</span></td>
            <td class="row-actions">
              <button @click="downloadTemplate(t)"><Download :size="14" /> 下载</button>
              <button v-if="canManage" @click="toggleEnabled(t)">
                <component :is="t.enabled ? ToggleLeft : ToggleRight" :size="14" /> {{ t.enabled ? '停用' : '启用' }}
              </button>
              <button v-if="canManage" @click="deleteTemplate(t)"><Trash2 :size="14" /> 删除</button>
            </td>
          </tr>
          <tr v-if="!loading && templates.length === 0"><td colspan="6" class="muted" style="text-align:center">暂无模板</td></tr>
          <tr v-if="loading"><td colspan="6" class="muted" style="text-align:center">加载中...</td></tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
