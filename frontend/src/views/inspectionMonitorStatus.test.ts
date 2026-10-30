import assert from 'node:assert/strict'
import test from 'node:test'
import { deriveInspectionMonitorStatus } from './inspectionMonitorStatus.ts'

const NOW_MS = Date.parse('2026-10-29T08:00:00.000Z')

test('missing runtime remains 未接入', () => {
  assert.equal(deriveInspectionMonitorStatus(undefined, '2026-10-29T07:59:00.000Z', NOW_MS), '未接入')
})

test('offline runtime remains 离线 even with recent probe data', () => {
  assert.equal(deriveInspectionMonitorStatus(false, '2026-10-29T07:59:00.000Z', NOW_MS), '离线')
})

test('online runtime without probe data is 空闲', () => {
  assert.equal(deriveInspectionMonitorStatus(true, undefined, NOW_MS), '空闲')
})

test('online runtime with probe data inside seven minutes is 测试中', () => {
  assert.equal(deriveInspectionMonitorStatus(true, '2026-10-29T07:54:00.000Z', NOW_MS), '测试中')
})

test('online runtime with probe data exactly seven minutes old is 测试中', () => {
  assert.equal(deriveInspectionMonitorStatus(true, '2026-10-29T07:53:00.000Z', NOW_MS), '测试中')
})

test('online runtime with probe data one millisecond older than seven minutes is 空闲', () => {
  assert.equal(deriveInspectionMonitorStatus(true, '2026-10-29T07:52:59.999Z', NOW_MS), '空闲')
})

test('online runtime with an invalid probe timestamp is 空闲', () => {
  assert.equal(deriveInspectionMonitorStatus(true, 'not-a-timestamp', NOW_MS), '空闲')
})
