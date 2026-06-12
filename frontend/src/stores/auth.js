import { defineStore } from 'pinia'
import { api } from '../api'

const authStorage = window.sessionStorage
localStorage.removeItem('token')
localStorage.removeItem('user')

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: authStorage.getItem('token') || '',
    user: JSON.parse(authStorage.getItem('user') || 'null'),
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
      authStorage.setItem('token', this.token)
      authStorage.setItem('user', JSON.stringify(this.user))
    },
    async refreshMe(force = false) {
      if (!this.token) return
      if (!force && Date.now() - this.lastRefreshedAt < 30000) return
      const res = await api.get('/auth/me')
      this.user = res.data
      this.lastRefreshedAt = Date.now()
      authStorage.setItem('user', JSON.stringify(this.user))
    },
    async updateProfile(payload) {
      const res = await api.put('/auth/profile', payload)
      this.user = res.data
      authStorage.setItem('user', JSON.stringify(this.user))
    },
    logout() {
      this.token = ''
      this.user = null
      authStorage.removeItem('token')
      authStorage.removeItem('user')
      localStorage.removeItem('token')
      localStorage.removeItem('user')
    }
  }
})
