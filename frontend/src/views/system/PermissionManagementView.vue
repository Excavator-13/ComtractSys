<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { Pencil, Search, RefreshCcw } from 'lucide-vue-next'
import { api } from '../../api'

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
  if (!editingId.value) return
  if (!form.permissionName || !form.module) {
    error.value = '权限名称和模块不能为空'
    return
  }
  try {
    await api.put(`/permissions/${editingId.value}`, form)
    showForm.value = false
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
      <div>
        <h2>权限管理</h2>
        <p class="muted">权限目录为系统固定能力，仅可编辑名称、模块和描述；角色拥有的权限在角色管理中单独勾选调整。</p>
      </div>
      <div class="actions">
        <div class="input" style="max-width:240px">
          <Search :size="16" />
          <input v-model="keyword" placeholder="搜索权限名称/模块" />
        </div>
        <button class="secondary" @click="loadPermissions"><RefreshCcw :size="16" /></button>
      </div>
    </div>

    <p v-if="error" class="error">{{ error }}</p>

    <div v-if="showForm" class="panel narrow" style="margin-bottom:18px">
      <h2>编辑权限</h2>
      <div class="form-grid single" style="margin-top:14px">
        <label>权限编码<input v-model="form.permissionCode" disabled /></label>
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
        <thead><tr><th>名称</th><th>模块</th><th>类型</th><th>描述</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="p in filteredPermissions" :key="p.id">
            <td>{{ p.permissionName }}</td>
            <td><span class="status">{{ p.module }}</span></td>
            <td><span class="status">{{ p.core ? '系统权限' : '扩展权限' }}</span></td>
            <td class="muted">{{ p.description || '-' }}</td>
            <td class="row-actions">
              <button @click="openEdit(p)"><Pencil :size="14" /> 编辑</button>
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
