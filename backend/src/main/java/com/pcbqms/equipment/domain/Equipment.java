package com.pcbqms.equipment.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@TableName("qms_equipment")
public class Equipment {
    @TableId
    private String id;
    private String tenantCode;
    private String assetNo;
    private String name;
    private String equipmentType;
    private String manufacturer;
    private String modelName;
    private String processCode;
    private String processName;
    private String status;
    private String responsibleName;
    private LocalDateTime calibrationDueAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
