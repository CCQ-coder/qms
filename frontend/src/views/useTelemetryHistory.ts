import { ref } from 'vue'
import type { EquipmentTelemetryHistoryPage, EquipmentTelemetryHistoryQuery, EquipmentTelemetryRecord } from '../types/qms'

export function flyingProbePassRate(record: EquipmentTelemetryRecord) {
  const tested = record.machiningCount
  const good = record.goodBoardCount
  return tested != null && tested > 0 && good != null ? `${(good / tested * 100).toFixed(2)}%` : '—'
}

export function useTelemetryHistory(
  fetchPage: (query: EquipmentTelemetryHistoryQuery) => Promise<EquipmentTelemetryHistoryPage>,
  eventType: string,
  label: string
) {
  const records = ref<EquipmentTelemetryRecord[]>([])
  const nextCursor = ref<string>()
  const loading = ref(false)
  const loaded = ref(false)
  const error = ref('')
  const updatedAt = ref<Date>()
  let search: Pick<EquipmentTelemetryHistoryQuery, 'workOrderNo' | 'internalProductNo'> = {}

  async function load(cursor?: string) {
    if (loading.value) return
    loading.value = true
    error.value = ''
    try {
      const page = await fetchPage({ eventType, limit: 100, ...search, ...(cursor ? { cursor } : {}) })
      records.value = cursor ? [...records.value, ...page.items] : page.items
      nextCursor.value = page.nextCursor
      loaded.value = true
      if (!cursor) updatedAt.value = new Date()
    } catch {
      error.value = `${label}读取失败，请重试。`
    } finally {
      loading.value = false
    }
  }

  return {
    records, nextCursor, loading, loaded, error, updatedAt,
    refresh: (filters: Pick<EquipmentTelemetryHistoryQuery, 'workOrderNo' | 'internalProductNo'> = search) => {
      search = filters
      return load()
    },
    loadMore: () => nextCursor.value ? load(nextCursor.value) : Promise.resolve()
  }
}
