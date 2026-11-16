import assert from 'node:assert/strict'
import test from 'node:test'
import { flyingProbePassRate, useFlyingProbeHistory } from './useFlyingProbeHistory.ts'
import type { EquipmentTelemetryRecord } from '../types/qms.ts'

function record(id: string): EquipmentTelemetryRecord {
  return {
    id, equipmentId: 'equipment-1', equipmentCode: 'FPT-01', equipmentName: '飞针机',
    deviceTime: '2026-10-21T15:02:22', receivedAt: '2026-10-21T20:34:47',
    eventType: 'FLYING_PROBE_TEST_COUNTERS', materialNo: 'hp009209a-zc',
    machiningCount: 6, goodBoardCount: 5, badBoardCount: 1
  }
}

test('shows each flying-probe record pass rate from its own counters', () => {
  assert.equal(flyingProbePassRate({ ...record('a'), machiningCount: 17, goodBoardCount: 13 }), '76.47%')
  assert.equal(flyingProbePassRate({ ...record('b'), machiningCount: 0 }), '—')
  assert.equal(flyingProbePassRate({ ...record('c'), goodBoardCount: undefined }), '—')
})

test('counts once, then requests only the selected page', async () => {
  const queries: unknown[] = []
  const history = useFlyingProbeHistory(async (query) => {
    queries.push(query)
    return query.page === 2
      ? { items: [record('second')], pageSize: 10 }
      : { items: [record('first')], pageSize: 10, total: 21 }
  })

  await history.refresh({ workOrderNo: 'WO-123', internalProductNo: 'P2L018680B' })
  await history.changePage(2, 10)

  assert.deepEqual(queries, [
    { eventType: 'FLYING_PROBE_TEST_COUNTERS', limit: 10, page: 1, includeTotal: true,
      workOrderNo: 'WO-123', internalProductNo: 'P2L018680B' },
    { eventType: 'FLYING_PROBE_TEST_COUNTERS', limit: 10, page: 2, includeTotal: false,
      workOrderNo: 'WO-123', internalProductNo: 'P2L018680B' }
  ])
  assert.deepEqual(history.records.value.map((item) => item.id), ['second'])
  assert.equal(history.total.value, 21)
  assert.equal(history.page.value, 2)
})

test('page-size change fetches only the new first page and keeps the count', async () => {
  const queries: unknown[] = []
  const history = useFlyingProbeHistory(async (query) => {
    queries.push(query)
    return { items: [record('first')], pageSize: query.limit ?? 10, total: 21 }
  })
  await history.refresh()
  await history.changePage(1, 20)
  assert.equal(history.pageSize.value, 20)
  assert.equal(history.total.value, 21)
  assert.deepEqual(queries, [
    { eventType: 'FLYING_PROBE_TEST_COUNTERS', limit: 10, page: 1, includeTotal: true },
    { eventType: 'FLYING_PROBE_TEST_COUNTERS', limit: 20, page: 1, includeTotal: false }
  ])
})

test('failed page load keeps the last successful page', async () => {
  const history = useFlyingProbeHistory(async (query) => {
    if (query.page === 2) throw new Error('HTTP 500')
    return { items: [record('first')], pageSize: 10, total: 21 }
  })
  await history.refresh()
  await history.changePage(2, 10)
  assert.equal(history.page.value, 1)
  assert.deepEqual(history.records.value.map((item) => item.id), ['first'])
  assert.match(history.error.value, /读取失败/)
})
