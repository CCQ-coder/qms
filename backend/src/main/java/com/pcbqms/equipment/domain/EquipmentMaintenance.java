package com.pcbqms.equipment.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@TableName("qms_equipment_maintenance")
public class EquipmentMaintenance {
    @TableId
    private String id;
    private String tenantCode;
    private String recordNo;
    private String equipmentId;
    private String maintenanceType;
    private LocalDateTime scheduledAt;
    private LocalDateTime completedAt;
    private String status;
    private String description;
    private String result;
    private String executorName;
    private String verifierName;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

