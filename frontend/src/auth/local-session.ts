import type { PasswordLogin } from '@/types/qms'

export type LocalSession = {
  tenantCode: string
  actor: string
  displayName: string
  roleCode: 'PLATFORM_ADMIN' | 'TENANT_ADMIN' | 'SUPERVISOR' | 'INSPECTOR'
  accessToken: string
  expiresAt: number
}

const SESSION_KEY = 'qms.password.session'

export function localSession(): LocalSession | undefined {
  try {
    const raw = sessionStorage.getItem(SESSION_KEY)
    if (!raw) return undefined
    const value = JSON.parse(raw) as Partial<LocalSession>
    if (!value.tenantCode || !value.actor || !value.displayName || !value.roleCode || !value.accessToken
      || !value.expiresAt || value.expiresAt <= Date.now()) {
      sessionStorage.removeItem(SESSION_KEY)
      return undefined
    }
    return value as LocalSession
  } catch {
    sessionStorage.removeItem(SESSION_KEY)
    return undefined
  }
}

export function signInWithPassword(login: PasswordLogin): LocalSession {
  const session: LocalSession = {
    tenantCode: login.tenantCode,
    actor: login.userName,
    displayName: login.displayName,
    roleCode: login.roleCode,
    accessToken: login.accessToken,
    expiresAt: Date.now() + login.expiresIn * 1000
  }
  sessionStorage.setItem(SESSION_KEY, JSON.stringify(session))
  localStorage.removeItem('qms.local.session')
  localStorage.removeItem('qms.tenantCode')
  localStorage.removeItem('qms.actor')
  return session
}

export function localAccessToken(): string | undefined {
  return localSession()?.accessToken
}

export function signOutLocal(): void {
  sessionStorage.removeItem(SESSION_KEY)
  localStorage.removeItem('qms.local.session')
  localStorage.removeItem('qms.actor')
  localStorage.removeItem('qms.tenantCode')
}
