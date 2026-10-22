import assert from 'node:assert/strict'
import test from 'node:test'
import { clampPage, paginateRows } from './pagination.ts'

test('shows the requested page and clamps it when the list shrinks', () => {
  const rows = Array.from({ length: 25 }, (_, index) => index + 1)
  assert.deepEqual(paginateRows(rows, 3, 10), [21, 22, 23, 24, 25])
  assert.deepEqual(paginateRows(rows, 2, 20), [21, 22, 23, 24, 25])
  assert.equal(clampPage(3, 15, 10), 2)
  assert.deepEqual(paginateRows(rows.slice(0, 15), clampPage(3, 15, 10), 10), [11, 12, 13, 14, 15])
  assert.equal(clampPage(3, 0, 10), 1)
  assert.deepEqual(paginateRows([], 1, 10), [])
})
