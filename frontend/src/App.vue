<script setup>
import { onBeforeUnmount, onMounted } from 'vue'
import { useAuthStore } from './stores/auth'

const auth = useAuthStore()

async function refreshAuth() {
  try {
    await auth.refreshMe()
  } catch {}
}

onMounted(() => {
  refreshAuth()
  window.addEventListener('auth-permissions-stale', refreshAuth)
})

onBeforeUnmount(() => window.removeEventListener('auth-permissions-stale', refreshAuth))
</script>

<template>
  <RouterView />
</template>
