import { isHostOnline } from '../utils/host-status.ts'
import { statusText } from '../utils/display-text.ts'

export type InspectionMonitorStatus = '未接入' | '离线' | '测试中' | '空闲'

export function monitorStatusLabel(status: InspectionMonitorStatus | '状态不可用', equipmentStatus: string): string {
  return status === '未接入' ? statusText(equipmentStatus) : status
}

const STATUS_PRIORITY = { 测试中: 0, 空闲: 1, 离线: 2, 未接入: 3, 状态不可用: 4 } as const

export function sortInspectionMonitorDevices<T>(
  devices: readonly T[],
  statusOf: (device: T) => InspectionMonitorStatus | '状态不可用'
): T[] {
  return [...devices].sort((left, right) => STATUS_PRIORITY[statusOf(left)] - STATUS_PRIORITY[statusOf(right)])
}

const ACTIVE_WINDOW_MS = 7 * 60 * 1000

export function deriveInspectionMonitorStatus(
  runtimeOnline: boolean | undefined,
  latestProbeReceivedAt?: string,
  nowMs: number = Date.now()
): InspectionMonitorStatus {
  if (runtimeOnline === undefined) return '未接入'
  if (!runtimeOnline) return '离线'
  if (!latestProbeReceivedAt) return '空闲'

  const receivedAtMs = Date.parse(latestProbeReceivedAt)
  if (!Number.isFinite(receivedAtMs)) return '空闲'

  return nowMs - receivedAtMs <= ACTIVE_WINDOW_MS ? '测试中' : '空闲'
}

export function inspectionDeviceOnline(
  runtimeOnline: boolean | undefined,
  hostReceivedAt?: string,
  nowMs: number = Date.now(),
  offlineAfterSeconds = 300
): boolean | undefined {
  return hostReceivedAt === undefined ? runtimeOnline : isHostOnline(hostReceivedAt, nowMs, offlineAfterSeconds)
}
