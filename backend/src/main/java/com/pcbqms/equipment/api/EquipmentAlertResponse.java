package com.pcbqms.equipment.api;

import com.pcbqms.equipment.domain.EquipmentAlert;
import java.time.LocalDateTime;

public record EquipmentAlertResponse(
    String id,
    String alertNo,
    String equipmentAssetNo,
    String equipmentName,
    String title,
    String description,
    String status,
    LocalDateTime createdAt,
    LocalDateTime resolvedAt
) {
    public static EquipmentAlertResponse from(EquipmentAlert alert, String assetNo, String equipmentName) {
        return new EquipmentAlertResponse(alert.getId(), alert.getAlertNo(), assetNo, equipmentName, alert.getTitle(),
            alert.getDescription(), alert.getStatus(), alert.getCreatedAt(), alert.getResolvedAt());
    }
}
