<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Search, Plus, Pencil, Trash2, RefreshCcw } from 'lucide-vue-next'
import { api } from '../api'

const customers = ref([])
const keyword = ref('')
const error = ref('')
const showForm = ref(false)
const editingId = ref(null)
const form = reactive({ name: '', tel: '', address: '', fax: '', postalCode: '', bankName: '', bankAccount: '', remark: '' })

async function loadCustomers() {
  error.value = ''
  try {
    const params = { page: 1, size: 100 }
    if (keyword.value) params.keyword = keyword.value
    const res = await api.get('/customers', { params })
    customers.value = res.data.records
  } catch (err) {
    error.value = err.message
  }
}

function openCreate() {
  editingId.value = null
  Object.keys(form).forEach(k => form[k] = '')
  showForm.value = true
}

function openEdit(c) {
  editingId.value = c.id
  Object.keys(form).forEach(k => form[k] = c[k] || '')
  showForm.value = true
}

async function save() {
  if (!form.name || !form.tel || !form.address) {
    error.value = '名称、电话、地址为必填'
    return
  }
  try {
    if (editingId.value) {
      await api.put(`/customers/${editingId.value}`, form)
    } else {
      await api.post('/customers', form)
    }
    showForm.value = false
    loadCustomers()
  } catch (err) {
    error.value = err.message
  }
}

async function remove(id) {
  if (!confirm('确认删除该客户？')) return
  try {
    await api.delete(`/customers/${id}`)
    loadCustomers()
  } catch (err) {
    error.value = err.message
  }
}

onMounted(loadCustomers)
</script>

<template>
  <div>
    <div class="section-title">
      <h2>客户管理</h2>
      <div class="actions">
        <div class="input" style="max-width:220px">
          <Search :size="16" />
          <input v-model="keyword" placeholder="搜索客户" @keyup.enter="loadCustomers" />
        </div>
        <button class="secondary" @click="loadCustomers"><RefreshCcw :size="16" /></button>
        <button class="primary" @click="openCreate"><Plus :size="16" /> 新增客户</button>
      </div>
    </div>

    <p v-if="error" class="error">{{ error }}</p>

    <div v-if="showForm" class="panel narrow" style="margin-bottom:18px">
      <h2>{{ editingId ? '编辑客户' : '新增客户' }}</h2>
      <div class="form-grid" style="margin-top:14px">
        <label>客户名称 *<input v-model="form.name" /></label>
        <label>电话 *<input v-model="form.tel" /></label>
        <label>地址 *<input v-model="form.address" /></label>
        <label>传真<input v-model="form.fax" /></label>
        <label>邮编<input v-model="form.postalCode" /></label>
        <label>开户行<input v-model="form.bankName" /></label>
        <label>银行账号<input v-model="form.bankAccount" /></label>
        <label>备注<input v-model="form.remark" /></label>
      </div>
      <div class="row-actions">
        <button class="primary" @click="save">保存</button>
        <button class="secondary" @click="showForm = false">取消</button>
      </div>
    </div>

    <div class="panel">
      <table>
        <thead><tr><th>名称</th><th>电话</th><th>地址</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="c in customers" :key="c.id">
            <td>{{ c.name }}</td>
            <td>{{ c.tel }}</td>
            <td>{{ c.address }}</td>
            <td class="row-actions">
              <button @click="openEdit(c)"><Pencil :size="14" /> 编辑</button>
              <button @click="remove(c.id)"><Trash2 :size="14" /> 删除</button>
            </td>
          </tr>
          <tr v-if="customers.length === 0"><td colspan="4" class="muted" style="text-align:center">暂无客户数据</td></tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
