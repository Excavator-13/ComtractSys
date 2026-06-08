<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { Search, Plus, Pencil, Trash2, RefreshCcw, ShieldCheck } from 'lucide-vue-next'
import { api } from '../../api'

const users = ref([])
const roles = ref([])
const keyword = ref('')
const error = ref('')
const showForm = ref(false)
const editingId = ref(null)
const showRoleForm = ref(false)
const roleUserId = ref(null)
const selectedRoleId = ref('')
const page = ref(1)
const total = ref(0)
const pageSize = 10

const form = reactive({
  username: '',
  password: '',
  displayName: '',
  phone: '',
  email: ''
})

async function loadUsers() {
  error.value = ''
  try {
    const params = { page: page.value, size: pageSize }
    if (keyword.value) params.keyword = keyword.value
    const res = await api.get('/users', { params })
    users.value = res.data.records
    total.value = res.data.total
  } catch (err) {
    error.value = err.message
  }
}

async function loadRoles() {
  try {
    const res = await api.get('/roles')
    roles.value = res.data
  } catch {}
}

function openCreate() {
  editingId.value = null
  Object.keys(form).forEach(k => form[k] = '')
  showForm.value = true
}

function openEdit(u) {
  editingId.value = u.id
  form.username = u.username
  form.password = ''
  form.displayName = u.displayName || ''
  form.phone = u.phone || ''
  form.email = u.email || ''
  showForm.value = true
}

async function save() {
  if (!form.username) {
    error.value = '用户名不能为空'
    return
  }
  try {
    if (editingId.value) {
      if (form.password && form.password.length < 6) { error.value = '密码长度不能少于6位'; return }
      await api.put(`/users/${editingId.value}`, form)
    } else {
      if (!form.password) { error.value = '密码不能为空'; return }
      if (form.password.length < 6) { error.value = '密码长度不能少于6位'; return }
      await api.post('/users', form)
    }
    showForm.value = false
    loadUsers()
  } catch (err) {
    error.value = err.message
  }
}

async function remove(id) {
  if (!confirm('确认删除该用户？')) return
  try {
    await api.delete(`/users/${id}`)
    loadUsers()
  } catch (err) {
    error.value = err.message
  }
}

async function toggleStatus(u) {
  const newStatus = u.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
  try {
    await api.patch(`/users/${u.id}/status`, { status: newStatus })
    loadUsers()
  } catch (err) {
    error.value = err.message
  }
}

function openRoleAssign(u) {
  roleUserId.value = u.id
  selectedRoleId.value = u.roles?.map(r => {
    const found = roles.value.find(rr => rr.roleCode === r)
    return found ? found.id : null
  }).filter(Boolean)?.[0] || ''
  showRoleForm.value = true
}

async function saveRoles() {
  try {
    await api.put(`/users/${roleUserId.value}/roles`, { roleId: selectedRoleId.value || null })
    showRoleForm.value = false
    loadUsers()
  } catch (err) {
    error.value = err.message
  }
}

function search() {
  page.value = 1
  loadUsers()
}

function goPage(p) {
  page.value = p
  loadUsers()
}

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))

onMounted(() => { loadUsers(); loadRoles() })
</script>

<template>
  <div>
    <div class="section-title">
      <h2>用户管理</h2>
      <div class="actions">
        <div class="input" style="max-width:200px">
          <Search :size="16" />
          <input v-model="keyword" placeholder="搜索用户名" @keyup.enter="search" />
        </div>
        <button class="secondary" @click="loadUsers"><RefreshCcw :size="16" /></button>
        <button class="primary" @click="openCreate"><Plus :size="16" /> 新增用户</button>
      </div>
    </div>

    <p v-if="error" class="error">{{ error }}</p>

    <div v-if="showForm" class="panel narrow" style="margin-bottom:18px">
      <h2>{{ editingId ? '编辑用户' : '新增用户' }}</h2>
      <div class="form-grid" style="margin-top:14px">
        <label>用户名 *<input v-model="form.username" :disabled="!!editingId" /></label>
        <label>密码 {{ editingId ? '(留空不修改)' : '*' }}<input v-model="form.password" type="password" minlength="6" /></label>
        <label>显示名称<input v-model="form.displayName" /></label>
        <label>电话<input v-model="form.phone" /></label>
        <label>邮箱<input v-model="form.email" /></label>
      </div>
      <div class="row-actions">
        <button class="primary" @click="save">保存</button>
        <button class="secondary" @click="showForm = false">取消</button>
      </div>
    </div>

    <div v-if="showRoleForm" class="panel narrow" style="margin-bottom:18px">
      <h2>分配角色</h2>
      <div style="margin-top:14px;display:grid;gap:8px">
        <label v-for="r in roles" :key="r.id" style="display:flex;align-items:center;gap:8px;cursor:pointer;font-weight:400">
          <input v-model="selectedRoleId" type="radio" name="role" :value="r.id" style="width:auto;min-height:auto" />
          <strong>{{ r.roleName }}</strong>
          <span class="muted">{{ r.roleCode }} - {{ r.description }}</span>
        </label>
      </div>
      <div class="row-actions" style="margin-top:14px">
        <button class="primary" @click="saveRoles">保存角色</button>
        <button class="secondary" @click="showRoleForm = false">取消</button>
      </div>
    </div>

    <div class="panel">
      <table>
        <thead><tr><th>用户名</th><th>显示名称</th><th>角色</th><th>状态</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="u in users" :key="u.id">
            <td>{{ u.username }}</td>
            <td>{{ u.displayName }}</td>
            <td><span v-for="r in u.roles" :key="r" class="status" style="margin-right:4px">{{ r }}</span></td>
            <td>
              <span :class="u.status === 'ENABLED' ? 'status status-APPROVED' : 'status status-REJECTED'">
                {{ u.status === 'ENABLED' ? '启用' : '禁用' }}
              </span>
            </td>
            <td class="row-actions">
              <button @click="openEdit(u)"><Pencil :size="14" /> 编辑</button>
              <button @click="openRoleAssign(u)"><ShieldCheck :size="14" /> 角色</button>
              <button @click="toggleStatus(u)">{{ u.status === 'ENABLED' ? '禁用' : '启用' }}</button>
              <button @click="remove(u.id)"><Trash2 :size="14" /> 删除</button>
            </td>
          </tr>
          <tr v-if="users.length === 0"><td colspan="5" class="muted" style="text-align:center">暂无用户</td></tr>
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
