import assert from 'node:assert/strict'
import test from 'node:test'
import { factoryNo, materialNo } from './nonconformityDetailDisplay.ts'
import type { Nonconformity, NonconformityDetail } from '../types/qms.ts'

test('imported scrap shows the linked full factory number and five-digit material number', () => {
  const ncr = { sourceType: 'EXCEL' } as Nonconformity
  const detail = {
    internalProductNo: '43605', workOrderInternalProductNo: '02PT43605A01', materialNo: 'BC-SM00284', layerCount: 2
  } as NonconformityDetail
  assert.equal(factoryNo(ncr, detail), '02PT43605A01')
  assert.equal(materialNo(ncr, detail), '43605')
})

test('regular scrap keeps its own factory and material numbers', () => {
  const ncr = { sourceType: 'MANUAL' } as Nonconformity
  const detail = { internalProductNo: '02PT43605A01', materialNo: 'BC-SM00284' } as NonconformityDetail
  assert.equal(factoryNo(ncr, detail), '02PT43605A01')
  assert.equal(materialNo(ncr, detail), 'BC-SM00284')
})
