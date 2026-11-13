import type { Nonconformity } from '../types/qms'

export function reviewCandidates(selected: Nonconformity[]) {
  return selected.filter(ncr => ['OPEN', 'PENDING_REVIEW'].includes(ncr.status)).map(ncr => ({
    id: ncr.id,
    ncrNo: ncr.ncrNo,
    badCount: ncr.details.reduce((sum, line) => sum + line.scrapQuantity, 0)
  }))
}
