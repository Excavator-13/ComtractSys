<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Plus, Pencil, Trash2, ShieldCheck } from 'lucide-vue-next'
import { api } from '../../api'

const roles = ref([])
const permissions = ref([])
const error = ref('')
const showForm = ref(false)
const editingId = ref(null)
const showPermForm = ref(false)
const permRoleId = ref(null)
const selectedPermIds = ref([])

const form = reactive({ roleCode: '', roleName: '', description: '' })

async function loadRoles() {
  error.value = ''
  try {
    const res = await api.get('/roles')
    roles.value = res.data
  } catch (err) {
    error.value = err.message
  }
}

async function loadPermissions() {
  try {
    const res = await api.get('/permissions')
    permissions.value = res.data
  } catch {}
}

function openCreate() {
  editingId.value = null
  Object.keys(form).forEach(k => form[k] = '')
  showForm.value = true
}

function openEdit(r) {
  editingId.value = r.id
  form.roleCode = r.roleCode
  form.roleName = r.roleName
  form.description = r.description || ''
  showForm.value = true
}

async function save() {
  if (!form.roleCode && !editingId.value) {
    error.value = '角色编码不能为空'
    return
  }
  if (!form.roleName) {
    error.value = '角色名称不能为空'
    return
  }
  try {
    if (editingId.value) {
      await api.put(`/roles/${editingId.value}`, { roleName: form.roleName, description: form.description })
    } else {
      await api.post('/roles', form)
    }
    showForm.value = false
    loadRoles()
  } catch (err) {
    error.value = err.message
  }
}

async function remove(id) {
  if (!confirm('确认删除该角色？')) return
  try {
    await api.delete(`/roles/${id}`)
    loadRoles()
  } catch (err) {
    error.value = err.message
  }
}

function openPermAssign(r) {
  permRoleId.value = r.id
  selectedPermIds.value = r.permissions?.map(p => p.id) || []
  showPermForm.value = true
}

function togglePerm(permId) {
  const idx = selectedPermIds.value.indexOf(permId)
  if (idx >= 0) selectedPermIds.value.splice(idx, 1)
  else selectedPermIds.value.push(permId)
}

async function savePerms() {
  try {
    await api.put(`/roles/${permRoleId.value}/permissions`, { permissionIds: selectedPermIds.value })
    showPermForm.value = false
    loadRoles()
  } catch (err) {
    error.value = err.message
  }
}

onMounted(() => { loadRoles(); loadPermissions() })
</script>

<template>
  <div>
    <div class="section-title">
      <h2>角色管理</h2>
      <button class="primary" @click="openCreate"><Plus :size="16" /> 新增角色</button>
    </div>

    <p v-if="error" class="error">{{ error }}</p>

    <div v-if="showForm" class="panel narrow" style="margin-bottom:18px">
      <h2>{{ editingId ? '编辑角色' : '新增角色' }}</h2>
      <div class="form-grid single" style="margin-top:14px">
        <label>角色编码 *<input v-model="form.roleCode" :disabled="!!editingId" /></label>
        <label>角色名称 *<input v-model="form.roleName" /></label>
        <label>描述<input v-model="form.description" /></label>
      </div>
      <div class="row-actions">
        <button class="primary" @click="save">保存</button>
        <button class="secondary" @click="showForm = false">取消</button>
      </div>
    </div>

    <div v-if="showPermForm" class="panel narrow" style="margin-bottom:18px">
      <h2>分配权限</h2>
      <div style="margin-top:14px">
        <div v-for="p in permissions" :key="p.id" style="margin-bottom:8px">
          <label style="display:flex;align-items:center;gap:8px;cursor:pointer;font-weight:400">
            <input type="checkbox" :checked="selectedPermIds.includes(p.id)" @change="togglePerm(p.id)" />
            <strong>{{ p.permissionName }}</strong>
            <span class="muted">{{ p.permissionCode }} · {{ p.module }}</span>
          </label>
        </div>
      </div>
      <div class="row-actions" style="margin-top:14px">
        <button class="primary" @click="savePerms">保存权限</button>
        <button class="secondary" @click="showPermForm = false">取消</button>
      </div>
    </div>

    <div class="panel">
      <table>
        <thead><tr><th>角色编码</th><th>名称</th><th>描述</th><th>权限</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="r in roles" :key="r.id">
            <td>{{ r.roleCode }}</td>
            <td>{{ r.roleName }}</td>
            <td>{{ r.description }}</td>
            <td>
              <span v-for="p in r.permissions" :key="p.id" class="status" style="margin-right:4px;font-size:12px">
                {{ p.permissionName }}
              </span>
            </td>
            <td class="row-actions">
              <button @click="openEdit(r)"><Pencil :size="14" /> 编辑</button>
              <button @click="openPermAssign(r)"><ShieldCheck :size="14" /> 权限</button>
              <button @click="remove(r.id)"><Trash2 :size="14" /> 删除</button>
            </td>
          </tr>
          <tr v-if="roles.length === 0"><td colspan="5" class="muted" style="text-align:center">暂无角色</td></tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
