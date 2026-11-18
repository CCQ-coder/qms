import assert from 'node:assert/strict'
import test from 'node:test'
import { moveColumn } from './nonconformityColumns.ts'

test('moves a dragged table column while preserving other columns', () => {
  assert.deepEqual(moveColumn(['单据编号', '检测日期', '责任部门'], 2, 0),
    ['责任部门', '单据编号', '检测日期'])
})
