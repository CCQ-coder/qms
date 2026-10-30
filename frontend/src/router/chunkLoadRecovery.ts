const RECOVERY_KEY_PREFIX = 'qms:chunk-load-recovery:'

interface RecoveryStorage {
  getItem(key: string): string | null
  setItem(key: string, value: string): void
  removeItem(key: string): void
}

interface ChunkLoadRecoveryOptions {
  routeKey: string
  storage: RecoveryStorage
  reload: () => void
}

function recoveryKey(routeKey: string) {
  return `${RECOVERY_KEY_PREFIX}${routeKey}`
}

function errorDescription(error: unknown) {
  if (typeof error === 'string') return error
  if (error instanceof Error) return `${error.name}: ${error.message}`
  if (error && typeof error === 'object') {
    const value = error as { name?: unknown; message?: unknown }
    return `${String(value.name ?? '')}: ${String(value.message ?? '')}`
  }
  return String(error)
}

export function isChunkLoadError(error: unknown) {
  const description = errorDescription(error)
  return /ChunkLoadError/i.test(description)
    || /Failed to fetch dynamically imported module/i.test(description)
    || /Importing a module script failed/i.test(description)
    || /Failed to load module script/i.test(description)
    || /Loading chunk [\w-]+ failed/i.test(description)
}

export function recoverFromChunkLoadError(error: unknown, options: ChunkLoadRecoveryOptions) {
  if (!isChunkLoadError(error)) return false

  const key = recoveryKey(options.routeKey)
  try {
    if (options.storage.getItem(key)) return false
    options.storage.setItem(key, new Date().toISOString())
  } catch (storageError) {
    console.error('无法记录页面资源恢复状态，已取消自动刷新以避免循环', storageError)
    return false
  }

  options.reload()
  return true
}

export function clearChunkLoadRecovery(routeKey: string, storage: RecoveryStorage) {
  try {
    storage.removeItem(recoveryKey(routeKey))
  } catch (storageError) {
    console.warn('清理页面资源恢复状态失败', storageError)
  }
}
