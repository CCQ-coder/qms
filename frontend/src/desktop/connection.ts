import { invoke, isTauri } from '@tauri-apps/api/core'
import { http } from '@/api/http'

export type DesktopConnection = {
  apiBaseUrl: string
}

const DEFAULT_API_BASE_URL = 'http://127.0.0.1:8080'
let activeApiBaseUrl = DEFAULT_API_BASE_URL

export function isDesktopApplication(): boolean {
  return isTauri()
}

export function normalizeDesktopApiBaseUrl(value: string): string {
  const normalized = value.trim().replace(/\/+$/, '')
  if (!/^https?:\/\/[\w.-]+(?::\d+)?(?:\/[^\s]*)?$/i.test(normalized)) {
    throw new Error('请输入有效的 HTTP 或 HTTPS QMS 服务地址。')
  }
  return normalized
}

export function desktopApiBaseUrl(): string {
  return activeApiBaseUrl
}

export async function initializeDesktopConnection(): Promise<void> {
  if (!isDesktopApplication()) return
  const connection = await invoke<DesktopConnection>('read_desktop_connection')
  activeApiBaseUrl = normalizeDesktopApiBaseUrl(connection.apiBaseUrl)
  http.defaults.baseURL = activeApiBaseUrl
}

export async function saveDesktopConnection(apiBaseUrl: string): Promise<void> {
  if (!isDesktopApplication()) return
  const normalized = normalizeDesktopApiBaseUrl(apiBaseUrl)
  const connection = await invoke<DesktopConnection>('save_desktop_connection', { apiBaseUrl: normalized })
  activeApiBaseUrl = normalizeDesktopApiBaseUrl(connection.apiBaseUrl)
  http.defaults.baseURL = activeApiBaseUrl
}
