<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { UserRound, LockKeyhole, ArrowLeft } from 'lucide-vue-next'
import { api } from '../api'

const router = useRouter()
const form = reactive({ username: '', password: '', confirmPassword: '' })
const error = ref('')
const loading = ref(false)

async function register() {
  error.value = ''
  if (form.password !== form.confirmPassword) {
    error.value = '两次输入的密码不一致'
    return
  }
  loading.value = true
  try {
    await api.post('/auth/register', form)
    router.push('/login')
  } catch (err) {
    error.value = err.message
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="login-page">
    <section class="login-panel">
      <div>
        <p class="eyebrow">ContractSys</p>
        <h1>注册账号</h1>
        <p class="muted">注册后请联系管理员分配权限。</p>
        <button class="secondary" style="margin-top:16px" @click="router.push('/login')">
          <ArrowLeft :size="16" /> 返回登录
        </button>
      </div>

      <form class="login-form" @submit.prevent="register">
        <label>
          <span>用户名</span>
          <div class="input">
            <UserRound :size="18" />
            <input v-model="form.username" autocomplete="username" required />
          </div>
        </label>
        <label>
          <span>密码</span>
          <div class="input">
            <LockKeyhole :size="18" />
            <input v-model="form.password" type="password" autocomplete="new-password" required />
          </div>
        </label>
        <label>
          <span>确认密码</span>
          <div class="input">
            <LockKeyhole :size="18" />
            <input v-model="form.confirmPassword" type="password" required />
          </div>
        </label>
        <p v-if="error" class="error">{{ error }}</p>
        <button class="primary" :disabled="loading">{{ loading ? '注册中...' : '注册' }}</button>
      </form>
    </section>
  </main>
</template>
