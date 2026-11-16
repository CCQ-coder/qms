import assert from 'node:assert/strict'
import test from 'node:test'
import { flyingProbePassRate, useTelemetryHistory } from './useTelemetryHistory.ts'
import type { EquipmentTelemetryRecord } from '../types/qms.ts'

function record(id: string): EquipmentTelemetryRecord {
  return {
    id, equipmentId: 'equipment-1', equipmentCode: 'FPT-01', equipmentName: '飞针机',
    deviceTime: '2026-10-21T15:02:22', receivedAt: '2026-10-21T20:34:47',
    eventType: 'FLYING_PROBE_TEST_COUNTERS', materialNo: 'hp009209a-zc',
    machiningCount: 6, goodBoardCount: 5, badBoardCount: 1
  }
}

test('shows pass rate only when both tested and good counts are available', () => {
  assert.equal(flyingProbePassRate({ ...record('a'), machiningCount: 17, goodBoardCount: 13 }), '76.47%')
  assert.equal(flyingProbePassRate({ ...record('b'), machiningCount: 0 }), '—')
  assert.equal(flyingProbePassRate({ ...record('c'), goodBoardCount: undefined }), '—')
})

test('loads only the selected history page while exposing the full total', async () => {
  const queries: unknown[] = []
  const history = useTelemetryHistory(async (query) => {
    queries.push(query)
    return { items: [record(query.page === 2 ? 'older' : 'newest')],
      total: query.includeTotal ? 43 : undefined, pageSize: 10 }
  }, 'FLYING_PROBE_TEST_COUNTERS', '飞针机采集记录')

  await history.refresh()
  const refreshedAt = history.updatedAt.value
  assert.deepEqual(queries, [{ eventType: 'FLYING_PROBE_TEST_COUNTERS', limit: 10, page: 1, includeTotal: true }])
  assert.deepEqual(history.records.value.map((item) => item.id), ['newest'])
  assert.equal(history.total.value, 43)

  await history.loadPage(2, 10)
  assert.deepEqual(queries[1], { eventType: 'FLYING_PROBE_TEST_COUNTERS', limit: 10, page: 2, includeTotal: false })
  assert.deepEqual(history.records.value.map((item) => item.id), ['older'])
  assert.equal(history.page.value, 2)
  assert.equal(history.total.value, 43)
  assert.equal(history.updatedAt.value, refreshedAt)
})

test('keeps filters across pages and resets to page one when the size changes', async () => {
  const queries: unknown[] = []
  const history = useTelemetryHistory(async (query) => {
    queries.push(query)
    return { items: [], total: query.includeTotal ? 43 : undefined, pageSize: query.limit ?? 10 }
  }, 'FLYING_PROBE_TEST_COUNTERS', '飞针机采集记录')
  const filters = { workOrderNo: 'WO-1', customerOrderNo: 'ORDER-1', fromTime: '2026-11-01T00:00:00', keyword: '42946' }

  await history.refresh(filters)
  await history.loadPage(3, 10)
  await history.loadPage(1, 20)

  assert.deepEqual(queries, [
    { eventType: 'FLYING_PROBE_TEST_COUNTERS', limit: 10, page: 1, includeTotal: true, ...filters },
    { eventType: 'FLYING_PROBE_TEST_COUNTERS', limit: 10, page: 3, includeTotal: false, ...filters },
    { eventType: 'FLYING_PROBE_TEST_COUNTERS', limit: 20, page: 1, includeTotal: false, ...filters }
  ])
  assert.equal(history.page.value, 1)
  assert.equal(history.pageSize.value, 20)
  assert.equal(history.total.value, 43)
})

test('keeps the visible page and permits retry when a new page fails', async () => {
  let failNextPage = true
  const history = useTelemetryHistory(async (query) => {
    if (query.page === 2 && failNextPage) throw new Error('HTTP 500')
    return { items: [record(query.page === 2 ? 'older' : 'newest')], total: 43, pageSize: 10 }
  }, 'FLYING_PROBE_TEST_COUNTERS', '飞针机采集记录')

  await history.refresh()
  await history.loadPage(2, 10)
  assert.deepEqual(history.records.value.map((item) => item.id), ['newest'])
  assert.equal(history.page.value, 1)
  assert.match(history.error.value, /读取失败/)

  failNextPage = false
  await history.loadPage(2, 10)
  assert.deepEqual(history.records.value.map((item) => item.id), ['older'])
  assert.equal(history.page.value, 2)
  assert.equal(history.error.value, '')
})

test('clears stale results when a new filter fails', async () => {
  let fail = false
  const history = useTelemetryHistory(async () => {
    if (fail) throw new Error('HTTP 500')
    return { items: [record('old')], total: 43, pageSize: 10 }
  }, 'FINISHED_TEST', '设备检测记录')

  await history.refresh()
  fail = true
  await history.refresh({ keyword: '42946' })
  assert.deepEqual(history.records.value, [])
  assert.equal(history.total.value, 0)
  assert.match(history.error.value, /读取失败/)
})

test('loads Zhongbo reports using their own event type', async () => {
  const queries: unknown[] = []
  const history = useTelemetryHistory(async (query) => {
    queries.push(query)
    return { items: [], total: 0, pageSize: 10 }
  }, 'ZHONGBO_TEST_REPORT', '众博信测试报告')

  await history.refresh()
  assert.deepEqual(queries, [{ eventType: 'ZHONGBO_TEST_REPORT', limit: 10, page: 1, includeTotal: true }])
})
