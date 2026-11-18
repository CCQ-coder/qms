import type { FinishedTestDeviceSummary } from '@/types/qms'

export type SummaryDimension = 'workOrderNo' | 'internalProductNo' | 'dateEquipmentOrder'

export function groupFinishedTestSummary(rows: FinishedTestDeviceSummary[], dimension: SummaryDimension) {
  const groups = new Map<string, {
    key: string; inspectionDate: string; equipmentCode: string; workOrderNo: string;
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
