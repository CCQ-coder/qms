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
  const total = ref(0)
  const page = ref(1)
  const pageSize = ref(10)
  const loading = ref(false)
  const loaded = ref(false)
  const error = ref('')
  const updatedAt = ref<Date>()
  let search: Pick<EquipmentTelemetryHistoryQuery, 'workOrderNo' | 'customerOrderNo' | 'internalProductNo' | 'factoryInternalNo' | 'unmatchedOnly' | 'fromTime' | 'toTime' | 'keyword'> = {}

  async function load(nextPage: number, nextSize: number, includeTotal = false) {
    if (loading.value) return
    loading.value = true
    error.value = ''
    try {
      const result = await fetchPage({ eventType, limit: nextSize, page: nextPage, includeTotal, ...search })
      records.value = result.items
      if (includeTotal) total.value = result.total ?? 0
      page.value = nextPage
      pageSize.value = nextSize
      loaded.value = true
      if (includeTotal) updatedAt.value = new Date()
    } catch {
      error.value = `${label}读取失败，请重试。`
      if (includeTotal) {
        records.value = []
        total.value = 0
        page.value = 1
        updatedAt.value = undefined
      }
    } finally {
      loading.value = false
    }
  }

  return {
    records, total, page, pageSize, loading, loaded, error, updatedAt,
    refresh: (filters: Pick<EquipmentTelemetryHistoryQuery, 'workOrderNo' | 'customerOrderNo' | 'internalProductNo' | 'factoryInternalNo' | 'unmatchedOnly' | 'fromTime' | 'toTime' | 'keyword'> = search) => {
      search = filters
      return load(1, pageSize.value, true)
    },
    loadPage: load
  }
}
