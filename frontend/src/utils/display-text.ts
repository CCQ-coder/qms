const statusLabels: Record<string, string> = {
  ACTIVE: '启用',
  INACTIVE: '停用',
  PENDING: '待处理',
  COMPLETED: '已完成',
  OVERDUE: '已逾期',
  PASS: '合格',
  FAIL: '不合格',
  OPEN: '待处置',
  CLOSED: '已关闭',
  IN_PROGRESS: '处理中',
  CANCELLED: '已取消',
  CANCELED: '已取消',
  DRAFT: '草稿',
  NORMAL: '正常',
  DUE_SOON: '即将到期',
  NOT_SET: '未设置',
  IN_USE: '在用',
  ALARM: '告警中',
  IDLE: '空闲',
  PROCESSING: '加工中',
  STOPPED: '已停止',
  OFFLINE: '离线',
  MAINTENANCE: '维护中',
  DISABLED: '停用'
}

const taskTypeLabels: Record<string, string> = {
  PROCESS: '制程巡检',
  FINISHED: '成品检验'
}

const legacyStateLabels: Record<string, string> = {
  WORK: '加工中',
  WAIT: '等待',
  IDLE: '空闲',
  STOP: '已停止',
  ALAM: '报警中',
  OFFLINE: '离线'
}

function pad(value: number): string {
  return String(value).padStart(2, '0')
}

function formatBrowserDate(value: Date, includeTime: boolean): string {
  const date = `${value.getFullYear()}-${pad(value.getMonth() + 1)}-${pad(value.getDate())}`
  return includeTime
    ? `${date} ${pad(value.getHours())}:${pad(value.getMinutes())}:${pad(value.getSeconds())}`
    : date
}

/**
 * 格式化接口返回的日期，统一为 YYYY-MM-DD。
 */
export function formatDate(value?: string | Date | null): string {
  if (!value) return '—'
  if (value instanceof Date) return Number.isNaN(value.getTime()) ? '—' : formatBrowserDate(value, false)

  const normalized = value.trim()
  const match = normalized.match(/^(\d{4}-\d{2}-\d{2})/)
  if (match) return match[1]

  const date = new Date(normalized)
  return Number.isNaN(date.getTime()) ? normalized : formatBrowserDate(date, false)
}

/**
 * 格式化接口返回的时间戳，统一为 YYYY-MM-DD HH:mm:ss，并隐藏毫秒/微秒。
 * 无时区的服务端本地时间直接保留，带时区的 ISO 时间会换算为当前客户端本地时间。
 */
export function formatDateTime(value?: string | Date | null): string {
  if (!value) return '—'
  if (value instanceof Date) return Number.isNaN(value.getTime()) ? '—' : formatBrowserDate(value, true)

  const normalized = value.trim()
  const localMatch = normalized.match(/^(\d{4}-\d{2}-\d{2})[T\s](\d{2}:\d{2})(?::(\d{2}))?(?:\.\d+)?$/)
  if (localMatch) return `${localMatch[1]} ${localMatch[2]}:${localMatch[3] ?? '00'}`

  const date = new Date(normalized)
  return Number.isNaN(date.getTime()) ? normalized : formatBrowserDate(date, true)
}

export function statusText(value?: string): string {
  if (!value) return '—'
  return statusLabels[value] ?? value
}

export function taskTypeText(value?: string): string {
  if (!value) return '—'
  return taskTypeLabels[value] ?? value
}

export function legacyStateText(value?: string): string {
  if (!value) return '—'
  return legacyStateLabels[value] ?? value
}

export function formatDurationSeconds(value?: number | null): string {
  if (value == null || !Number.isFinite(value)) return '—'
  const totalSeconds = Math.max(0, Math.floor(value))
  const hours = Math.floor(totalSeconds / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  const seconds = totalSeconds % 60
  return `${hours} 小时 ${minutes} 分 ${seconds} 秒`
}
