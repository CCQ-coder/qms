import axios from 'axios'
import { localAccessToken, signOutLocal } from '@/auth/local-session'

export const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '',
  timeout: 12_000
})

http.interceptors.request.use((config) => {
  const token = localAccessToken()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    if (!axios.isAxiosError(error)) return Promise.reject(error)

    const message = typeof error.response?.data?.message === 'string' ? error.response.data.message : undefined
    const requestPath = error.config?.url ?? ''
    if (error.response?.status === 401 && !requestPath.startsWith('/api/v1/auth/')) {
      signOutLocal()
      if (window.location.pathname !== '/login') {
        const redirect = `${window.location.pathname}${window.location.search}${window.location.hash}`
        window.location.replace(`/login?redirect=${encodeURIComponent(redirect)}`)
      }
    }
    return Promise.reject(message ? new Error(message) : error)
  }
)
