export function moveColumn<T>(columns: readonly T[], from: number, to: number): T[] {
  const reordered = [...columns]
  reordered.splice(to, 0, ...reordered.splice(from, 1))
  return reordered
}
