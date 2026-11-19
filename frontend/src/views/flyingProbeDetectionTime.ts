import { formatDateTime } from '../utils/display-text.ts'

type FlyingProbeTimeSource = {
  deviceTime: string
  correctedDeviceTime?: string | null
}

function localTimestampMillis(value: string, allowFraction: boolean): number | undefined {
  const pattern = allowFraction
    ? /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?$/
    : /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}$/
  if (!pattern.test(value)) return undefined
  const parsed = new Date(`${value}Z`)
  if (Number.isNaN(parsed.getTime()) || parsed.toISOString().slice(0, 19) !== value.slice(0, 19)) return undefined
  return parsed.getTime()
}

export function flyingProbeDetectionTime(record?: FlyingProbeTimeSource): { text: string; note: string } {
  if (!record) return { text: '—', note: '' }
  const corrected = record.correctedDeviceTime
  const rawMillis = localTimestampMillis(record.deviceTime, true)
  const correctedMillis = corrected ? localTimestampMillis(corrected, false) : undefined
  if (rawMillis !== undefined && correctedMillis !== undefined
      && Math.abs(correctedMillis - rawMillis) <= 2 * 60 * 60 * 1000) {
    return { text: formatDateTime(corrected), note: '' }
  }
  return { text: formatDateTime(record.deviceTime), note: '未校时' }
}
