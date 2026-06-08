<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { Plus, Pencil, Trash2, Search, RefreshCcw } from 'lucide-vue-next'
import { api } from '../../api'

const corePermissions = new Set([
  'contract:create',
  'contract:update',
  'contract:delete',
  'contract:view',
  'contract:query',
  'contract:assign',
  'contract:countersign',
  'contract:approve',
  'contract:sign',
  'customer:manage',
  'user:manage',
  'role:manage',
  'permission:manage',
  'log:view'
])

const permissions = ref([])
const keyword = ref('')
const error = ref('')
const showForm = ref(false)
const editingId = ref(null)
const form = reactive({ permissionCode: '', permissionName: '', module: '', url: '', description: '' })

async function loadPermissions() {
  error.value = ''
  try {
    const res = await api.get('/permissions')
    permissions.value = res.data || []
  } catch (err) {
    error.value = err.message
  }
}

const filteredPermissions = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return permissions.value
  return permissions.value.filter(p =>
    p.permissionCode?.toLowerCase().includes(kw) ||
    p.permissionName?.toLowerCase().includes(kw) ||
    p.module?.toLowerCase().includes(kw)
  )
})

function isCorePermission(permission) {
  return corePermissions.has(permission.permissionCode)
}

function openCreate() {
  editingId.value = null
  Object.keys(form).forEach(k => form[k] = '')
  showForm.value = true
}

function openEdit(permission) {
  editingId.value = permission.id
  form.permissionCode = permission.permissionCode
  form.permissionName = permission.permissionName
  form.module = permission.module
  form.url = permission.url || ''
  form.description = permission.description || ''
  showForm.value = true
}

async function save() {
  error.value = ''
  if (!form.permissionCode || !form.permissionName || !form.module) {
    error.value = '权限编码、权限名称和模块不能为空'
    return
  }
  try {
    if (editingId.value) {
      await api.put(`/permissions/${editingId.value}`, form)
    } else {
      await api.post('/permissions', form)
    }
    showForm.value = false
    await loadPermissions()
  } catch (err) {
    error.value = err.message
  }
}

async function remove(permission) {
  if (isCorePermission(permission)) return
  if (!confirm(`确认删除权限「${permission.permissionName}」？`)) return
  try {
    await api.delete(`/permissions/${permission.id}`)
    await loadPermissions()
  } catch (err) {
    error.value = err.message
  }
}

onMounted(loadPermissions)
</script>

<template>
  <div>
    <div class="section-title">
      <h2>权限管理</h2>
      <div class="actions">
        <div class="input" style="max-width:240px">
          <Search :size="16" />
          <input v-model="keyword" placeholder="搜索权限编码/名称/模块" />
        </div>
        <button class="secondary" @click="loadPermissions"><RefreshCcw :size="16" /></button>
        <button class="primary" @click="openCreate"><Plus :size="16" /> 新增权限</button>
      </div>
    </div>

    <p v-if="error" class="error">{{ error }}</p>

    <div v-if="showForm" class="panel narrow" style="margin-bottom:18px">
      <h2>{{ editingId ? '编辑权限' : '新增权限' }}</h2>
      <div class="form-grid single" style="margin-top:14px">
        <label>权限编码 *<input v-model="form.permissionCode" :disabled="!!editingId" placeholder="如 report:view" /></label>
        <label>权限名称 *<input v-model="form.permissionName" placeholder="如 查看报表" /></label>
        <label>模块 *<input v-model="form.module" placeholder="如 REPORT" /></label>
        <label>URL<input v-model="form.url" placeholder="可选" /></label>
        <label>描述<input v-model="form.description" placeholder="可选" /></label>
      </div>
      <div class="row-actions">
        <button class="primary" @click="save">保存</button>
        <button class="secondary" @click="showForm = false">取消</button>
      </div>
    </div>

    <div class="panel">
      <table>
        <thead><tr><th>权限编码</th><th>名称</th><th>模块</th><th>描述</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="p in filteredPermissions" :key="p.id">
            <td><code>{{ p.permissionCode }}</code></td>
            <td>{{ p.permissionName }}</td>
            <td><span class="status">{{ p.module }}</span></td>
            <td class="muted">{{ p.description || '-' }}</td>
            <td class="row-actions">
              <button @click="openEdit(p)"><Pencil :size="14" /> 编辑</button>
              <button :disabled="isCorePermission(p)" @click="remove(p)">
                <Trash2 :size="14" /> {{ isCorePermission(p) ? '核心权限' : '删除' }}
              </button>
            </td>
          </tr>
          <tr v-if="filteredPermissions.length === 0">
            <td colspan="5" class="muted" style="text-align:center">暂无权限</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
