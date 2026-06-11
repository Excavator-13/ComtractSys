import axios from 'axios'

export const api = axios.create({
  baseURL: '/api/v1'
})

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

api.interceptors.response.use(
  (response) => {
    // For blob downloads, return the full response
    if (response.config.responseType === 'blob') {
      return response
    }
    return response.data
  },
  async (error) => {
    let message = error.response?.data?.message || error.message || '请求失败'
    if (error.response?.data instanceof Blob) {
      try {
        const text = await error.response.data.text()
        const data = JSON.parse(text)
        message = data.message || message
      } catch {}
    }
    const isLoginRequest = error.config?.url?.includes('/auth/login')
    if (error.response?.status === 401 && !isLoginRequest) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      if (window.location.pathname !== '/login') {
        const redirect = encodeURIComponent(window.location.pathname + window.location.search)
        window.location.href = `/login?redirect=${redirect}`
      }
    }
    if (error.response?.status === 403) {
      window.dispatchEvent(new CustomEvent('auth-permissions-stale'))
    }
    return Promise.reject(new Error(message))
  }
)
