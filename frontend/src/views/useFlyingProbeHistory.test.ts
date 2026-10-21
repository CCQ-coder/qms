import assert from 'node:assert/strict'
import test from 'node:test'
import { useFlyingProbeHistory } from './useFlyingProbeHistory.ts'
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

test('loads only flying-probe history and exposes its next cursor', async () => {
  const queries: unknown[] = []
  const history = useFlyingProbeHistory(async (query) => {
    queries.push(query)
    return { items: [record('newest')], nextCursor: 'older-page', pageSize: 100 }
  })

  await history.refresh()

  assert.deepEqual(queries, [{ eventType: 'FLYING_PROBE_TEST_COUNTERS', limit: 100 }])
  assert.deepEqual(history.records.value.map((item) => item.id), ['newest'])
  assert.equal(history.nextCursor.value, 'older-page')
  assert.equal(history.loaded.value, true)
  assert.equal(history.error.value, '')
})

test('appends the next page once and stops when there is no cursor', async () => {
  const queries: unknown[] = []
  const history = useFlyingProbeHistory(async (query) => {
    queries.push(query)
    return query.cursor
      ? { items: [record('older')], pageSize: 100 }
      : { items: [record('newest')], nextCursor: 'older-page', pageSize: 100 }
  })

  await history.refresh()
  const newestPageRefreshedAt = history.updatedAt.value
  await history.loadMore()
  await history.loadMore()

  assert.deepEqual(history.records.value.map((item) => item.id), ['newest', 'older'])
  assert.deepEqual(queries, [
    { eventType: 'FLYING_PROBE_TEST_COUNTERS', limit: 100 },
    { eventType: 'FLYING_PROBE_TEST_COUNTERS', limit: 100, cursor: 'older-page' }
  ])
  assert.equal(history.nextCursor.value, undefined)
  assert.strictEqual(history.updatedAt.value, newestPageRefreshedAt)
})

test('reports failed page loads without replacing previously loaded records', async () => {
  let failNextPage = true
  const history = useFlyingProbeHistory(async (query) => {
    if (query.cursor && failNextPage) throw new Error('HTTP 500')
    return query.cursor
      ? { items: [record('older')], pageSize: 100 }
      : { items: [record('newest')], nextCursor: 'older-page', pageSize: 100 }
  })

  await history.refresh()
  await history.loadMore()

  assert.deepEqual(history.records.value.map((item) => item.id), ['newest'])
  assert.equal(history.nextCursor.value, 'older-page')
  assert.match(history.error.value, /读取失败/)

  failNextPage = false
  await history.loadMore()
  assert.deepEqual(history.records.value.map((item) => item.id), ['newest', 'older'])
  assert.equal(history.error.value, '')
})
