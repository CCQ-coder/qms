import assert from 'node:assert/strict'
import test from 'node:test'
import { reviewCandidates } from './nonconformityBatchReview.ts'
import type { Nonconformity } from '../types/qms.ts'

test('batch review includes only unreviewed orders with their current detail totals', () => {
  const selected = [
    { id: 'a', ncrNo: 'NCR-1', status: 'OPEN', details: [{ scrapQuantity: 2 }, { scrapQuantity: 3 }] },
    { id: 'b', ncrNo: 'NCR-2', status: 'APPROVED', details: [{ scrapQuantity: 4 }] },
    { id: 'c', ncrNo: 'NCR-3', status: 'PENDING_REVIEW', details: [{ scrapQuantity: 1 }] }
  ] as Nonconformity[]
  assert.deepEqual(reviewCandidates(selected), [
    { id: 'a', ncrNo: 'NCR-1', badCount: 5 },
    { id: 'c', ncrNo: 'NCR-3', badCount: 1 }
  ])
})
