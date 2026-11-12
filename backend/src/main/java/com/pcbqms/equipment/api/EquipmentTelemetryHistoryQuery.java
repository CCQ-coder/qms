package com.pcbqms.equipment.api;

import java.time.LocalDateTime;

/** Cursor-based query for telemetry history; Zhongbo test reports are retained. */
public record EquipmentTelemetryHistoryQuery(
    String equipmentId,
    LocalDateTime fromTime,
    LocalDateTime toTime,
    String cursor,
    Integer limit,
    String eventType,
    String workOrderNo,
    String internalProductNo,
    String factoryInternalNo,
    Boolean unmatchedOnly,
    Boolean workOrderEmptyOnly
) {
    public int resolvedLimit() {
        int requested = limit == null ? 100 : limit;
        return Math.min(Math.max(requested, 1), 200);
    }
}
