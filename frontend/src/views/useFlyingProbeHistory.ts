import { ref } from 'vue'
import type { EquipmentTelemetryHistoryPage, EquipmentTelemetryHistoryQuery, EquipmentTelemetryRecord } from '../types/qms'

export function flyingProbePassRateFromCounts(tested?: number | null, good?: number | null) {
  return tested != null && tested > 0 && good != null ? `${(good / tested * 100).toFixed(2)}%` : '—'
}

export function flyingProbePassRate(record: EquipmentTelemetryRecord) {
  return flyingProbePassRateFromCounts(record.machiningCount, record.goodBoardCount)
}

export function useFlyingProbeHistory(
  fetchPage: (query: EquipmentTelemetryHistoryQuery) => Promise<EquipmentTelemetryHistoryPage>
) {
  const records = ref<EquipmentTelemetryRecord[]>([])
  const page = ref(1)
  const pageSize = ref(10)
  const total = ref(0)
  const loading = ref(false)
  const loaded = ref(false)
  const error = ref('')
  const updatedAt = ref<Date>()
  let search: Pick<EquipmentTelemetryHistoryQuery, 'equipmentId' | 'workOrderNo' | 'customerOrderNo' | 'internalProductNo'> = {}

  async function load(selectedPage: number, selectedSize: number, includeTotal: boolean) {
    if (loading.value) return
    loading.value = true
    error.value = ''
    try {
      const result = await fetchPage({ eventType: 'FLYING_PROBE_TEST_COUNTERS', limit: selectedSize,
        page: selectedPage, includeTotal, ...search })
      records.value = result.items
      if (includeTotal) total.value = result.total ?? 0
      page.value = selectedPage
      pageSize.value = selectedSize
      loaded.value = true
      if (includeTotal) updatedAt.value = new Date()
    } catch {
      error.value = '飞针机采集记录读取失败，请重试。'
    } finally {
      loading.value = false
    }
  }

  return {
    records, page, pageSize, total, loading, loaded, error, updatedAt,
    refresh: (filters: Pick<EquipmentTelemetryHistoryQuery, 'equipmentId' | 'workOrderNo' | 'customerOrderNo' | 'internalProductNo'> = search) => {
      search = filters
      return load(1, pageSize.value, true)
    },
    changePage: (selectedPage: number, selectedSize: number) =>
      load(selectedPage, selectedSize, false)
  }
}
