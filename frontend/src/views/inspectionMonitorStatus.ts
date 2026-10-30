export type InspectionMonitorStatus = '未接入' | '离线' | '测试中' | '空闲'

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
