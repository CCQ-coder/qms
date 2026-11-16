import type { FinishedTestDeviceSummary } from '@/types/qms'

export type SummaryDimension = 'workOrderNo' | 'internalProductNo' | 'dateEquipmentOrder'

export function groupFinishedTestSummary(rows: FinishedTestDeviceSummary[], dimension: SummaryDimension) {
  const groups = new Map<string, {
    key: string; inspectionDate: string; equipmentCode: string; workOrderNo: string; customerOrderNo: string;
    internalProductNo: string; materials: Set<string>; totalCount: number; goodCount: number; badCount: number
  }>()
  for (const row of rows) {
    const key = dimension === 'workOrderNo' ? row.workOrderNo
      : dimension === 'internalProductNo' ? (row.internalProductNo || '未填写厂内编号')
        : [row.inspectionDate, row.equipmentCode, row.workOrderNo].join('\u0000')
    const group = groups.get(key) ?? {
      key, inspectionDate: dimension === 'dateEquipmentOrder' ? row.inspectionDate : '',
      equipmentCode: dimension === 'dateEquipmentOrder' ? row.equipmentCode : '',
      workOrderNo: dimension === 'internalProductNo' ? '' : row.workOrderNo,
      customerOrderNo: dimension === 'internalProductNo' ? '' : (row.customerOrderNo || ''),
      internalProductNo: row.internalProductNo || '未填写厂内编号',
      materials: new Set<string>(), totalCount: 0, goodCount: 0, badCount: 0
    }
    if (row.pcbMaterialNo) group.materials.add(row.pcbMaterialNo)
    group.totalCount += row.totalCount
    group.goodCount += row.goodCount
    group.badCount += row.badCount
    groups.set(key, group)
  }
  return [...groups.values()]
    .map(({ materials, ...group }) => ({ ...group, pcbMaterialNo: [...materials].join('、'),
      passRate: group.totalCount ? Math.round(group.goodCount * 10000 / group.totalCount) / 100 : 0 }))
    .sort((a, b) => b.key.localeCompare(a.key, 'zh-CN', { numeric: true }))
}

export function summarizeFinishedTestDevices(rows: FinishedTestDeviceSummary[], from?: string, to?: string) {
  const totals = { totalCount: 0, goodCount: 0, badCount: 0 }
  const byDate = new Map<string, typeof totals>()
  for (const row of rows) {
    if ((from && row.inspectionDate < from) || (to && row.inspectionDate > to)) continue
    const day = byDate.get(row.inspectionDate) ?? { totalCount: 0, goodCount: 0, badCount: 0 }
    for (const count of ['totalCount', 'goodCount', 'badCount'] as const) {
      totals[count] += row[count]
      day[count] += row[count]
    }
    byDate.set(row.inspectionDate, day)
  }
  const passRate = (counts: typeof totals) => counts.totalCount
    ? Math.round(counts.goodCount * 10000 / counts.totalCount) / 100 : 0
  return { ...totals, passRate: passRate(totals), dailyTrend: [...byDate.entries()]
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([inspectionDate, counts]) => ({ inspectionDate, ...counts, passRate: passRate(counts) })) }
}
