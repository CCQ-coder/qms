package com.pcbqms.equipment.api;

import com.pcbqms.equipment.domain.Equipment;
import com.pcbqms.equipment.domain.EquipmentGatewayMapping;
import java.time.LocalDateTime;

public record EquipmentGatewayMappingResponse(
    String id,
    String equipmentId,
    String equipmentCode,
    String equipmentName,
    String username,
    String machineName,
    LocalDateTime updatedAt,
    boolean valid,
    String mappingIssue,
    boolean ignored
) {
    public EquipmentGatewayMappingResponse(String id, String equipmentId, String equipmentCode, String equipmentName,
        String username, String machineName, LocalDateTime updatedAt, boolean valid, String mappingIssue) {
        this(id, equipmentId, equipmentCode, equipmentName, username, machineName, updatedAt, valid, mappingIssue, false);
    }

    public static EquipmentGatewayMappingResponse from(EquipmentGatewayMapping mapping, Equipment equipment) {
        return new EquipmentGatewayMappingResponse(mapping.getId(), mapping.getEquipmentId(),
            equipment == null ? null : equipment.getAssetNo(), equipment == null ? null : equipment.getName(),
            mapping.getSourceUsername(), mapping.getMachineName(), mapping.getUpdatedAt(), equipment != null,
            Boolean.TRUE.equals(mapping.getIgnored()) ? null : equipment == null ? "目标设备不存在或不属于当前租户，请编辑映射并选择正确设备" : null,
            Boolean.TRUE.equals(mapping.getIgnored()));
    }
}
