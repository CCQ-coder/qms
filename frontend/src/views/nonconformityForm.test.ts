import assert from 'node:assert/strict'
import test from 'node:test'
import { finishedTestProcess, responsibleDepartmentForProcess } from './nonconformityForm.ts'
import type { ProcessMaster } from '../types/qms.ts'

function process(processCode: string, responsibleDepartment?: string): ProcessMaster {
  return { id: processCode, processCode, processName: processCode, sequenceNo: 1, status: 'ACTIVE', responsibleDepartment }
}

test('uses the department configured for the selected process', () => {
  assert.equal(responsibleDepartmentForProcess(process('FQC', '品质'), 'FQC'), '品质')
  assert.equal(responsibleDepartmentForProcess(process('DRILL', '钻孔'), 'DRILL'), '钻孔')
})

test('keeps the existing FQC default and clears an unmapped other process', () => {
  assert.equal(responsibleDepartmentForProcess(process('FQC'), 'FQC'), '测试')
  assert.equal(responsibleDepartmentForProcess(process('DRILL'), 'DRILL'), '')
})

test('uses the configured FQC process even when its code is numeric', () => {
  const configured = { ...process('001'), processName: 'FQC' }
  assert.equal(finishedTestProcess([configured])?.processCode, '001')
  assert.equal(responsibleDepartmentForProcess(configured, '001'), '测试')
})
