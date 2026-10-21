import { ref } from 'vue'
import type { EquipmentTelemetryHistoryPage, EquipmentTelemetryHistoryQuery, EquipmentTelemetryRecord } from '../types/qms'

export function useFlyingProbeHistory(
  fetchPage: (query: EquipmentTelemetryHistoryQuery) => Promise<EquipmentTelemetryHistoryPage>
) {
  const records = ref<EquipmentTelemetryRecord[]>([])
  const nextCursor = ref<string>()
  const loading = ref(false)
  const loaded = ref(false)
  const error = ref('')
  const updatedAt = ref<Date>()

  async function load(cursor?: string) {
    if (loading.value) return
    loading.value = true
    error.value = ''
    try {
      const page = await fetchPage({ eventType: 'FLYING_PROBE_TEST_COUNTERS', limit: 100, ...(cursor ? { cursor } : {}) })
      records.value = cursor ? [...records.value, ...page.items] : page.items
      nextCursor.value = page.nextCursor
      loaded.value = true
      if (!cursor) updatedAt.value = new Date()
    } catch {
      error.value = '飞针机采集记录读取失败，请重试。'
    } finally {
      loading.value = false
    }
  }

  return {
    records, nextCursor, loading, loaded, error, updatedAt,
    refresh: () => load(),
    loadMore: () => nextCursor.value ? load(nextCursor.value) : Promise.resolve()
  }
}
