import { defineStore } from 'pinia'
import { api } from '../api'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    user: JSON.parse(localStorage.getItem('user') || 'null'),
    lastRefreshedAt: 0
  }),
  getters: {
    isLoggedIn: (state) => Boolean(state.token),
    permissions: (state) => state.user?.permissions || []
  },
  actions: {
    async login(payload) {
      const res = await api.post('/auth/login', payload)
      this.token = res.data.token
      this.user = res.data.user
      localStorage.setItem('token', this.token)
      localStorage.setItem('user', JSON.stringify(this.user))
    },
    async refreshMe(force = false) {
      if (!this.token) return
      if (!force && Date.now() - this.lastRefreshedAt < 30000) return
      const res = await api.get('/auth/me')
      this.user = res.data
      this.lastRefreshedAt = Date.now()
      localStorage.setItem('user', JSON.stringify(this.user))
    },
    async updateProfile(payload) {
      const res = await api.put('/auth/profile', payload)
      this.user = res.data
      localStorage.setItem('user', JSON.stringify(this.user))
    },
    logout() {
      this.token = ''
      this.user = null
      localStorage.removeItem('token')
      localStorage.removeItem('user')
    }
  }
})
