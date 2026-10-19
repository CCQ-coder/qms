package com.pcbqms.equipment.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Latest accepted state; historical data is kept separately in telemetry. */
@Data
@NoArgsConstructor
@TableName("qms_equipment_runtime_status")
public class EquipmentRuntimeStatus {
    @TableId
    private String id;
    private String tenantCode;
    private String equipmentId;
    private String reportedStatus;
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
    private String lastSourceRecordNo;
    private LocalDateTime lastDeviceTime;
    private LocalDateTime lastReceivedAt;
    private LocalDateTime updatedAt;
}
