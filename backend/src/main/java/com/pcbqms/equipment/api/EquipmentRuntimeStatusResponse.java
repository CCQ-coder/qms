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
    LocalDateTime lastReceivedAt
) {
    public static EquipmentRuntimeStatusResponse from(Equipment equipment, EquipmentRuntimeStatus status,
                                                       boolean online) {
        return new EquipmentRuntimeStatusResponse(equipment.getId(), equipment.getAssetNo(), equipment.getName(),
            equipment.getProcessCode(), status.getReportedStatus(), online ? status.getReportedStatus() : "OFFLINE", online,
            status.getProgramNo(), status.getProgramVersion(), status.getMaterialNo(), status.getWorkOrderNo(),
            status.getBatchNo(), status.getOperatorName(), status.getProgressPercent(), status.getSpindleStatus(),
            status.getAlarmCode(), status.getAlarmMessage(), status.getMachiningCount(), status.getLastDeviceTime(),
            status.getLastReceivedAt());
    }
}
