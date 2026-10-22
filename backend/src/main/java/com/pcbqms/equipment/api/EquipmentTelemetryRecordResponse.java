package com.pcbqms.equipment.api;

import com.pcbqms.equipment.domain.Equipment;
import com.pcbqms.equipment.domain.EquipmentTelemetryRecord;
import com.pcbqms.equipment.domain.EquipmentTelemetryProjectionStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A permission-filtered telemetry record suitable for the equipment monitoring screen. */
public record EquipmentTelemetryRecordResponse(
    String id,
    String equipmentId,
    String equipmentCode,
    String equipmentName,
    String sourceUsername,
    String machineName,
    LocalDateTime deviceTime,
    LocalDateTime receivedAt,
    String eventType,
    String status,
    String programNo,
    String operatorName,
    BigDecimal progressPercent,
    String spindleStatus,
    String alarmCode,
    String alarmMessage,
    Integer machiningCount,
    String legacyState,
    String legacyId,
    String legacyMachineType,
    BigDecimal runtimeSeconds,
    BigDecimal routeLength,
    String screenText,
    String sourceRecordNo,
    String rawPayload,
    String materialNo,
    String workOrderNo,
    String batchNo,
    Integer goodBoardCount,
    Integer badBoardCount,
    String fqcProjectionStatus,
    String fqcProjectionReason,
    String fqcInspectionRecordId,
    Integer fqcDeltaTested
) {
    public static EquipmentTelemetryRecordResponse from(
        EquipmentTelemetryRecord record,
        Equipment equipment,
        String legacyState,
        String legacyId,
        String legacyMachineType,
        BigDecimal runtimeSeconds,
        BigDecimal routeLength,
        String screenText,
        Integer goodBoardCount,
        Integer badBoardCount,
        EquipmentTelemetryProjectionStatus projection
    ) {
        return new EquipmentTelemetryRecordResponse(
            record.getId(), record.getEquipmentId(), equipment.getAssetNo(), equipment.getName(), record.getSourceUsername(),
            record.getMachineName(), record.getDeviceTime(), record.getReceivedAt(), record.getEventType(), record.getEquipmentStatus(), record.getProgramNo(),
            record.getOperatorName(), record.getProgressPercent(), record.getSpindleStatus(), record.getAlarmCode(),
            record.getAlarmMessage(), record.getMachiningCount()
            , legacyState, legacyId, legacyMachineType, runtimeSeconds, routeLength, screenText,
            record.getSourceRecordNo(), record.getRawPayload(),
            record.getMaterialNo(), record.getWorkOrderNo(), record.getBatchNo(), goodBoardCount, badBoardCount,
            projection == null ? null : projection.getStatus(),
            projection == null ? null : projection.getReason(),
            projection == null ? null : projection.getInspectionRecordId(),
            projection == null ? null : projection.getDeltaTested()
        );
    }
}
