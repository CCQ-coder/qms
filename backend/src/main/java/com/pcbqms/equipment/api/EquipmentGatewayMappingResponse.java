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
    LocalDateTime updatedAt
) {
    public static EquipmentGatewayMappingResponse from(EquipmentGatewayMapping mapping, Equipment equipment) {
        return new EquipmentGatewayMappingResponse(mapping.getId(), mapping.getEquipmentId(),
            equipment == null ? null : equipment.getAssetNo(), equipment == null ? null : equipment.getName(),
            mapping.getSourceUsername(), mapping.getMachineName(), mapping.getUpdatedAt());
    }
}
