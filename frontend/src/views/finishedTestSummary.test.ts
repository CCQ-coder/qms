import assert from 'node:assert/strict'
import test from 'node:test'
import { groupFinishedTestSummary } from './finishedTestSummary.ts'

const rows = [
  { inspectionDate: '2026-11-10', equipmentCode: 'WTD-1', workOrderNo: 'WO-1', internalProductNo: 'IN-1', pcbMaterialNo: 'PCB-1', totalCount: 3, goodCount: 2, badCount: 1, passRate: 66.67 },
  { inspectionDate: '2026-11-09', equipmentCode: 'WTD-1', workOrderNo: 'WO-1', internalProductNo: 'IN-1', pcbMaterialNo: 'PCB-1', totalCount: 2, goodCount: 2, badCount: 0, passRate: 100 },
  { inspectionDate: '2026-11-10', equipmentCode: 'WTD-2', workOrderNo: 'WO-2', internalProductNo: 'IN-1', pcbMaterialNo: 'PCB-2', totalCount: 5, goodCount: 3, badCount: 2, passRate: 60 },
  { inspectionDate: '2026-11-10', equipmentCode: 'WTD-1', workOrderNo: 'WO-1', internalProductNo: 'IN-1', pcbMaterialNo: 'PCB-3', totalCount: 1, goodCount: 1, badCount: 0, passRate: 100 }
]

test('groups device counts by production order, internal number or date plus device plus order', () => {
  assert.deepEqual(groupFinishedTestSummary(rows, 'workOrderNo').map(({ key, pcbMaterialNo, totalCount, passRate }) => ({ key, pcbMaterialNo, totalCount, passRate })), [
    { key: 'WO-2', pcbMaterialNo: 'PCB-2', totalCount: 5, passRate: 60 },
    { key: 'WO-1', pcbMaterialNo: 'PCB-1、PCB-3', totalCount: 6, passRate: 83.33 }
  ])
  assert.deepEqual(groupFinishedTestSummary(rows, 'internalProductNo').map(({ key, totalCount, goodCount, badCount }) => ({ key, totalCount, goodCount, badCount })), [
    { key: 'IN-1', totalCount: 11, goodCount: 8, badCount: 3 }
  ])
  assert.deepEqual(groupFinishedTestSummary(rows, 'dateEquipmentOrder').map(({ inspectionDate, equipmentCode, workOrderNo, totalCount }) => ({ inspectionDate, equipmentCode, workOrderNo, totalCount })), [
    { inspectionDate: '2026-11-10', equipmentCode: 'WTD-2', workOrderNo: 'WO-2', totalCount: 5 },
    { inspectionDate: '2026-11-10', equipmentCode: 'WTD-1', workOrderNo: 'WO-1', totalCount: 4 },
    { inspectionDate: '2026-11-09', equipmentCode: 'WTD-1', workOrderNo: 'WO-1', totalCount: 2 }
  ])
})
