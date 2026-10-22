export function formatHostCapacity(availableBytes: number, totalBytes: number) {
  const gib = 1024 ** 3
  return `${(availableBytes / gib).toFixed(1)} / ${(totalBytes / gib).toFixed(1)} GiB`
}

export function formatHostUptime(seconds: number) {
  const days = Math.floor(seconds / 86_400)
  const hours = Math.floor(seconds % 86_400 / 3_600)
  if (days === 0 && hours === 0) return `${Math.floor(seconds / 60)} 分钟`
  return `${days} 天 ${hours} 小时`
}

export function isHostOnline(receivedAt: string, now: number, offlineAfterSeconds: number) {
  const received = Date.parse(receivedAt)
  return Number.isFinite(received) && now - received <= offlineAfterSeconds * 1000
}
