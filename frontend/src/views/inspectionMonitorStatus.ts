import { isHostOnline } from '../utils/host-status.ts'
import { statusText } from '../utils/display-text.ts'

export type InspectionMonitorStatus = '未接入' | '离线' | '测试中' | '空闲'

export function monitorStatusLabel(status: InspectionMonitorStatus | '状态不可用', equipmentStatus: string): string {
  return status === '未接入' ? statusText(equipmentStatus) : status
}

const STATUS_PRIORITY = { 测试中: 0, 空闲: 1, 未接入: 2, 离线: 3, 状态不可用: 4 } as const

export function sortInspectionMonitorDevices<T extends { status: string }>(
  devices: readonly T[],
  statusOf: (device: T) => InspectionMonitorStatus | '状态不可用'
): T[] {
  return [...devices].sort((left, right) => {
    const leftInUse = left.status === 'IN_USE'
    const rightInUse = right.status === 'IN_USE'
    if (leftInUse !== rightInUse) return leftInUse ? -1 : 1
    return STATUS_PRIORITY[statusOf(left)] - STATUS_PRIORITY[statusOf(right)]
  })
}

const ACTIVE_WINDOW_MS = 7 * 60 * 1000
const DEFAULT_OFFLINE_AFTER_SECONDS = 300

export function deriveInspectionMonitorStatus(
  hostReceivedAt: string | undefined,
  latestProbeReceivedAt?: string,
  nowMs: number = Date.now(),
  offlineAfterSeconds: number = DEFAULT_OFFLINE_AFTER_SECONDS
): InspectionMonitorStatus {
  if (!hostReceivedAt) return '未接入'
  if (!isHostOnline(hostReceivedAt, nowMs, offlineAfterSeconds)) return '离线'
  if (!latestProbeReceivedAt) return '空闲'

  const receivedAtMs = Date.parse(latestProbeReceivedAt)
  if (!Number.isFinite(receivedAtMs)) return '空闲'

  return nowMs - receivedAtMs <= ACTIVE_WINDOW_MS ? '测试中' : '空闲'
}
