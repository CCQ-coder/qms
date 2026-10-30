import { isHostOnline } from '../utils/host-status.ts'

export type InspectionMonitorStatus = '未接入' | '离线' | '测试中' | '空闲'

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
