package com.pcbqms.equipment.api;

import com.pcbqms.equipment.domain.EquipmentMaintenance;
import java.time.LocalDateTime;

public record EquipmentMaintenanceResponse(
    String id,
    String recordNo,
    String equipmentAssetNo,
    String equipmentName,
    String maintenanceType,
    LocalDateTime scheduledAt,
    LocalDateTime completedAt,
    String status,
    String description,
    String result,
    String executorName,
    String verifierName,
    String scheduleStatus
) {
    public static EquipmentMaintenanceResponse from(EquipmentMaintenance entity, String assetNo, String equipmentName) {
        return new EquipmentMaintenanceResponse(entity.getId(), entity.getRecordNo(), assetNo, equipmentName,
            entity.getMaintenanceType(), entity.getScheduledAt(), entity.getCompletedAt(), entity.getStatus(),
            entity.getDescription(), entity.getResult(), entity.getExecutorName(), entity.getVerifierName(), scheduleStatus(entity));
    }

    private static String scheduleStatus(EquipmentMaintenance entity) {
        if ("ALERT".equals(entity.getStatus())) {
            return "ALERT";
        }
        if ("COMPLETED".equals(entity.getStatus())) {
            return "COMPLETED";
        }
        return entity.getScheduledAt() != null && entity.getScheduledAt().isBefore(LocalDateTime.now()) ? "OVERDUE" : "PENDING";
    }
}
