package com.pcbqms.equipment.api;

import java.time.LocalDateTime;

/** Cursor or numbered-page query for telemetry history. */
public record EquipmentTelemetryHistoryQuery(
    String equipmentId,
    LocalDateTime fromTime,
    LocalDateTime toTime,
    String cursor,
    Integer limit,
    String eventType,
    String workOrderNo,
    String internalProductNo,
    Integer page,
    Boolean includeTotal
) {
    public EquipmentTelemetryHistoryQuery(String equipmentId, LocalDateTime fromTime, LocalDateTime toTime,
                                          String cursor, Integer limit, String eventType, String workOrderNo,
                                          String internalProductNo) {
        this(equipmentId, fromTime, toTime, cursor, limit, eventType, workOrderNo, internalProductNo, null, null);
    }
    public int resolvedLimit() {
        int requested = limit == null ? 100 : limit;
        return Math.min(Math.max(requested, 1), 200);
    }
}
