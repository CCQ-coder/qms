package com.pcbqms.equipment.api;

import com.pcbqms.equipment.domain.EquipmentRuntimeChannel;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A program slot reported by one source of a physical machine. */
public record EquipmentRuntimeProgramResponse(
    String sourceName,
    String status,
    boolean online,
    String programNo,
    String operatorName,
    BigDecimal progressPercent,
    String spindleStatus,
    String alarmCode,
    String alarmMessage,
    Integer machiningCount,
    LocalDateTime lastDeviceTime,
    LocalDateTime lastReceivedAt
) {
    public static EquipmentRuntimeProgramResponse from(EquipmentRuntimeChannel channel, boolean online) {
        return new EquipmentRuntimeProgramResponse(channel.getSourceName(),
            online ? channel.getReportedStatus() : "OFFLINE", online, channel.getProgramNo(),
            channel.getOperatorName(), channel.getProgressPercent(), channel.getSpindleStatus(),
            channel.getAlarmCode(), channel.getAlarmMessage(), channel.getMachiningCount(),
            channel.getLastDeviceTime(), channel.getLastReceivedAt());
    }
}
