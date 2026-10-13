package com.pcbqms.equipment.api;

import com.pcbqms.equipment.domain.Equipment;
import com.pcbqms.equipment.domain.EquipmentRuntimeStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

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
    List<EquipmentRuntimeProgramResponse> programs
) {
    public static EquipmentRuntimeStatusResponse from(Equipment equipment, EquipmentRuntimeStatus status,
                                                       boolean online, List<EquipmentRuntimeProgramResponse> programs) {
        List<EquipmentRuntimeProgramResponse> active = programs.stream().filter(EquipmentRuntimeProgramResponse::online).toList();
        if (!programs.isEmpty()) {
            EquipmentRuntimeProgramResponse single = active.size() == 1 ? active.getFirst() : null;
            EquipmentRuntimeProgramResponse alarm = active.stream()
                .filter(program -> "ALARM".equals(program.status())).findFirst().orElse(null);
            String aggregateStatus = active.stream().map(EquipmentRuntimeProgramResponse::status)
                .min(Comparator.comparingInt(EquipmentRuntimeStatusResponse::statusPriority))
                .orElse("OFFLINE");
            return new EquipmentRuntimeStatusResponse(equipment.getId(), equipment.getAssetNo(), equipment.getName(),
                equipment.getProcessCode(), status.getReportedStatus(), aggregateStatus, !active.isEmpty(),
                single == null ? null : single.programNo(), null, null, null, null,
                single == null ? null : single.operatorName(), single == null ? null : single.progressPercent(),
                single == null ? null : single.spindleStatus(), alarm == null ? null : alarm.alarmCode(),
                alarm == null ? null : alarm.alarmMessage(), single == null ? null : single.machiningCount(),
                status.getLastDeviceTime(), status.getLastReceivedAt(), programs);
        }
        return new EquipmentRuntimeStatusResponse(equipment.getId(), equipment.getAssetNo(), equipment.getName(),
            equipment.getProcessCode(), status.getReportedStatus(), online ? status.getReportedStatus() : "OFFLINE", online,
            status.getProgramNo(), status.getProgramVersion(), status.getMaterialNo(), status.getWorkOrderNo(),
            status.getBatchNo(), status.getOperatorName(), status.getProgressPercent(), status.getSpindleStatus(),
            status.getAlarmCode(), status.getAlarmMessage(), status.getMachiningCount(), status.getLastDeviceTime(),
            status.getLastReceivedAt(), programs);
    }

    private static int statusPriority(String value) {
        return switch (value == null ? "" : value) {
            case "ALARM" -> 0;
            case "PROCESSING" -> 1;
            case "STOPPED" -> 2;
            case "IDLE" -> 3;
            case "COMPLETED" -> 4;
            default -> 5;
        };
    }
}
