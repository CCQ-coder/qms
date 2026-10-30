import assert from 'node:assert/strict'
import test from 'node:test'
import { deriveInspectionMonitorStatus } from './inspectionMonitorStatus.ts'

const NOW_MS = Date.parse('2026-10-29T08:00:00.000Z')

test('missing computer heartbeat remains 未接入', () => {
  assert.equal(deriveInspectionMonitorStatus(undefined, '2026-10-29T07:59:00.000Z', NOW_MS), '未接入')
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
