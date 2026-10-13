package com.pcbqms.equipment.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/** Latest report from one source/program slot of a physical machine. */
@Data
@TableName("qms_equipment_runtime_channel")
public class EquipmentRuntimeChannel {
    @TableId
    private String id;
    private String tenantCode;
    private String equipmentId;
    private String sourceName;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String reportedStatus;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String programNo;
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
    private LocalDateTime lastDeviceTime;
    private LocalDateTime lastReceivedAt;
    private LocalDateTime updatedAt;
}
