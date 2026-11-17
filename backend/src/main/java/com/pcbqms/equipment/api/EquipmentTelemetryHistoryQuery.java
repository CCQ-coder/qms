package com.pcbqms.equipment.api;

import java.time.LocalDateTime;

/** Cursor or numbered-page query for telemetry history; Zhongbo test reports are retained. */
public record EquipmentTelemetryHistoryQuery(
    String equipmentId,
    LocalDateTime fromTime,
    LocalDateTime toTime,
    String cursor,
    Integer limit,
    String eventType,
    String workOrderNo,
    String customerOrderNo,
    String internalProductNo,
    String factoryInternalNo,
    Boolean unmatchedOnly,
    Integer page,
    Boolean includeTotal,
    String keyword,
    String sortOrder
) {
    public EquipmentTelemetryHistoryQuery(String equipmentId, LocalDateTime fromTime, LocalDateTime toTime,
                                          String cursor, Integer limit, String eventType, String workOrderNo,
                                          String customerOrderNo, String internalProductNo, String factoryInternalNo,
                                          Boolean unmatchedOnly, Integer page, Boolean includeTotal, String keyword) {
        this(equipmentId, fromTime, toTime, cursor, limit, eventType, workOrderNo, customerOrderNo,
            internalProductNo, factoryInternalNo, unmatchedOnly, page, includeTotal, keyword, null);
    }
    public EquipmentTelemetryHistoryQuery(String equipmentId, LocalDateTime fromTime, LocalDateTime toTime,
                                          String cursor, Integer limit, String eventType, String workOrderNo,
                                          String customerOrderNo, String internalProductNo, String factoryInternalNo,
                                          Boolean unmatchedOnly) {
        this(equipmentId, fromTime, toTime, cursor, limit, eventType, workOrderNo, customerOrderNo,
            internalProductNo, factoryInternalNo, unmatchedOnly, null, null, null, null);
    }

    public EquipmentTelemetryHistoryQuery(String equipmentId, LocalDateTime fromTime, LocalDateTime toTime,
                                          String cursor, Integer limit, String eventType, String workOrderNo,
                                          String customerOrderNo, String internalProductNo, String factoryInternalNo,
                                          Boolean unmatchedOnly, Integer page, Boolean includeTotal) {
        this(equipmentId, fromTime, toTime, cursor, limit, eventType, workOrderNo, customerOrderNo,
            internalProductNo, factoryInternalNo, unmatchedOnly, page, includeTotal, null, null);
    }

    public int resolvedLimit() {
        int requested = limit == null ? 100 : limit;
        return Math.min(Math.max(requested, 1), 200);
    }
}
