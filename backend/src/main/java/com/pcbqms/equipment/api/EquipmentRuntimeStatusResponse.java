package com.pcbqms.equipment.api;

import com.pcbqms.equipment.domain.Equipment;
import com.pcbqms.equipment.domain.EquipmentRuntimeStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record EquipmentRuntimeStatusResponse(
    String equipmentId,
    String equipmentCode,
    String equipmentName,
    String processCode,
    String reportedStatus,
    String effectiveStatus,
    boolean online,
    String programNo,
    String programVersion,
    String materialNo,
    String workOrderNo,
    String batchNo,
    String operatorName,
    BigDecimal progressPercent,
    String spindleStatus,
    String alarmCode,
    String alarmMessage,
    Integer machiningCount,
    LocalDateTime lastDeviceTime,
    LocalDateTime lastReceivedAt,
    String statusSource,
    LocalDateTime lastProbeAt,
    LocalDateTime lastTxtChangeAt
) {
    public static EquipmentRuntimeStatusResponse from(Equipment equipment, EquipmentRuntimeStatus status,
                                                       boolean online) {
        return new EquipmentRuntimeStatusResponse(equipment.getId(), equipment.getAssetNo(), equipment.getName(),
            equipment.getProcessCode(), status.getReportedStatus(), online ? status.getReportedStatus() : "OFFLINE", online,
            status.getProgramNo(), status.getProgramVersion(), status.getMaterialNo(), status.getWorkOrderNo(),
            status.getBatchNo(), status.getOperatorName(), status.getProgressPercent(), status.getSpindleStatus(),
            status.getAlarmCode(), status.getAlarmMessage(), status.getMachiningCount(), status.getLastDeviceTime(),
            status.getLastReceivedAt(), "TELEMETRY", null, null);
    }

    public static EquipmentRuntimeStatusResponse fromPresence(Equipment equipment, EquipmentRuntimeStatus status,
        String reportedStatus, String effectiveStatus, boolean online, LocalDateTime lastProbeAt,
        LocalDateTime lastTxtChangeAt) {
        return new EquipmentRuntimeStatusResponse(equipment.getId(), equipment.getAssetNo(), equipment.getName(),
            equipment.getProcessCode(), reportedStatus, effectiveStatus, online,
            status == null ? null : status.getProgramNo(), status == null ? null : status.getProgramVersion(),
            status == null ? null : status.getMaterialNo(), status == null ? null : status.getWorkOrderNo(),
            status == null ? null : status.getBatchNo(), status == null ? null : status.getOperatorName(),
            status == null ? null : status.getProgressPercent(), status == null ? null : status.getSpindleStatus(),
            status == null ? null : status.getAlarmCode(), status == null ? null : status.getAlarmMessage(),
            status == null ? null : status.getMachiningCount(), status == null ? null : status.getLastDeviceTime(),
            status == null ? null : status.getLastReceivedAt(), "FLYING_PROBE", lastProbeAt, lastTxtChangeAt);
    }
}
