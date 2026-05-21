<script setup>
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft } from 'lucide-vue-next'
import { api } from '../api'

const router = useRouter()
const customers = ref([])
const error = ref('')
const loading = ref(false)

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
    if (!form.customerId && customers.value[0]) {
      form.customerId = customers.value[0].id
    }
  } catch (err) {
    error.value = err.message
  }
}

async function submit() {
  if (!form.name || !form.customerId || !form.content) {
    error.value = '请填写合同名称、客户和内容'
    return
  }
  loading.value = true
  error.value = ''
  try {
    const res = await api.post('/contracts', { ...form, customerId: Number(form.customerId) })
    router.push(`/contracts/${res.data.id}`)
  } catch (err) {
    error.value = err.message
  } finally {
    loading.value = false
  }
}

onMounted(loadCustomers)
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
        <label class="full">合同内容<textarea v-model="form.content" rows="8" placeholder="输入合同正文内容" required /></label>
      </div>
      <button class="primary" :disabled="loading" @click="submit">{{ loading ? '提交中...' : '提交起草' }}</button>
    </div>
  </div>
</template>
