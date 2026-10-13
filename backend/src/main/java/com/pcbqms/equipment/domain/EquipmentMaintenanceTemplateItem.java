package com.pcbqms.equipment.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@TableName("qms_equipment_maintenance_template_item")
public class EquipmentMaintenanceTemplateItem {
    @TableId
    private String id;
    private String tenantCode;
    private String equipmentType;
    private String maintenanceType;
    private String itemCode;
    private String itemName;
    private BigDecimal lowerLimit;
    private BigDecimal upperLimit;
    private String unit;
    private String status;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
