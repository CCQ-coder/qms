package com.pcbqms.equipment.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One latest/maximum snapshot for a device within a minute or hour bucket. */
@Data
@NoArgsConstructor
public class EquipmentTelemetrySummary {
    private String id;
    private String tenantCode;
    private String equipmentId;
    private LocalDateTime bucketStart;
    private LocalDateTime firstDeviceTime;
    private LocalDateTime lastDeviceTime;
    private LocalDateTime lastReceivedAt;
    private Integer sampleCount;
    private String lastEventType;
    private String lastEquipmentStatus;
    private String lastProgramNo;
    private String lastOperatorName;
    private BigDecimal maxProgressPercent;
    private String lastSpindleStatus;
    private String lastAlarmCode;
    private String lastAlarmMessage;
    private Integer lastMachiningCount;
    private Integer maxMachiningCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
