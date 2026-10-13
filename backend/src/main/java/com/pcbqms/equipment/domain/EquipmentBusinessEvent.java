package com.pcbqms.equipment.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Durable hand-off for asynchronous QMS business rules. */
@Data
@NoArgsConstructor
@TableName("qms_equipment_business_event")
public class EquipmentBusinessEvent {
    @TableId
    private String id;
    private String tenantCode;
    private String equipmentId;
    private String telemetryId;
    private String eventType;
    private String status;
    private String payload;
    private LocalDateTime createdAt;
    private LocalDateTime processedAt;
}
