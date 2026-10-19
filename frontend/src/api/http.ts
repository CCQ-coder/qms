import axios from 'axios'
import { accessToken, isOidcEnabled } from '@/auth/oidc'
import { localAccessToken, signOutLocal } from '@/auth/local-session'

export const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '',
  timeout: 12_000
})

http.interceptors.request.use((config) => {
  if (isOidcEnabled()) {
    const token = accessToken()
    if (token) config.headers.Authorization = `Bearer ${token}`
  } else {
    const token = localAccessToken()
    if (token) config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    if (!axios.isAxiosError(error)) return Promise.reject(error)

    const message = typeof error.response?.data?.message === 'string' ? error.response.data.message : undefined
    const requestPath = error.config?.url ?? ''
    if (error.response?.status === 401 && !isOidcEnabled() && !requestPath.startsWith('/api/v1/auth/')) {
      signOutLocal()
      const hashRouting = import.meta.env.VITE_ROUTER_HASH === 'true'
      const onLogin = hashRouting ? window.location.hash.startsWith('#/login') : window.location.pathname === '/login'
      if (!onLogin) {
        const redirect = hashRouting ? (window.location.hash.slice(1) || '/dashboard')
          : `${window.location.pathname}${window.location.search}${window.location.hash}`
        const loginPath = hashRouting ? '/#/login' : '/login'
        window.location.replace(`${loginPath}?redirect=${encodeURIComponent(redirect)}`)
      }
    }
    return Promise.reject(message ? new Error(message) : error)
  }
)
