package com.pcbqms.equipment.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Latest field packet for a source that is not yet mapped to a QMS equipment record. */
@Data
@NoArgsConstructor
@TableName("qms_equipment_gateway_unmatched_message")
public class EquipmentGatewayUnmatchedMessage {
    @TableId
    private String id;
    private String tenantCode;
    private String sourceKey;
    private String sourceUsername;
    private String machineName;
    private LocalDateTime lastDeviceTime;
    private LocalDateTime lastReceivedAt;
    private Integer occurrenceCount;
    private String rawPayload;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
