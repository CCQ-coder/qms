import type { FinishedTestDeviceSummary } from '@/types/qms'

export type SummaryDimension = 'workOrderNo' | 'internalProductNo' | 'inspectionDate'

export function groupFinishedTestSummary(rows: FinishedTestDeviceSummary[], dimension: SummaryDimension) {
  const groups = new Map<string, { key: string; totalCount: number; goodCount: number; badCount: number; passRate: number }>()
  for (const row of rows) {
    const key = row[dimension] || (dimension === 'internalProductNo' ? '未填写厂内编号' : '—')
    const group = groups.get(key) ?? { key, totalCount: 0, goodCount: 0, badCount: 0, passRate: 0 }
    group.totalCount += row.totalCount
    group.goodCount += row.goodCount
    group.badCount += row.badCount
    groups.set(key, group)
  }
  return [...groups.values()]
    .map((group) => ({ ...group, passRate: group.totalCount ? Math.round(group.goodCount * 10000 / group.totalCount) / 100 : 0 }))
    .sort((a, b) => b.key.localeCompare(a.key, 'zh-CN', { numeric: true }))
}
