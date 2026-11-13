import type { Nonconformity, NonconformityDetail } from '../types/qms'

export const factoryNo = (ncr: Nonconformity | undefined, detail: NonconformityDetail) => ncr?.sourceType === 'EXCEL'
  ? detail.workOrderInternalProductNo : detail.internalProductNo

export const materialNo = (ncr: Nonconformity | undefined, detail: NonconformityDetail) => ncr?.sourceType === 'EXCEL'
  ? (/^\d{5}$/.test(detail.internalProductNo ?? '') ? detail.internalProductNo : detail.materialNo) : detail.materialNo
