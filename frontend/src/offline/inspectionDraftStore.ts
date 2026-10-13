const DATABASE_NAME = 'pcb-qms-offline'
const STORE_NAME = 'inspection-drafts'
const DATABASE_VERSION = 1

export type OfflineInspectionPayload = {
  verdict: string
  defectiveQuantity: number
  defectSummary?: string
  defectLocation?: string
  results: Record<string, unknown>
  equipmentAssetNo?: string
  clientRequestId: string
}

export type OfflineInspectionPhoto = {
  name: string
  type: string
  blob: Blob
}

export type OfflineInspectionDraft = {
  id: string
  tenantCode: string
  actor: string
  taskId: string
  taskNo: string
  processName: string
  payload: OfflineInspectionPayload
  photos: OfflineInspectionPhoto[]
  createdAt: string
}

let databasePromise: Promise<IDBDatabase> | undefined

function database() {
  if (!databasePromise) {
    databasePromise = new Promise((resolve, reject) => {
      const request = indexedDB.open(DATABASE_NAME, DATABASE_VERSION)
      request.onupgradeneeded = () => {
        if (!request.result.objectStoreNames.contains(STORE_NAME)) {
          request.result.createObjectStore(STORE_NAME, { keyPath: 'id' })
        }
      }
      request.onsuccess = () => resolve(request.result)
      request.onerror = () => reject(request.error ?? new Error('无法打开本地暂存库'))
    })
  }
  return databasePromise
}

function transactionCompleted(transaction: IDBTransaction) {
  return new Promise<void>((resolve, reject) => {
    transaction.oncomplete = () => resolve()
    transaction.onerror = () => reject(transaction.error ?? new Error('本地暂存操作失败'))
    transaction.onabort = () => reject(transaction.error ?? new Error('本地暂存操作被中止'))
  })
}

export async function saveInspectionDraft(draft: OfflineInspectionDraft) {
  const db = await database()
  const transaction = db.transaction(STORE_NAME, 'readwrite')
  transaction.objectStore(STORE_NAME).put(draft)
  await transactionCompleted(transaction)
}

export async function listInspectionDrafts(tenantCode: string, actor: string) {
  const db = await database()
  const transaction = db.transaction(STORE_NAME, 'readonly')
  const request = transaction.objectStore(STORE_NAME).getAll()
  const drafts = await new Promise<OfflineInspectionDraft[]>((resolve, reject) => {
    request.onsuccess = () => resolve(request.result as OfflineInspectionDraft[])
    request.onerror = () => reject(request.error ?? new Error('本地暂存读取失败'))
  })
  await transactionCompleted(transaction)
  return drafts
    .filter((draft) => draft.tenantCode === tenantCode && draft.actor === actor)
    .sort((left, right) => left.createdAt.localeCompare(right.createdAt))
}

export async function removeInspectionDraft(id: string) {
  const db = await database()
  const transaction = db.transaction(STORE_NAME, 'readwrite')
  transaction.objectStore(STORE_NAME).delete(id)
  await transactionCompleted(transaction)
}

export function createClientRequestId() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `${Date.now()}-${Math.random().toString(36).slice(2)}`
}
