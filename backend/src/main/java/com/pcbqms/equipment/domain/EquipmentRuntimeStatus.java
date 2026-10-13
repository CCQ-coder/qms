package com.pcbqms.equipment.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
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
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String reportedStatus;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String programNo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String programVersion;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String materialNo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String workOrderNo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String batchNo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String operatorName;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal progressPercent;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String spindleStatus;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String alarmCode;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String alarmMessage;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer machiningCount;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String lastSourceRecordNo;
    private LocalDateTime lastDeviceTime;
    private LocalDateTime lastReceivedAt;
    private LocalDateTime updatedAt;
}
