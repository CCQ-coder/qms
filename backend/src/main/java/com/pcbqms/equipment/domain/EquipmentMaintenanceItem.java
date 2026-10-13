package com.pcbqms.equipment.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@TableName("qms_equipment_maintenance_item")
public class EquipmentMaintenanceItem {
    @TableId
    private String id;
    private String tenantCode;
    private String maintenanceId;
    private String templateItemId;
    private String itemCode;
    private String itemName;
    private BigDecimal lowerLimit;
    private BigDecimal upperLimit;
    private String unit;
    private BigDecimal measuredValue;
    private String judgement;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
