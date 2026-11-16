package com.pcbqms.equipment.api;

import java.util.List;

/** Cursor pages retain nextCursor; numbered pages include the matching total. */
public record EquipmentTelemetryHistoryPageResponse(
    List<EquipmentTelemetryRecordResponse> items,
    String nextCursor,
    int pageSize,
    Long total
) {
    public EquipmentTelemetryHistoryPageResponse(List<EquipmentTelemetryRecordResponse> items,
                                                 String nextCursor, int pageSize) {
        this(items, nextCursor, pageSize, null);
    }
}
