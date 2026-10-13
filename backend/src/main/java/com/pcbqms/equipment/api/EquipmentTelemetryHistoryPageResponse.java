package com.pcbqms.equipment.api;

import java.util.List;

/** A seek page: callers pass nextCursor to read older records without deep SQL offsets. */
public record EquipmentTelemetryHistoryPageResponse(
    List<EquipmentTelemetryRecordResponse> items,
    String nextCursor,
    int pageSize
) {
}
