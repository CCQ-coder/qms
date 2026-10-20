/** Flying-probe presence is authoritative even when an old telemetry row has a legacy state. */
export function chooseRuntimeLabel(
  statusSource: string | undefined,
  effectiveLabel: string,
  legacyLabel: string | undefined
): string {
  return statusSource === 'FLYING_PROBE' ? effectiveLabel : (legacyLabel || effectiveLabel)
}
