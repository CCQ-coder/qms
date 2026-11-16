package com.pcbqms.equipment.api;

import java.util.List;

/** History page with an optional cursor or total count for numbered pagination. */
public record EquipmentTelemetryHistoryPageResponse(
    List<EquipmentTelemetryRecordResponse> items,
    String nextCursor,
    int pageSize,
    Long total
) {
    public EquipmentTelemetryHistoryPageResponse(List<EquipmentTelemetryRecordResponse> items, String nextCursor, int pageSize) {
        this(items, nextCursor, pageSize, null);
    }
}
