export function clampPage(page: number, total: number, pageSize: number) {
  return Math.min(page, Math.max(1, Math.ceil(total / pageSize)))
}

export function paginateRows<T>(rows: T[], page: number, pageSize: number) {
  return rows.slice((page - 1) * pageSize, page * pageSize)
}

export function shouldPrefetchNextPage(page: number, loadedCount: number, pageSize: number, hasMore: boolean) {
  return hasMore && loadedCount > 0 && page >= Math.max(1, Math.ceil(loadedCount / pageSize) - 2)
}
