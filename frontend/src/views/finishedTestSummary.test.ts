import assert from 'node:assert/strict'
import test from 'node:test'
import { groupFinishedTestSummary } from './finishedTestSummary.ts'

const rows = [
  { inspectionDate: '2026-11-10', workOrderNo: 'WO-1', internalProductNo: 'IN-1', pcbMaterialNo: 'PCB-1', totalCount: 3, goodCount: 2, badCount: 1, passRate: 66.67 },
  { inspectionDate: '2026-11-09', workOrderNo: 'WO-1', internalProductNo: 'IN-1', pcbMaterialNo: 'PCB-1', totalCount: 2, goodCount: 2, badCount: 0, passRate: 100 },
  { inspectionDate: '2026-11-10', workOrderNo: 'WO-2', internalProductNo: 'IN-1', pcbMaterialNo: 'PCB-2', totalCount: 5, goodCount: 3, badCount: 2, passRate: 60 }
]

test('groups the same device counts by production order, internal number or date', () => {
  assert.deepEqual(groupFinishedTestSummary(rows, 'workOrderNo').map(({ key, totalCount, passRate }) => ({ key, totalCount, passRate })), [
    { key: 'WO-2', totalCount: 5, passRate: 60 },
    { key: 'WO-1', totalCount: 5, passRate: 80 }
  ])
  assert.deepEqual(groupFinishedTestSummary(rows, 'internalProductNo').map(({ key, totalCount, goodCount, badCount }) => ({ key, totalCount, goodCount, badCount })), [
    { key: 'IN-1', totalCount: 10, goodCount: 7, badCount: 3 }
  ])
  assert.deepEqual(groupFinishedTestSummary(rows, 'inspectionDate').map(({ key, totalCount, passRate }) => ({ key, totalCount, passRate })), [
    { key: '2026-11-10', totalCount: 8, passRate: 62.5 },
    { key: '2026-11-09', totalCount: 2, passRate: 100 }
  ])
})
