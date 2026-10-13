package com.pcbqms.equipment.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Immutable, normalized message received from a field gateway. */
@Data
@NoArgsConstructor
public class EquipmentTelemetryRecord {
    private String id;
    private String tenantCode;
    private String equipmentId;
    private String sourceUsername;
    private String machineName;
    private LocalDateTime deviceTime;
    private LocalDateTime receivedAt;
    private String eventType;
    private String equipmentStatus;
    private String programNo;
    private String programVersion;
    private String materialNo;
    private String workOrderNo;
    private String batchNo;
    private String operatorName;
    private BigDecimal progressPercent;
    private String spindleStatus;
    private String alarmCode;
    private String alarmMessage;
    private Integer machiningCount;
    private String sourceRecordNo;
    private String deduplicationKey;
    private String rawPayload;
    private LocalDateTime createdAt;
}
