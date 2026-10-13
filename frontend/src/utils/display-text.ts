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

/** QMS 容器以 UTC 保存的无时区接收时间，展示时换算为浏览器本地时间。 */
export function formatUtcDateTime(value?: string | Date | null): string {
  if (typeof value !== 'string') return formatDateTime(value)
  const normalized = value.trim()
  const utcMatch = normalized.match(/^(\d{4}-\d{2}-\d{2})[T\s](\d{2}:\d{2}(?::\d{2})?(?:\.\d+)?)$/)
  return formatDateTime(utcMatch ? `${utcMatch[1]}T${utcMatch[2]}Z` : normalized)
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

export function telemetryEventTypeText(value?: string | null): string {
  if (!value?.trim()) return '—'
  const labels: Record<string, string> = {
    LEGACY_MONITOR_SNAPSHOT: '设备实时数据上报',
    TELEMETRY: '设备数据上报',
    STATUS_CHANGED: '工作状态变更',
    ALARM: '报警信息',
    ALARM_RAISED: '报警发生',
    ALARM_CLEARED: '报警解除',
    MACHINING_COMPLETED: '加工完成',
    HEARTBEAT: '设备心跳',
    PRODUCTION_PARAMETERS: '加工参数上报',
    OEE_REPORTED: '设备利用率上报',
    MODE_CHANGED: '工作模式变更',
    EXPOSURE_RESULT: '曝光结果上报'
  }
  return labels[value.trim()] ?? '其他设备事件'
}

export function equipmentAlarmReasonText(item: {
  status?: string
  effectiveStatus?: string
  reportedStatus?: string
  legacyState?: string
  alarmMessage?: string | null
  screenText?: string | null
  alarmCode?: string | null
}): string {
  const alarm = item.status === 'ALARM' || item.effectiveStatus === 'ALARM'
    || item.reportedStatus === 'ALARM' || item.legacyState === 'ALAM'
  if (!alarm) return '—'
  return item.alarmMessage?.trim() || item.screenText?.trim()
    || (item.alarmCode?.trim() ? `报警代码：${item.alarmCode.trim()}` : '设备已报警，但未上报报警文字')
}

/** 主轴选择掩码不代表设备的加工、报警等运行状态。 */
export function spindleStatusText(value?: string | null): string {
  const mask = value?.trim()
  if (!mask) return '—'
  // Only decode binary masks; retain unfamiliar gateway values for diagnosis.
  if (!/^[01]+$/.test(mask)) return `未知主轴信息（${mask}）`
  const selected = [...mask].filter((bit) => bit === '1').length
  return selected === 0 ? `未选择主轴（0/${mask.length}）` : `已选择 ${selected}/${mask.length} 个主轴`
}

export function formatDurationSeconds(value?: number | null): string {
  if (value == null || !Number.isFinite(value)) return '—'
  const totalSeconds = Math.max(0, Math.floor(value))
  const hours = Math.floor(totalSeconds / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  const seconds = totalSeconds % 60
  return `${hours} 小时 ${minutes} 分 ${seconds} 秒`
}
