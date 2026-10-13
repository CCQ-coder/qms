package com.pcbqms.equipment.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Normalized gateway identifiers for one QMS equipment record. */
@Data
@NoArgsConstructor
@TableName("qms_equipment_gateway_mapping")
public class EquipmentGatewayMapping {
    @TableId
    private String id;
    private String tenantCode;
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private String equipmentId;
    private Boolean ignored;
    private String sourceUsername;
    private String machineName;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
