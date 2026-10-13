package com.pcbqms.equipment.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@TableName("qms_equipment_alert")
public class EquipmentAlert {
    @TableId
    private String id;
    private String tenantCode;
    private String alertNo;
    private String equipmentId;
    private String maintenanceId;
    private String title;
    private String description;
    private String status;
    private String resolvedByMaintenanceId;
    private LocalDateTime resolvedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
