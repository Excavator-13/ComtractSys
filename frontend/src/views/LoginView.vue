<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { LockKeyhole, UserRound } from 'lucide-vue-next'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const form = reactive({ username: 'admin', password: '123456' })
const error = ref('')
const loading = ref(false)

async function login() {
  error.value = ''
  loading.value = true
  try {
    await auth.login(form)
    router.push('/dashboard')
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
        <h1>合同管理系统</h1>
        <p class="muted">登录后处理合同起草、会签、审批和签订流程。</p>
      </div>

      <form class="login-form" @submit.prevent="login">
        <label>
          <span>用户名</span>
          <div class="input">
            <UserRound :size="18" />
            <input v-model="form.username" autocomplete="username" />
          </div>
        </label>
        <label>
          <span>密码</span>
          <div class="input">
            <LockKeyhole :size="18" />
            <input v-model="form.password" type="password" autocomplete="current-password" />
          </div>
        </label>
        <p v-if="error" class="error">{{ error }}</p>
        <button class="primary" :disabled="loading">{{ loading ? '登录中...' : '登录' }}</button>
        <p class="muted" style="text-align:center">还没有账号？<router-link to="/register">立即注册</router-link></p>
      </form>
    </section>
  </main>
</template>
