import assert from 'node:assert/strict'
import test from 'node:test'
import { paginateRows } from '../utils/pagination.ts'
import { deriveInspectionMonitorStatus, monitorStatusLabel, sortInspectionMonitorDevices } from './inspectionMonitorStatus.ts'

const NOW_MS = Date.parse('2026-10-29T08:00:00.000Z')

test('missing computer heartbeat remains 未接入', () => {
  assert.equal(deriveInspectionMonitorStatus(undefined, '2026-10-29T07:59:00.000Z', NOW_MS), '未接入')
})

test('missing computer heartbeat displays equipment ledger status', () => {
  assert.equal(monitorStatusLabel('未接入', 'IN_USE'), '在用')
  assert.equal(monitorStatusLabel('未接入', 'MAINTENANCE'), '维护中')
  assert.equal(monitorStatusLabel('离线', 'IN_USE'), '离线')
})

test('stale computer heartbeat remains 离线 even with recent probe data', () => {
  assert.equal(deriveInspectionMonitorStatus(
    '2026-10-29T07:54:59.999Z',
    '2026-10-29T07:59:00.000Z',
    NOW_MS,
    300
  ), '离线')
})

test('fresh computer heartbeat without probe data is 空闲', () => {
  assert.equal(deriveInspectionMonitorStatus('2026-10-29T07:59:00.000Z', undefined, NOW_MS, 300), '空闲')
})

test('fresh computer heartbeat with probe data inside seven minutes is 测试中', () => {
  assert.equal(deriveInspectionMonitorStatus(
    '2026-10-29T07:59:00.000Z',
    '2026-10-29T07:54:00.000Z',
    NOW_MS,
    300
  ), '测试中')
})

test('heartbeat exactly at the offline threshold remains online', () => {
  assert.equal(deriveInspectionMonitorStatus('2026-10-29T07:55:00.000Z', undefined, NOW_MS, 300), '空闲')
})

test('online computer with probe data exactly seven minutes old is 测试中', () => {
  assert.equal(deriveInspectionMonitorStatus(
    '2026-10-29T07:59:00.000Z',
    '2026-10-29T07:53:00.000Z',
    NOW_MS,
    300
  ), '测试中')
})

test('online computer with probe data one millisecond older than seven minutes is 空闲', () => {
  assert.equal(deriveInspectionMonitorStatus(
    '2026-10-29T07:59:00.000Z',
    '2026-10-29T07:52:59.999Z',
    NOW_MS,
    300
  ), '空闲')
})

test('invalid heartbeat timestamp is 离线', () => {
  assert.equal(deriveInspectionMonitorStatus('not-a-timestamp', undefined, NOW_MS, 300), '离线')
})

test('online computer with an invalid probe timestamp is 空闲', () => {
  assert.equal(deriveInspectionMonitorStatus('2026-10-29T07:59:00.000Z', 'not-a-timestamp', NOW_MS, 300), '空闲')
})

test('device priority keeps the default order within each status', () => {
  const devices = [
    { id: 'offline-1', status: 'IN_USE', monitorStatus: '离线' },
    { id: 'online-1', status: 'IN_USE', monitorStatus: '空闲' },
    { id: 'testing-1', status: 'IN_USE', monitorStatus: '测试中' },
    { id: 'online-2', status: 'IN_USE', monitorStatus: '空闲' },
    { id: 'testing-2', status: 'IN_USE', monitorStatus: '测试中' },
    { id: 'offline-2', status: 'IN_USE', monitorStatus: '离线' },
    { id: 'missing', status: 'IN_USE', monitorStatus: '未接入' }
  ] as const

  assert.deepEqual(sortInspectionMonitorDevices(devices, (device) => device.monitorStatus).map((device) => device.id),
    ['testing-1', 'testing-2', 'online-1', 'online-2', 'missing', 'offline-1', 'offline-2'])
  assert.equal(devices[0].id, 'offline-1')
})

test('in-use equipment appears on the first page ahead of maintenance equipment with a newer heartbeat', () => {
  const devices = [
    { id: 'maintenance', status: 'MAINTENANCE', monitorStatus: '离线' },
    { id: 'in-use', status: 'IN_USE', monitorStatus: '未接入' }
  ] as const

  const sorted = sortInspectionMonitorDevices(devices, (device) => device.monitorStatus)
  assert.deepEqual(paginateRows(sorted, 1, 1).map((device) => device.id), ['in-use'])
})

test('in-use equipment without a heartbeat appears on the first page ahead of offline equipment', () => {
  const devices = Array.from({ length: 10 }, (_, index) => ({
    id: `offline-${index}`,
    status: 'IN_USE',
    monitorStatus: '离线'
  })).concat([{ id: 'in-use', status: 'IN_USE', monitorStatus: '未接入' }])

  const sorted = sortInspectionMonitorDevices(devices, (device) => device.monitorStatus as '离线' | '未接入')
  assert.equal(paginateRows(sorted, 1, 10)[0].id, 'in-use')
})
