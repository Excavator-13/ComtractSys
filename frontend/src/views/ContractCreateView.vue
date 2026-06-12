<script setup>
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, FolderDown, Paperclip, Upload, X } from 'lucide-vue-next'
import { api } from '../api'

const router = useRouter()
const customers = ref([])
const error = ref('')
const loading = ref(false)
const files = ref([])

const form = reactive({
  name: '',
  customerId: '',
  beginDate: new Date().toISOString().slice(0, 10),
  endDate: new Date(Date.now() + 365 * 86400000).toISOString().slice(0, 10),
  content: ''
})

async function loadCustomers() {
  try {
    const res = await api.get('/customers', { params: { page: 1, size: 100 } })
    customers.value = res.data.records
  } catch (err) {
    error.value = err.message
  }
}

async function submit() {
  if (!form.name || !form.customerId || !form.content) {
    error.value = '请填写合同名称、客户和合同概述'
    return
  }
  loading.value = true
  error.value = ''
  try {
    let res
    if (files.value.length) {
      const formData = new FormData()
      formData.append('name', form.name)
      formData.append('customerId', Number(form.customerId))
      formData.append('beginDate', form.beginDate)
      formData.append('endDate', form.endDate)
      formData.append('content', form.content)
      files.value.forEach(file => formData.append('files', file))
      res = await api.post('/contracts', formData, { headers: { 'Content-Type': 'multipart/form-data' } })
    } else {
      res = await api.post('/contracts', { ...form, customerId: Number(form.customerId) })
    }
    router.push(`/contracts/${res.data.id}`)
  } catch (err) {
    error.value = err.message
  } finally {
    loading.value = false
  }
}

function handleFiles(e) {
  files.value = Array.from(e.target.files || [])
}

function removeFile(index) {
  files.value.splice(index, 1)
}

function fileSizeLabel(bytes) {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

onMounted(() => {
  loadCustomers()
})
</script>

<template>
  <div class="narrow">
    <button class="secondary" style="margin-bottom:16px" @click="router.push('/contracts')">
      <ArrowLeft :size="16" /> 返回合同列表
    </button>
    <div class="panel">
      <h2>起草合同</h2>
      <p v-if="error" class="error">{{ error }}</p>
      <div class="form-grid" style="margin-top:18px">
        <label>合同名称<input v-model="form.name" placeholder="输入合同名称" required /></label>
        <label>客户
          <select v-model="form.customerId" required>
            <option value="" disabled>选择客户</option>
            <option v-for="c in customers" :key="c.id" :value="c.id">{{ c.name }}</option>
          </select>
        </label>
        <label>开始日期<input v-model="form.beginDate" type="date" required /></label>
        <label>结束日期<input v-model="form.endDate" type="date" required /></label>
        <div class="full attachment-box">
          <div class="attachment-head">
            <div>
              <strong>参考模板</strong>
              <p class="muted">进入模板库下载参考文件，线下拟稿后可在下方作为附件上传。</p>
            </div>
            <button class="secondary" type="button" @click="router.push('/templates')">
              <FolderDown :size="16" /> 模板库
            </button>
          </div>
        </div>
        <label class="full">合同概述<textarea v-model="form.content" rows="8" placeholder="概述合同目标、范围、关键条款和交付要求" required /></label>
        <div class="full attachment-box">
          <div class="attachment-head">
            <div>
              <strong>附件</strong>
              <p class="muted">支持 doc/docx/jpg/png/pdf，最大 10MB，可随合同一起提交。</p>
            </div>
            <label class="secondary attach-trigger">
              <Upload :size="16" /> 选择附件
              <input type="file" hidden multiple accept=".doc,.docx,.jpg,.jpeg,.png,.bmp,.gif,.pdf" @change="handleFiles" />
            </label>
          </div>
          <div v-if="files.length" class="attachment-list">
            <div v-for="(file, index) in files" :key="file.name + index" class="attachment-item">
              <Paperclip :size="16" />
              <span>{{ file.name }}</span>
              <small>{{ fileSizeLabel(file.size) }}</small>
              <button class="icon mini" type="button" @click="removeFile(index)" title="移除附件">
                <X :size="14" />
              </button>
            </div>
          </div>
          <p v-else class="empty-hint">尚未选择附件</p>
        </div>
      </div>
      <button class="primary" :disabled="loading" @click="submit">{{ loading ? '提交中...' : '提交起草' }}</button>
    </div>
  </div>
</template>
