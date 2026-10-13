package com.pcbqms.equipment.api;

import java.time.LocalDateTime;

/** Cursor-based query for the short-lived, second-level telemetry stream. */
public record EquipmentTelemetryHistoryQuery(
    String equipmentId,
    LocalDateTime fromTime,
    LocalDateTime toTime,
    String cursor,
    Integer limit
) {
    public int resolvedLimit() {
        int requested = limit == null ? 100 : limit;
        return Math.min(Math.max(requested, 1), 200);
    }
}
