import assert from 'node:assert/strict'
import test from 'node:test'
import { flyingProbePassRate, useTelemetryHistory } from './useTelemetryHistory.ts'
import type { EquipmentTelemetryRecord } from '../types/qms.ts'

function record(id: string): EquipmentTelemetryRecord {
  return {
    id,
    equipmentId: 'equipment-1',
    equipmentCode: 'FPT-01',
    equipmentName: '飞针机',
    deviceTime: '2026-10-21T15:02:22',
    receivedAt: '2026-10-21T20:34:47',
    eventType: 'FLYING_PROBE_TEST_COUNTERS',
    materialNo: 'hp009209a-zc',
    machiningCount: 6,
    goodBoardCount: 5,
    badBoardCount: 1
  }
}

test('shows pass rate only when both tested and good counts are available', () => {
  assert.equal(flyingProbePassRate({ ...record('a'), machiningCount: 17, goodBoardCount: 13 }), '76.47%')
  assert.equal(flyingProbePassRate({ ...record('b'), machiningCount: 0 }), '—')
  assert.equal(flyingProbePassRate({ ...record('c'), goodBoardCount: undefined }), '—')
})

test('loads every flying-probe history page before showing pagination', async () => {
  const queries: unknown[] = []
  const history = useTelemetryHistory(async (query) => {
    queries.push(query)
    return query.cursor
      ? { items: [record('older')], pageSize: 100 }
      : { items: [record('newest')], nextCursor: 'older-page', pageSize: 100 }
  }, 'FLYING_PROBE_TEST_COUNTERS', '飞针机采集记录')

  await history.refresh()

  assert.deepEqual(queries, [
    { eventType: 'FLYING_PROBE_TEST_COUNTERS', limit: 200 },
    { eventType: 'FLYING_PROBE_TEST_COUNTERS', limit: 200, cursor: 'older-page' }
  ])
  assert.deepEqual(history.records.value.map((item) => item.id), ['newest', 'older'])
  assert.equal(history.nextCursor.value, undefined)
  assert.equal(history.loaded.value, true)
  assert.equal(history.error.value, '')
})

test('keeps detection date filters while automatically loading every page', async () => {
  const queries: unknown[] = []
  const history = useTelemetryHistory(async (query) => {
    queries.push(query)
    return { items: [], nextCursor: query.cursor ? undefined : 'older-page', pageSize: 100 }
  }, 'FLYING_PROBE_TEST_COUNTERS', '飞针机采集记录')

  await history.refresh({ fromTime: '2026-11-01T00:00:00', toTime: '2026-11-09T23:59:59' })

  assert.deepEqual(queries, [
    { eventType: 'FLYING_PROBE_TEST_COUNTERS', limit: 200, fromTime: '2026-11-01T00:00:00', toTime: '2026-11-09T23:59:59' },
    { eventType: 'FLYING_PROBE_TEST_COUNTERS', limit: 200, fromTime: '2026-11-01T00:00:00', toTime: '2026-11-09T23:59:59', cursor: 'older-page' }
  ])
})

test('stops after the last page', async () => {
  const queries: unknown[] = []
  const history = useTelemetryHistory(async (query) => {
    queries.push(query)
    return query.cursor
      ? { items: [record('older')], pageSize: 100 }
      : { items: [record('newest')], nextCursor: 'older-page', pageSize: 100 }
  }, 'FLYING_PROBE_TEST_COUNTERS', '飞针机采集记录')

  await history.refresh()
  const newestPageRefreshedAt = history.updatedAt.value
  await history.loadMore()

  assert.deepEqual(history.records.value.map((item) => item.id), ['newest', 'older'])
  assert.deepEqual(queries, [
    { eventType: 'FLYING_PROBE_TEST_COUNTERS', limit: 200 },
    { eventType: 'FLYING_PROBE_TEST_COUNTERS', limit: 200, cursor: 'older-page' }
  ])
  assert.equal(history.nextCursor.value, undefined)
  assert.strictEqual(history.updatedAt.value, newestPageRefreshedAt)
})

test('keeps loaded pages and permits retry after a later page fails', async () => {
  let failNextPage = true
  const history = useTelemetryHistory(async (query) => {
    if (query.cursor && failNextPage) throw new Error('HTTP 500')
    return query.cursor
      ? { items: [record('older')], pageSize: 100 }
      : { items: [record('newest')], nextCursor: 'older-page', pageSize: 100 }
  }, 'FLYING_PROBE_TEST_COUNTERS', '飞针机采集记录')

  await history.refresh()
  assert.deepEqual(history.records.value.map((item) => item.id), ['newest'])
  assert.equal(history.nextCursor.value, 'older-page')
  assert.match(history.error.value, /读取失败/)

  failNextPage = false
  await history.loadMore()
  assert.deepEqual(history.records.value.map((item) => item.id), ['newest', 'older'])
  assert.equal(history.error.value, '')
})

test('loads Zhongbo reports using their own event type', async () => {
  const queries: unknown[] = []
  const history = useTelemetryHistory(async (query) => {
    queries.push(query)
    return { items: [], pageSize: 100 }
  }, 'ZHONGBO_TEST_REPORT', '众博信测试报告')

  await history.refresh()

  assert.deepEqual(queries, [{ eventType: 'ZHONGBO_TEST_REPORT', limit: 200 }])
})

test('keeps production order, device material and factory number filters across automatically loaded pages', async () => {
  const queries: unknown[] = []
  const history = useTelemetryHistory(async (query) => {
    queries.push(query)
    return query.cursor
      ? { items: [], pageSize: 100 }
      : { items: [record('newest')], nextCursor: 'older-page', pageSize: 100 }
  }, 'FLYING_PROBE_TEST_COUNTERS', '飞针机采集记录')

  await history.refresh({ workOrderNo: 'WO-1', internalProductNo: 'DEVICE-1', factoryInternalNo: '02PT42777A01' })

  assert.deepEqual(queries, [
    { eventType: 'FLYING_PROBE_TEST_COUNTERS', limit: 200, workOrderNo: 'WO-1', internalProductNo: 'DEVICE-1', factoryInternalNo: '02PT42777A01' },
    { eventType: 'FLYING_PROBE_TEST_COUNTERS', limit: 200, workOrderNo: 'WO-1', internalProductNo: 'DEVICE-1', factoryInternalNo: '02PT42777A01', cursor: 'older-page' }
  ])
})
