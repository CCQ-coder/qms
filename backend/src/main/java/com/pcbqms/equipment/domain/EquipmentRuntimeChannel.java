package com.pcbqms.equipment.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@TableName("qms_equipment_runtime_channel")
public class EquipmentRuntimeChannel {
    @TableId
    private String id;
    private String tenantCode;
    private String equipmentId;
    private String sourceName;
    private String reportedStatus;
    private LocalDateTime lastDeviceTime;
    private LocalDateTime lastReceivedAt;
    private LocalDateTime updatedAt;
}
